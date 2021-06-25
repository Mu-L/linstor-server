package com.linbit.linstor.core.apicallhandler.controller;

import com.linbit.ExhaustedPoolException;
import com.linbit.ImplementationError;
import com.linbit.linstor.InternalApiConsts;
import com.linbit.linstor.LinstorParsingUtils;
import com.linbit.linstor.annotation.SystemContext;
import com.linbit.linstor.api.ApiCallRcImpl;
import com.linbit.linstor.api.ApiCallRcWith;
import com.linbit.linstor.api.ApiConsts;
import com.linbit.linstor.api.pojo.backups.BackupMetaDataPojo;
import com.linbit.linstor.core.LinStor;
import com.linbit.linstor.core.SecretGenerator;
import com.linbit.linstor.core.apicallhandler.ScopeRunner;
import com.linbit.linstor.core.apicallhandler.controller.backup.l2l.rest.BackupShippingResponse;
import com.linbit.linstor.core.apicallhandler.controller.internal.CtrlSatelliteUpdateCaller;
import com.linbit.linstor.core.identifier.SnapshotName;
import com.linbit.linstor.core.objects.NetInterface;
import com.linbit.linstor.core.objects.Snapshot;
import com.linbit.linstor.core.objects.StltRemote;
import com.linbit.linstor.core.objects.StltRemoteControllerFactory;
import com.linbit.linstor.core.repository.RemoteRepository;
import com.linbit.linstor.core.repository.SystemConfRepository;
import com.linbit.linstor.dbdrivers.DatabaseException;
import com.linbit.linstor.logging.ErrorReporter;
import com.linbit.linstor.numberpool.DynamicNumberPool;
import com.linbit.linstor.numberpool.NumberPoolModule;
import com.linbit.linstor.propscon.InvalidKeyException;
import com.linbit.linstor.propscon.InvalidValueException;
import com.linbit.linstor.propscon.Props;
import com.linbit.linstor.security.AccessContext;
import com.linbit.linstor.security.AccessDeniedException;
import com.linbit.locks.LockGuardFactory;
import com.linbit.locks.LockGuardFactory.LockObj;
import com.linbit.locks.LockGuardFactory.LockType;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;

import reactor.core.publisher.Flux;

@Singleton
public class CtrlBackupL2LDstApiCallHandler
{
    private final AccessContext apiCtx;
    private final ScopeRunner scopeRunner;
    private final LockGuardFactory lockGuardFactory;
    private final CtrlTransactionHelper ctrlTransactionHelper;
    private final ErrorReporter errorReporter;
    private final CtrlSatelliteUpdateCaller ctrlSatelliteUpdateCaller;
    private final CtrlBackupApiCallHandler backupApiCallHandler;
    private final FreeCapacityFetcher freeCapacityFetcher;
    private final DynamicNumberPool snapshotShippingPortPool;
    private final StltRemoteControllerFactory stltRemoteControllerFactory;
    private final RemoteRepository remoteRepo;
    private final SystemConfRepository systemConfRepository;

    @Inject
    public CtrlBackupL2LDstApiCallHandler(
        @SystemContext AccessContext apiCtxRef,
        ScopeRunner scopeRunnerRef,
        LockGuardFactory lockGuardFactoryRef,
        CtrlTransactionHelper ctrlTransactionHelperRef,
        ErrorReporter errorReporterRef,
        CtrlSatelliteUpdateCaller ctrlSatelliteUpdateCallerRef,
        CtrlBackupApiCallHandler backupApiCallHandlerRef,
        FreeCapacityFetcher freeCapacityFetcherRef,
        @Named(NumberPoolModule.SNAPSHOPT_SHIPPING_PORT_POOL) DynamicNumberPool snapshotShippingPortPoolRef,
        StltRemoteControllerFactory stltRemoteControllerFactoryRef,
        RemoteRepository remoteRepoRef,
        SystemConfRepository systemConfRepositoryRef
    )
    {
        apiCtx = apiCtxRef;
        scopeRunner = scopeRunnerRef;
        lockGuardFactory = lockGuardFactoryRef;
        ctrlTransactionHelper = ctrlTransactionHelperRef;
        errorReporter = errorReporterRef;
        ctrlSatelliteUpdateCaller = ctrlSatelliteUpdateCallerRef;
        backupApiCallHandler = backupApiCallHandlerRef;
        freeCapacityFetcher = freeCapacityFetcherRef;
        snapshotShippingPortPool = snapshotShippingPortPoolRef;
        stltRemoteControllerFactory = stltRemoteControllerFactoryRef;
        remoteRepo = remoteRepoRef;
        systemConfRepository = systemConfRepositoryRef;
    }

    public Flux<BackupShippingResponse> startReceiving(
        int[] srcVersionRef,
        String dstRscNameRef,
        BackupMetaDataPojo metaDataRef,
        String srcBackupNameRef,
        String srcClusterIdRef,
        @Nullable String dstNodeNameRef,
        @Nullable String dstNetIfNameRef,
        @Nullable String dstStorPoolRef,
        @Nullable Map<String, String> storPoolRenameMapRef
    )
    {
        Flux<BackupShippingResponse> flux;
        if (!LinStor.VERSION_INFO_PROVIDER.equalsVersion(srcVersionRef[0], srcVersionRef[1], srcVersionRef[2]))
        {
            flux = Flux.just(
                new BackupShippingResponse(
                    false,
                    ApiCallRcImpl.singleApiCallRc(
                        ApiConsts.FAIL_BACKUP_INCOMPATIBLE_VERSION,
                        "Incompatible versions. Source version: " + srcVersionRef[0] + "." + srcVersionRef[1] + "." +
                        srcVersionRef[2] + ". Destination (local) version: " +
                        LinStor.VERSION_INFO_PROVIDER.getVersion()
                    ),
                    null,
                    null
                )
            );
        }
        else
        {
            int snapShipPort;
            try
            {
                snapShipPort = snapshotShippingPortPool.autoAllocate();

                BackupShippingData data = new BackupShippingData(
                    srcVersionRef,
                    dstRscNameRef,
                    metaDataRef,
                    srcBackupNameRef,
                    srcClusterIdRef,
                    dstNodeNameRef,
                    dstNetIfNameRef,
                    dstStorPoolRef,
                    storPoolRenameMapRef,
                    snapShipPort
                );

                flux = scopeRunner.fluxInTransactionalScope(
                    "Backupshipping L2L start receive",
                    lockGuardFactory.create()
                    .read(LockObj.NODES_MAP)
                    .write(LockObj.RSC_DFN_MAP).buildDeferred(),
                    () -> startReceivingInTransaction(data)
                ).map(snap -> snapshotToResponse(snap, dstNetIfNameRef, snapShipPort));
            }
            catch (ExhaustedPoolException exc)
            {
                flux = Flux.just(
                    new BackupShippingResponse(
                        false,
                        ApiCallRcImpl.singleApiCallRc(
                            ApiConsts.FAIL_POOL_EXHAUSTED_BACKUP_SHIPPING_TCP_PORT,
                            "Shipping port range exhausted"
                        ),
                        null,
                        null
                    )
                );
            }
        }

        return flux;
    }

    private Flux<ApiCallRcWith<Snapshot>> startReceivingInTransaction(BackupShippingData data)
    {
        String clusterHash = getSrcClusterShortId(data.srcClusterId);

        SnapshotName snapName = LinstorParsingUtils.asSnapshotName(data.srcBackupName + "_" + clusterHash);
        StltRemote stltRemote = CtrlBackupL2LSrcApiCallHandler.createStltRemote(
            stltRemoteControllerFactory,
            remoteRepo,
            apiCtx,
            data.dstRscName,
            snapName.displayValue,
            data.snapShipPort
        );

        data.snapName = snapName;
        data.stltRemote = stltRemote;

        data.metaData.getRsc().getProps().put(
            ApiConsts.NAMESPC_BACKUP_SHIPPING + "/" + InternalApiConsts.KEY_BACKUP_TARGET_REMOTE,
            stltRemote.getName().displayValue
        );

        ctrlTransactionHelper.commit();

        return ctrlSatelliteUpdateCaller.updateSatellites(stltRemote)
            .thenMany(
                freeCapacityFetcher.fetchThinFreeCapacities(
                    data.dstNodeName == null ?
                        Collections.emptySet() :
                        Collections.singleton(LinstorParsingUtils.asNodeName(data.dstNodeName))
                ).flatMapMany(
                    thinFreeCapacities -> scopeRunner.fluxInTransactionalScope(
                        "restore backup",
                        lockGuardFactory.buildDeferred(
                            LockType.WRITE,
                            LockObj.NODES_MAP,
                            LockObj.RSC_DFN_MAP
                        ),
                        () -> backupApiCallHandler.restoreBackupL2LInTransaction(
                            data.dstNodeName,
                            data.dstStorPool,
                            thinFreeCapacities,
                            data.dstRscName,
                            data.metaData,
                            data.storPoolRenameMap,
                            data.stltRemote,
                            data.snapName
                        )
                    )
                )
        );
    }

    private String getSrcClusterShortId(String srcClusterIdRef)
    {
        String ret;
        try
        {
            Props props = systemConfRepository.getCtrlConfForChange(apiCtx);
            ret = props.getProp(srcClusterIdRef, ApiConsts.NAMESPC_CLUSTER_REMOTE);
            if (ret == null)
            {
                ret = generateClusterShortId();

                Optional<Props> namespace = props.getNamespace(ApiConsts.NAMESPC_CLUSTER_REMOTE);
                if (namespace.isPresent())
                {
                    HashSet<String> existingHashes = new HashSet<>(namespace.get().values());

                    while (existingHashes.contains(ret))
                    {
                        ret = generateClusterShortId();
                    }
                }

                props.setProp(srcClusterIdRef, ret, ApiConsts.NAMESPC_CLUSTER_REMOTE);
            }
        }
        catch (AccessDeniedException | InvalidKeyException | DatabaseException | InvalidValueException exc)
        {
            throw new ImplementationError(exc);
        }
        return ret;
    }

    public static String generateClusterShortId()
    {
        return new BigInteger(SecretGenerator.generateSecret(5)).abs().toString(36);
    }

    private BackupShippingResponse snapshotToResponse(
        ApiCallRcWith<Snapshot> apiCallRcWithSnapRef,
        @Nullable String dstNetIfNameRef,
        int snapShipPortRef
    )
    {
        try
        {
            ApiCallRcImpl responses = new ApiCallRcImpl();
            Snapshot snap = apiCallRcWithSnapRef.extractApiCallRc(responses);

            NetInterface netIf = null;
            if (dstNetIfNameRef != null)
            {
                netIf = snap.getNode().getNetInterface(
                    apiCtx,
                    LinstorParsingUtils.asNetInterfaceName(dstNetIfNameRef)
                );
            }
            if (netIf == null)
            {
                netIf = snap.getNode().iterateNetInterfaces(apiCtx).next();
            }

            return new BackupShippingResponse(
                true,
                responses,
                netIf.getAddress(apiCtx).getAddress(),
                snapShipPortRef
            );
        }
        catch (AccessDeniedException exc)
        {
            throw new ImplementationError(exc);
        }
    }

    private class BackupShippingData
    {
        public StltRemote stltRemote;
        public SnapshotName snapName;
        private final int[] srcVersion;
        private final String dstRscName;
        private final BackupMetaDataPojo metaData;
        private final String srcBackupName;
        private final String srcClusterId;
        private String dstNodeName;
        private String dstNetIfName;
        private String dstStorPool;
        private Map<String, String> storPoolRenameMap;
        private final int snapShipPort;

        public BackupShippingData(
            int[] srcVersionRef,
            String dstRscNameRef,
            BackupMetaDataPojo metaDataRef,
            String srcBackupNameRef,
            String srcClusterIdRef,
            String dstNodeNameRef,
            String dstNetIfNameRef,
            String dstStorPoolRef,
            Map<String, String> storPoolRenameMapRef,
            int snapShipPortRef
        )
        {
            srcVersion = srcVersionRef;
            dstRscName = dstRscNameRef;
            metaData = metaDataRef;
            srcBackupName = srcBackupNameRef;
            srcClusterId = srcClusterIdRef;
            dstNodeName = dstNodeNameRef;
            dstNetIfName = dstNetIfNameRef;
            dstStorPool = dstStorPoolRef;
            storPoolRenameMap = storPoolRenameMapRef;
            snapShipPort = snapShipPortRef;
        }
    }
}
