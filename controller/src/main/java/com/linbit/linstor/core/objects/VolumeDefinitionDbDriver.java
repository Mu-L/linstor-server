package com.linbit.linstor.core.objects;

import com.linbit.ImplementationError;
import com.linbit.InvalidIpAddressException;
import com.linbit.InvalidNameException;
import com.linbit.SingleColumnDatabaseDriver;
import com.linbit.ValueOutOfRangeException;
import com.linbit.drbd.md.MdException;
import com.linbit.linstor.annotation.SystemContext;
import com.linbit.linstor.core.identifier.ResourceName;
import com.linbit.linstor.core.identifier.VolumeNumber;
import com.linbit.linstor.core.objects.VolumeDefinition.InitMaps;
import com.linbit.linstor.dbdrivers.AbsDatabaseDriver;
import com.linbit.linstor.dbdrivers.DatabaseException;
import com.linbit.linstor.dbdrivers.DbEngine;
import com.linbit.linstor.dbdrivers.GeneratedDatabaseTables;
import com.linbit.linstor.dbdrivers.interfaces.VolumeDefinitionDataDatabaseDriver;
import com.linbit.linstor.logging.ErrorReporter;
import com.linbit.linstor.propscon.PropsContainerFactory;
import com.linbit.linstor.security.AccessContext;
import com.linbit.linstor.security.ObjectProtectionDatabaseDriver;
import com.linbit.linstor.stateflags.StateFlagsPersistence;
import com.linbit.linstor.transaction.TransactionMgr;
import com.linbit.linstor.transaction.TransactionObjectFactory;
import com.linbit.utils.Pair;

import static com.linbit.linstor.dbdrivers.GeneratedDatabaseTables.VolumeDefinitions.RESOURCE_NAME;
import static com.linbit.linstor.dbdrivers.GeneratedDatabaseTables.VolumeDefinitions.UUID;
import static com.linbit.linstor.dbdrivers.GeneratedDatabaseTables.VolumeDefinitions.VLM_FLAGS;
import static com.linbit.linstor.dbdrivers.GeneratedDatabaseTables.VolumeDefinitions.VLM_NR;
import static com.linbit.linstor.dbdrivers.GeneratedDatabaseTables.VolumeDefinitions.VLM_SIZE;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import java.util.Map;
import java.util.TreeMap;

import com.google.common.base.Functions;

@Singleton
public class VolumeDefinitionDbDriver extends
    AbsDatabaseDriver<VolumeDefinitionData, VolumeDefinition.InitMaps, Map<ResourceName, ResourceDefinition>>
    implements VolumeDefinitionDataDatabaseDriver
{
    private final StateFlagsPersistence<VolumeDefinitionData> flagsDriver;
    private final SingleColumnDatabaseDriver<VolumeDefinitionData, Long> volumeSizeDriver;
    private final Provider<TransactionMgr> transMgrProvider;
    private final PropsContainerFactory propsContainerFactory;
    private final TransactionObjectFactory transObjFactory;

    @Inject
    public VolumeDefinitionDbDriver(
        @SystemContext AccessContext dbCtxRef,
        ErrorReporter errorReporterRef,
        DbEngine dbEngineRef,
        Provider<TransactionMgr> transMgrProviderRef,
        ObjectProtectionDatabaseDriver objProtDriverRef,
        PropsContainerFactory propsContainerFactoryRef,
        TransactionObjectFactory transObjFactoryRef
    )
    {
        super(
            errorReporterRef,
            GeneratedDatabaseTables.VOLUME_DEFINITIONS,
            dbEngineRef,
            objProtDriverRef
        );
        transMgrProvider = transMgrProviderRef;
        propsContainerFactory = propsContainerFactoryRef;
        transObjFactory = transObjFactoryRef;

        setColumnSetter(UUID, vlmDfn -> vlmDfn.getUuid().toString());
        setColumnSetter(RESOURCE_NAME, vlmDfn -> vlmDfn.getResourceDefinition().getName().value);
        setColumnSetter(VLM_NR, vlmDfn -> vlmDfn.getVolumeNumber().value);
        setColumnSetter(VLM_SIZE, vlmDfn -> vlmDfn.getVolumeSize(dbCtxRef));
        setColumnSetter(VLM_FLAGS, vlmDfn -> vlmDfn.getFlags().getFlagsBits(dbCtxRef));

        flagsDriver = generateFlagDriver(VLM_FLAGS, VolumeDefinition.VlmDfnFlags.class);
        volumeSizeDriver = generateSingleColumnDriver(
            VLM_SIZE,
            vlmDfn -> Long.toString(vlmDfn.getVolumeSize(dbCtxRef)),
            Functions.identity()
        );
    }

    @Override
    public StateFlagsPersistence<VolumeDefinitionData> getStateFlagsPersistence()
    {
        return flagsDriver;
    }

    @Override
    public SingleColumnDatabaseDriver<VolumeDefinitionData, Long> getVolumeSizeDriver()
    {
        return volumeSizeDriver;
    }

    @Override
    protected Pair<VolumeDefinitionData, InitMaps> load(
        RawParameters raw,
        Map<ResourceName, ResourceDefinition> parent
    )
        throws DatabaseException, InvalidNameException, ValueOutOfRangeException, InvalidIpAddressException,
        MdException, RuntimeException
    {

        final ResourceName rscName = raw.build(RESOURCE_NAME, ResourceName::new);
        final VolumeNumber vlmNr;
        final long vlmSize;
        final long vlmFlags;
        switch (getDbType())
        {
            case ETCD:
                vlmNr = new VolumeNumber(Integer.parseInt(raw.get(VLM_NR)));
                vlmSize = Long.parseLong(raw.get(VLM_SIZE));
                vlmFlags = Long.parseLong(raw.get(VLM_FLAGS));
                break;
            case SQL:
                vlmNr = new VolumeNumber(raw.get(VLM_NR));
                vlmSize = raw.get(VLM_SIZE);
                vlmFlags = raw.get(VLM_FLAGS);
                break;
            default:
                throw new ImplementationError("Unknown database type: " + getDbType());
        }

        Map<String, Volume> vlmMap = new TreeMap<>();
        return new Pair<>(
            new VolumeDefinitionData(
                raw.build(UUID, java.util.UUID::fromString),
                parent.get(rscName),
                vlmNr,
                vlmSize,
                vlmFlags,
                this,
                propsContainerFactory,
                transObjFactory,
                transMgrProvider,
                vlmMap,
                new TreeMap<>()
            ),
            new InitMapsImpl(vlmMap)
        );
    }

    @Override
    protected String getId(VolumeDefinitionData vlmDfn)
    {
        return "(ResName=" + vlmDfn.getResourceDefinition().getName().displayValue +
            " VolNum=" + vlmDfn.getVolumeNumber().value + ")";
    }

    private class InitMapsImpl implements VolumeDefinition.InitMaps
    {
        private final Map<String, Volume> vlmMap;

        private InitMapsImpl(Map<String, Volume> vlmMapRef)
        {
            vlmMap = vlmMapRef;
        }

        @Override
        public Map<String, Volume> getVlmMap()
        {
            return vlmMap;
        }
    }
}