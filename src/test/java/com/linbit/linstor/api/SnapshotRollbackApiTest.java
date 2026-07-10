package com.linbit.linstor.api;

import com.linbit.linstor.api.utils.AbsApiCallTester;
import com.linbit.linstor.core.ApiTestBase;
import com.linbit.linstor.core.BackupInfoManager;
import com.linbit.linstor.core.apicallhandler.controller.CtrlRscCrtApiHelper;
import com.linbit.linstor.core.apicallhandler.controller.CtrlSnapshotCrtApiCallHandler;
import com.linbit.linstor.core.apicallhandler.controller.CtrlSnapshotRollbackApiCallHandler;
import com.linbit.linstor.core.apicallhandler.controller.FreeCapacityFetcher;
import com.linbit.linstor.core.apicallhandler.controller.mgr.SnapshotRollbackManager;
import com.linbit.linstor.core.identifier.NodeName;
import com.linbit.linstor.core.identifier.RemoteName;
import com.linbit.linstor.core.identifier.ResourceName;
import com.linbit.linstor.core.identifier.SnapshotName;
import com.linbit.linstor.core.identifier.StorPoolName;
import com.linbit.linstor.core.identifier.VolumeNumber;
import com.linbit.linstor.core.objects.Node;
import com.linbit.linstor.core.objects.Resource;
import com.linbit.linstor.core.objects.ResourceDefinition;
import com.linbit.linstor.core.objects.SnapshotDefinition;
import com.linbit.linstor.core.objects.StorPool;
import com.linbit.linstor.core.objects.StorPoolDefinition;
import com.linbit.linstor.core.objects.VolumeDefinition;
import com.linbit.linstor.layer.LayerPayload;
import com.linbit.linstor.netcom.Peer;
import com.linbit.linstor.satellitestate.SatelliteState;
import com.linbit.linstor.storage.data.RscLayerSuffixes;
import com.linbit.linstor.storage.data.adapter.drbd.DrbdRscDfnData;
import com.linbit.linstor.storage.kinds.DeviceLayerKind;
import com.linbit.linstor.storage.kinds.DeviceProviderKind;
import com.linbit.linstor.utils.externaltools.ExtToolsManager;

import javax.inject.Inject;
import javax.inject.Provider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.google.inject.testing.fieldbinder.Bind;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@SuppressWarnings("checkstyle:magicnumber")
public class SnapshotRollbackApiTest extends ApiTestBase
{
    private static final String TEST_NODE_NAME = "TestSatellite";
    private static final String TEST_RSC_NAME = "TestRsc";
    private static final String TEST_SP_NAME = "TestStorPool";
    private static final String TEST_SNAP_NAME = "snap1";
    private static final long TEST_VLM_SIZE = 100 * 1024L;

    @Inject
    private Provider<CtrlSnapshotCrtApiCallHandler> snapCrtApiCallHandlerProvider;
    @Inject
    private Provider<CtrlSnapshotRollbackApiCallHandler> snapRollbackApiCallHandlerProvider;
    @Inject
    private CtrlRscCrtApiHelper ctrlRscCrtApiHelper;
    @Inject
    private SnapshotRollbackManager snapRollbackMgr;
    @Inject
    private BackupInfoManager backupInfoMgr;

    @Bind
    @Mock
    protected FreeCapacityFetcher freeCapacityFetcher;

    @Mock
    protected Peer mockSatellite;

    @Mock
    protected ExtToolsManager mockExtToolsMgr;

    private final NodeName testNodeName;
    private final ResourceName testRscName;
    private final SnapshotName testSnapName;
    private final StorPoolName testStorPoolName;

    private Node testNode;
    private StorPool testStorPool;

    private final AtomicInteger minorNrGenerator = new AtomicInteger(1000);

    public SnapshotRollbackApiTest() throws Exception
    {
        testNodeName = new NodeName(TEST_NODE_NAME);
        testRscName = new ResourceName(TEST_RSC_NAME);
        testSnapName = new SnapshotName(TEST_SNAP_NAME);
        testStorPoolName = new StorPoolName(TEST_SP_NAME);
    }

    @Before
    @Override
    public void setUp() throws Exception
    {
        super.setUp();

        Mockito.when(minorNrPoolMock.autoAllocate())
            .thenAnswer(ignoredContext -> minorNrGenerator.getAndIncrement());

        stubSatellitePeer(mockSatellite, mockExtToolsMgr, new SatelliteState(), false);
        stubAllExtToolsSupported(mockExtToolsMgr);
        Mockito.when(freeCapacityFetcher.fetchThinFreeCapacities(any()))
            .thenReturn(Mono.just(Collections.emptyMap()));

        testNode = nodeFactory.create(
            testNodeName,
            Node.Type.SATELLITE,
            null
        );
        testNode.setPeer(mockSatellite);
        nodesMap.put(testNodeName, testNode);

        leaveScope();
    }

    /*
     * rollback via clone strategy (safety-snapshot, delete, restore, recreate), used for all
     * non-ZFS resources
     */

    @Test
    public void rollbackCloneStrategySuccess() throws Exception
    {
        createDeployedSnapshot(DeviceProviderKind.LVM_THIN);

        evaluateTest(
            new RollbackSnapshotCall(cloneRollbackRcs())
        );

        ResourceDefinition rscDfn = rscDfnMap.get(testRscName);
        // the safety snapshot was deleted again, only the user snapshot remains
        assertThat(rscDfn.getSnapshotDfns()).hasSize(1);
        assertThat(rscDfn.getSnapshotDfn(testSnapName)).isNotNull();
        // the resource was restored on the original node
        Resource rsc = rscDfn.getResource(testNodeName);
        assertThat(rsc).isNotNull();
        assertThat(rsc.getStateFlags().isSet(Resource.Flags.DELETE)).isFalse();
        assertThat(rscDfn.getVolumeDfnCount()).isEqualTo(1);
    }

    @Test
    public void rollbackCloneStrategyResetsVolumeDefinitions() throws Exception
    {
        createDeployedSnapshot(DeviceProviderKind.LVM_THIN);

        // resize the snapshotted volume definition and add a new one (including its volume on the
        // deployed resource) after the snapshot was taken
        enterScope();
        ResourceDefinition rscDfn = rscDfnMap.get(testRscName);
        rscDfn.getVolumeDfn(new VolumeNumber(0)).setVolumeSize(TEST_VLM_SIZE * 2);
        VolumeDefinition newVlmDfn = volumeDefinitionTestFactory.builder(TEST_RSC_NAME, 1)
            .setSize(TEST_VLM_SIZE)
            .build();
        LayerPayload payload = new LayerPayload();
        payload.putStorageVlmPayload(RscLayerSuffixes.SUFFIX_DATA, 1, testStorPool);
        volumeFactory.create(
            rscDfn.getResource(testNodeName),
            newVlmDfn,
            null,
            payload,
            null,
            Collections.emptyMap(),
            null
        );
        leaveScope();

        evaluateTest(
            new RollbackSnapshotCall(cloneRollbackRcs())
        );

        // the rollback restored the volume definitions to the state of the snapshot
        assertThat(rscDfn.getVolumeDfnCount()).isEqualTo(1);
        assertThat(rscDfn.getVolumeDfn(new VolumeNumber(0)).getVolumeSize()).isEqualTo(TEST_VLM_SIZE);
        assertThat(rscDfn.getVolumeDfn(new VolumeNumber(1))).isNull();
    }

    /*
     * rollback via "zfs rollback" strategy, only applicable if everything is ZFS
     */

    @Test
    public void rollbackZfsRollbackStrategySuccess() throws Exception
    {
        createDeployedSnapshot(DeviceProviderKind.ZFS_THIN);
        simulateRollbackResponses();

        evaluateTest(
            new RollbackSnapshotCall(
                // resource definition marked down for rollback
                ApiConsts.MODIFIED,
                // deactivated resource on satellite
                ApiConsts.MODIFIED,
                // volume definitions reset to snapshot state
                ApiConsts.MODIFIED,
                // rolled resource back on satellite
                ApiConsts.MODIFIED,
                // re-activated resource after rollback
                ApiConsts.MODIFIED
            )
        );

        ResourceDefinition rscDfn = rscDfnMap.get(testRscName);
        Resource rsc = rscDfn.getResource(testNodeName);
        assertThat(rsc).isNotNull();
        // the rollback target property was removed after the successful rollback
        assertThat(rsc.getProps().getProp(ApiConsts.KEY_RSC_ROLLBACK_TARGET)).isNull();
        // characterization of a latent bug: finishRollbackInScope() unmarks the DRBD "down" flag via
        // unmarkDownPrivileged() but misses the ctrlTransactionHelper.commit() (unlike the error path
        // reactivateRscDfnInTransaction()), so the change is rolled back when the transactional scope
        // closes and the resource definition stays marked down on the controller
        Map<String, DrbdRscDfnData<Resource>> drbdRscDfnDataMap = rscDfn.getLayerData(DeviceLayerKind.DRBD);
        assertThat(drbdRscDfnDataMap).isNotEmpty();
        for (DrbdRscDfnData<Resource> drbdRscDfnData : drbdRscDfnDataMap.values())
        {
            assertThat(drbdRscDfnData.isDown()).isTrue();
        }
    }

    @Test
    public void rollbackZfsRollbackStrategyNotMostRecentRejected() throws Exception
    {
        createDeployedSnapshot(DeviceProviderKind.ZFS_THIN);
        // a newer snapshot exists, "zfs rollback" would have to destroy it
        createSnapDfn(TEST_RSC_NAME, "snap2", SnapshotDefinition.Flags.SUCCESSFUL);

        evaluateTest(
            new RollbackSnapshotCall(ApiConsts.FAIL_DEPENDEND_BACKUP)
                .setZfsStrategy(ApiConsts.VAL_STOR_POOL_ZFS_ROLLBACK_STRAT_ROLLBACK)
        );
    }

    @Test
    public void rollbackZfsRollbackStrategyVolumeWithoutSnapshotRejected() throws Exception
    {
        createDeployedSnapshot(DeviceProviderKind.ZFS_THIN);

        // add a volume that is not captured by the snapshot
        enterScope();
        ResourceDefinition rscDfn = rscDfnMap.get(testRscName);
        VolumeDefinition newVlmDfn = volumeDefinitionTestFactory.builder(TEST_RSC_NAME, 1)
            .setSize(TEST_VLM_SIZE)
            .build();
        LayerPayload payload = new LayerPayload();
        payload.putStorageVlmPayload(RscLayerSuffixes.SUFFIX_DATA, 1, testStorPool);
        volumeFactory.create(
            rscDfn.getResource(testNodeName),
            newVlmDfn,
            null,
            payload,
            null,
            Collections.emptyMap(),
            null
        );
        leaveScope();

        evaluateTest(
            new RollbackSnapshotCall(ApiConsts.FAIL_NOT_FOUND_SNAPSHOT)
                .setZfsStrategy(ApiConsts.VAL_STOR_POOL_ZFS_ROLLBACK_STRAT_ROLLBACK)
        );
    }

    @Test
    public void rollbackWhileRollbackInProgressRejected() throws Exception
    {
        createDeployedSnapshot(DeviceProviderKind.ZFS_THIN);

        // simulate a rollback that is already in progress for this resource definition
        snapRollbackMgr.prepareFlux(
            rscDfnMap.get(testRscName),
            Collections.singleton(testNodeName)
        );

        evaluateTest(
            new RollbackSnapshotCall(
                // resource definition marked down for rollback
                ApiConsts.MODIFIED,
                // deactivated resource on satellite
                ApiConsts.MODIFIED,
                // volume definitions reset to snapshot state
                ApiConsts.MODIFIED,
                // second rollback rejected
                ApiConsts.FAIL_SNAPSHOT_ROLLBACK_IN_PROGRESS
            )
        );
    }

    @Test
    public void rollbackBackupRestoreRunningRejected() throws Exception
    {
        deployTestResource(DeviceProviderKind.LVM_THIN);
        createSnapDfn(TEST_RSC_NAME, TEST_SNAP_NAME, SnapshotDefinition.Flags.SUCCESSFUL);

        enterScope();
        backupInfoMgr.addAllRestoreEntries(
            rscDfnMap.get(testRscName),
            "dummy.meta",
            TEST_RSC_NAME,
            Collections.emptyList(),
            Collections.emptyMap(),
            new RemoteName("dummyremote")
        );
        leaveScope();

        evaluateTest(
            new RollbackSnapshotCall(ApiConsts.FAIL_IN_USE)
        );
    }

    /*
     * helpers
     */

    /**
     * Response sequence of a successful rollback via the clone strategy (safety-snapshot, truncate,
     * restore, recreate). Unless noted otherwise the entries carry the snapshot/modify context masks
     * of the rollback api call.
     */
    private long[] cloneRollbackRcs()
    {
        return new long[]
        {
            // safety snapshot: suspended IO
            ApiConsts.MODIFIED,
            // safety snapshot: took snapshot
            ApiConsts.MODIFIED,
            // safety snapshot: resumed IO
            ApiConsts.MODIFIED,
            // truncate: resource definition marked for deletion
            ApiConsts.DELETED,
            // truncate: resource marked for deletion on satellite
            ApiConsts.MODIFIED,
            // truncate: updated resync-after entries
            ApiConsts.MASK_INFO,
            // restore: resource restored from the snapshot (emitted with the restore's resource context)
            ApiConsts.CREATED | ApiConsts.MASK_RSC | ApiConsts.MASK_CRT,
            // restore: resource deployed on satellite
            ApiConsts.MODIFIED,
            // restore: updated resync-after entries
            ApiConsts.MASK_INFO,
            // safety snapshot marked for deletion
            ApiConsts.DELETED,
            // safety snapshot deleted on satellite
            ApiConsts.MODIFIED,
            // safety snapshot deleted on all nodes
            ApiConsts.DELETED,
            // safety snapshot definition deleted
            ApiConsts.DELETED,
            // make available: resource already deployed as requested
            ApiConsts.MASK_RSC | ApiConsts.MASK_CRT
        };
    }

    private void satelliteOnline()
    {
        setSatelliteOnline(mockSatellite, true);
    }

    /**
     * Simulates the satellite's NotifyRollbackDone answer: whenever the controller sends an update to
     * the mocked satellite, a background thread reports the rollback as successful. The report thread
     * blocks in {@link SnapshotRollbackManager#handle} until the rollback flux is subscribed, hence
     * a background thread instead of answering inline.
     */
    private void simulateRollbackResponses()
    {
        Mockito.when(mockSatellite.apiCall(anyString(), any())).thenAnswer(
            ignored ->
            {
                Thread notifier = new Thread(
                    () -> snapRollbackMgr.handle(testNodeName, testRscName, true)
                );
                notifier.setDaemon(true);
                notifier.start();
                return Flux.empty();
            }
        );
    }

    private void createRscDfnWithVlmDfn(String rscNameStr) throws Exception
    {
        enterScope();

        rscDfnMap.put(
            new ResourceName(rscNameStr),
            resourceDefinitionTestFactory.builder(rscNameStr)
                .setLayerStack(new ArrayList<>(Arrays.asList(DeviceLayerKind.DRBD, DeviceLayerKind.STORAGE)))
                .build()
        );
        volumeDefinitionTestFactory.builder(rscNameStr, 0)
            .setSize(TEST_VLM_SIZE)
            .build();

        leaveScope();
    }

    private void deployTestResource(DeviceProviderKind providerKind) throws Exception
    {
        createRscDfnWithVlmDfn(TEST_RSC_NAME);

        enterScope();

        StorPoolDefinition storPoolDfn = storPoolDefinitionFactory.create(testStorPoolName);
        storPoolDfnMap.put(testStorPoolName, storPoolDfn);
        testStorPool = storPoolFactory.create(
            testNode,
            storPoolDfn,
            providerKind,
            getFreeSpaceMgr(storPoolDfn, testNode),
            false
        );
        testStorPool.getFreeSpaceTracker().setCapacityInfo(10_000_000, 10_000_000);

        Map<String, String> rscProps = new TreeMap<>();
        rscProps.put(ApiConsts.KEY_STOR_POOL_NAME, TEST_SP_NAME);
        ctrlRscCrtApiHelper.createResourceDb(
            TEST_NODE_NAME,
            TEST_RSC_NAME,
            0L,
            rscProps,
            Collections.emptyList(),
            null,
            null,
            null,
            null,
            Collections.emptyList(),
            Resource.DiskfulBy.USER,
            false
        );

        leaveScope();
    }

    private SnapshotDefinition createSnapDfn(
        String rscNameStr,
        String snapNameStr,
        SnapshotDefinition.Flags... flags
    )
        throws Exception
    {
        enterScope();

        SnapshotDefinition snapDfn = snapshotDefinitionFactory.create(
            rscDfnMap.get(new ResourceName(rscNameStr)),
            new SnapshotName(snapNameStr),
            flags
        );

        leaveScope();

        return snapDfn;
    }

    private void createDeployedSnapshot(DeviceProviderKind providerKind) throws Exception
    {
        deployTestResource(providerKind);
        satelliteOnline();
        Mockito.when(mockPeer.isOnline()).thenReturn(true);

        // the mocked satellite reports no DRBD states, which counts as UpToDate, and immediately
        // "responds" to all updates, so the snapshot create flux runs through all of its stages
        evaluateTest(
            new CreateSnapshotCall(
                // snapshot registered
                ApiConsts.CREATED,
                // suspended IO
                ApiConsts.MODIFIED,
                // took snapshot
                ApiConsts.MODIFIED,
                // resumed IO
                ApiConsts.MODIFIED
            )
        );
    }

    private class CreateSnapshotCall extends AbsApiCallTester
    {
        CreateSnapshotCall(long... expectedRcs)
        {
            super(
                ApiConsts.MASK_SNAPSHOT,
                ApiConsts.MASK_CRT,
                expectedRcs
            );
        }

        @Override
        public ApiCallRc executeApiCall()
        {
            return collect(
                snapCrtApiCallHandlerProvider.get().createSnapshot(
                    Collections.emptyList(),
                    TEST_RSC_NAME,
                    TEST_SNAP_NAME,
                    Collections.emptyMap()
                )
            );
        }
    }

    private class RollbackSnapshotCall extends AbsApiCallTester
    {
        private String rscName;
        private String snapName;
        private String zfsStrategy;

        RollbackSnapshotCall(long... expectedRcs)
        {
            super(
                ApiConsts.MASK_SNAPSHOT,
                ApiConsts.MASK_MOD,
                expectedRcs
            );
            rscName = TEST_RSC_NAME;
            snapName = TEST_SNAP_NAME;
            zfsStrategy = null;
        }

        @Override
        public ApiCallRc executeApiCall()
        {
            return collect(
                snapRollbackApiCallHandlerProvider.get().rollbackSnapshot(
                    rscName,
                    snapName,
                    zfsStrategy
                )
            );
        }

        public RollbackSnapshotCall setZfsStrategy(String zfsStrategyRef)
        {
            zfsStrategy = zfsStrategyRef;
            return this;
        }
    }
}
