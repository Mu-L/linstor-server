package com.linbit.linstor.api;

import com.linbit.linstor.api.utils.AbsApiCallTester;
import com.linbit.linstor.core.ApiTestBase;
import com.linbit.linstor.core.apicallhandler.controller.CtrlRscCrtApiHelper;
import com.linbit.linstor.core.apicallhandler.controller.CtrlSnapshotCrtApiCallHandler;
import com.linbit.linstor.core.apicallhandler.controller.CtrlSnapshotDeleteApiCallHandler;
import com.linbit.linstor.core.apicallhandler.controller.CtrlSnapshotRestoreApiCallHandler;
import com.linbit.linstor.core.apicallhandler.controller.CtrlSnapshotRollbackApiCallHandler;
import com.linbit.linstor.core.apicallhandler.controller.FreeCapacityFetcher;
import com.linbit.linstor.core.identifier.NodeName;
import com.linbit.linstor.core.identifier.ResourceName;
import com.linbit.linstor.core.identifier.SnapshotName;
import com.linbit.linstor.core.identifier.StorPoolName;
import com.linbit.linstor.core.objects.Node;
import com.linbit.linstor.core.objects.Resource;
import com.linbit.linstor.core.objects.SnapshotDefinition;
import com.linbit.linstor.core.objects.StorPool;
import com.linbit.linstor.core.objects.StorPoolDefinition;
import com.linbit.linstor.netcom.Peer;
import com.linbit.linstor.satellitestate.SatelliteResourceState;
import com.linbit.linstor.satellitestate.SatelliteState;
import com.linbit.linstor.storage.kinds.DeviceLayerKind;
import com.linbit.linstor.storage.kinds.DeviceProviderKind;
import com.linbit.linstor.utils.externaltools.ExtToolsManager;

import javax.inject.Inject;
import javax.inject.Provider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.inject.testing.fieldbinder.Bind;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

@SuppressWarnings("checkstyle:magicnumber")
public class SnapshotApiTest extends ApiTestBase
{
    private static final String TEST_NODE_NAME = "TestSatellite";
    private static final String TEST_RSC_NAME = "TestRsc";
    private static final String TEST_TARGET_RSC_NAME = "TargetRsc";
    private static final String TEST_SP_NAME = "TestStorPool";
    private static final String TEST_SNAP_NAME = "snap1";

    @Inject
    private Provider<CtrlSnapshotCrtApiCallHandler> snapCrtApiCallHandlerProvider;
    @Inject
    private Provider<CtrlSnapshotDeleteApiCallHandler> snapDelApiCallHandlerProvider;
    @Inject
    private Provider<CtrlSnapshotRestoreApiCallHandler> snapRestoreApiCallHandlerProvider;
    @Inject
    private Provider<CtrlSnapshotRollbackApiCallHandler> snapRollbackApiCallHandlerProvider;
    @Inject
    private CtrlRscCrtApiHelper ctrlRscCrtApiHelper;

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
    private SatelliteState satelliteState;

    public SnapshotApiTest() throws Exception
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

        satelliteState = new SatelliteState();

        stubSatellitePeer(mockSatellite, mockExtToolsMgr, satelliteState, false);
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
     * snapshot create tests (flux based, must not run within the testScope)
     */

    @Test
    public void crtSnapUnknownRscDfn() throws Exception
    {
        evaluateTest(
            new CreateSnapshotCall(ApiConsts.FAIL_NOT_FOUND_RSC_DFN)
                .setRscName("UnknownRsc")
        );
    }

    @Test
    public void crtSnapInvalidName() throws Exception
    {
        createRscDfnWithVlmDfn(TEST_RSC_NAME);

        evaluateTest(
            new CreateSnapshotCall(ApiConsts.FAIL_INVLD_SNAPSHOT_NAME)
                .setSnapName("Invalid Name") // blank is not allowed
        );
    }

    @Test
    public void crtSnapDuplicate() throws Exception
    {
        // the duplicate check happens before any resource / storage pool validation
        createRscDfnWithVlmDfn(TEST_RSC_NAME);
        createSnapDfn(TEST_RSC_NAME, TEST_SNAP_NAME);

        evaluateTest(
            new CreateSnapshotCall(ApiConsts.FAIL_EXISTS_SNAPSHOT_DFN)
        );
    }

    @Test
    public void crtSnapNoResources() throws Exception
    {
        // a resource definition without deployed resources has no diskful satellite to take the snapshot
        createRscDfnWithVlmDfn(TEST_RSC_NAME);

        evaluateTest(
            new CreateSnapshotCall(ApiConsts.FAIL_NOT_CONNECTED)
        );
    }

    @Test
    public void crtSnapProviderNotSupported() throws Exception
    {
        // storage spaces does not support snapshots
        deployTestResource(DeviceProviderKind.STORAGE_SPACES);
        satelliteOnline();

        evaluateTest(
            new CreateSnapshotCall(ApiConsts.FAIL_SNAPSHOTS_NOT_SUPPORTED)
        );
    }

    @Test
    public void crtSnapOfflineSatellite() throws Exception
    {
        deployTestResource(DeviceProviderKind.LVM_THIN);
        // mockSatellite stays offline

        evaluateTest(
            new CreateSnapshotCall(ApiConsts.FAIL_NOT_CONNECTED)
        );
    }

    @Test
    public void crtSnapSuccess() throws Exception
    {
        createDeployedSnapshot();

        SnapshotDefinition snapDfn = rscDfnMap.get(testRscName).getSnapshotDfn(testSnapName);
        assertThat(snapDfn).isNotNull();
        assertThat(snapDfn.getFlags().isSet(SnapshotDefinition.Flags.SUCCESSFUL)).isTrue();
        assertThat(snapDfn.getAllSnapshots()).hasSize(1);
    }

    /*
     * snapshot delete tests (flux based)
     */

    @Test
    public void delSnapUnknownRscDfn() throws Exception
    {
        evaluateTest(
            new DeleteSnapshotCall(ApiConsts.WARN_NOT_FOUND)
                .setRscName("UnknownRsc")
        );
    }

    @Test
    public void delSnapUnknownSnapshot() throws Exception
    {
        createRscDfnWithVlmDfn(TEST_RSC_NAME);

        evaluateTest(
            new DeleteSnapshotCall(ApiConsts.WARN_NOT_FOUND)
        );
    }

    @Test
    public void delSnapRegisteredDfnOnly() throws Exception
    {
        // a snapshot definition without snapshots on any node is deleted right away
        createRscDfnWithVlmDfn(TEST_RSC_NAME);
        createSnapDfn(TEST_RSC_NAME, TEST_SNAP_NAME);

        evaluateTest(
            new DeleteSnapshotCall(ApiConsts.DELETED)
        );

        assertThat(rscDfnMap.get(testRscName).getSnapshotDfn(testSnapName)).isNull();
    }

    @Test
    public void delSnapDeployed() throws Exception
    {
        createDeployedSnapshot();

        evaluateTest(
            new DeleteSnapshotCall(
                // snapshot marked for deletion
                ApiConsts.DELETED,
                // satellite update
                ApiConsts.MODIFIED,
                // snapshot deleted
                ApiConsts.DELETED,
                // snapshot definition deleted
                ApiConsts.DELETED
            )
        );

        assertThat(rscDfnMap.get(testRscName).getSnapshotDfn(testSnapName)).isNull();
    }

    /*
     * snapshot restore tests (flux based) - validation paths only
     */

    @Test
    public void restoreUnknownFromRscDfn() throws Exception
    {
        createRscDfnWithVlmDfn(TEST_TARGET_RSC_NAME);

        evaluateTest(
            new RestoreSnapshotCall(ApiConsts.FAIL_NOT_FOUND_RSC_DFN)
                .setFromRscName("UnknownRsc")
        );
    }

    @Test
    public void restoreUnknownSnapshot() throws Exception
    {
        createRscDfnWithVlmDfn(TEST_RSC_NAME);
        createRscDfnWithVlmDfn(TEST_TARGET_RSC_NAME);

        evaluateTest(
            new RestoreSnapshotCall(ApiConsts.FAIL_NOT_FOUND_SNAPSHOT_DFN)
        );
    }

    @Test
    public void restoreUnknownTargetRscDfn() throws Exception
    {
        createRscDfnWithVlmDfn(TEST_RSC_NAME);
        createSnapDfn(TEST_RSC_NAME, TEST_SNAP_NAME, SnapshotDefinition.Flags.SUCCESSFUL);

        evaluateTest(
            new RestoreSnapshotCall(ApiConsts.FAIL_NOT_FOUND_RSC_DFN)
                .setToRscName("UnknownRsc")
        );
    }

    @Test
    public void restoreTargetHasResources() throws Exception
    {
        deployTestResource(DeviceProviderKind.LVM_THIN);
        createSnapDfn(TEST_RSC_NAME, TEST_SNAP_NAME, SnapshotDefinition.Flags.SUCCESSFUL);

        evaluateTest(
            new RestoreSnapshotCall(ApiConsts.FAIL_EXISTS_RSC)
                .setToRscName(TEST_RSC_NAME)
        );
    }

    @Test
    public void restoreFailedSnapshotRejected() throws Exception
    {
        createRscDfnWithVlmDfn(TEST_RSC_NAME);
        createRscDfnWithVlmDfn(TEST_TARGET_RSC_NAME);
        // snapshot definition without the SUCCESSFUL flag, i.e. a failed / still in progress snapshot
        createSnapDfn(TEST_RSC_NAME, TEST_SNAP_NAME);

        evaluateTest(
            new RestoreSnapshotCall(ApiConsts.FAIL_UNKNOWN_ERROR)
        );
    }

    /*
     * snapshot rollback tests (flux based) - validation paths only
     */

    @Test
    public void rollbackUnknownRscDfn() throws Exception
    {
        evaluateTest(
            new RollbackSnapshotCall(ApiConsts.FAIL_NOT_FOUND_RSC_DFN)
                .setRscName("UnknownRsc")
        );
    }

    @Test
    public void rollbackUnknownSnapshot() throws Exception
    {
        createRscDfnWithVlmDfn(TEST_RSC_NAME);

        evaluateTest(
            new RollbackSnapshotCall(ApiConsts.FAIL_NOT_FOUND_SNAPSHOT_DFN)
        );
    }

    @Test
    public void rollbackFailedSnapshotRejected() throws Exception
    {
        deployTestResource(DeviceProviderKind.LVM_THIN);
        createSnapDfn(TEST_RSC_NAME, TEST_SNAP_NAME);

        evaluateTest(
            new RollbackSnapshotCall(ApiConsts.FAIL_UNKNOWN_ERROR)
        );
    }

    @Test
    public void rollbackOfflineSatelliteRejected() throws Exception
    {
        deployTestResource(DeviceProviderKind.LVM_THIN);
        createSnapDfn(TEST_RSC_NAME, TEST_SNAP_NAME, SnapshotDefinition.Flags.SUCCESSFUL);
        // mockSatellite stays offline

        evaluateTest(
            new RollbackSnapshotCall(ApiConsts.FAIL_NOT_CONNECTED)
        );
    }

    @Test
    public void rollbackResourceInUseRejected() throws Exception
    {
        deployTestResource(DeviceProviderKind.LVM_THIN);
        createSnapDfn(TEST_RSC_NAME, TEST_SNAP_NAME, SnapshotDefinition.Flags.SUCCESSFUL);
        satelliteOnline();
        satelliteState.setOnResource(testRscName, SatelliteResourceState::setInUse, Boolean.TRUE);

        evaluateTest(
            new RollbackSnapshotCall(ApiConsts.FAIL_IN_USE)
        );
    }

    private void satelliteOnline()
    {
        setSatelliteOnline(mockSatellite, true);
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
            .setSize(100 * 1024L)
            .build();

        leaveScope();
    }

    private void deployTestResource(DeviceProviderKind providerKind) throws Exception
    {
        createRscDfnWithVlmDfn(TEST_RSC_NAME);

        enterScope();

        StorPoolDefinition storPoolDfn = storPoolDefinitionFactory.create(testStorPoolName);
        storPoolDfnMap.put(testStorPoolName, storPoolDfn);
        StorPool storPool = storPoolFactory.create(
            testNode,
            storPoolDfn,
            providerKind,
            getFreeSpaceMgr(storPoolDfn, testNode),
            false
        );
        storPool.getFreeSpaceTracker().setCapacityInfo(10_000_000, 10_000_000);

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

    private void createDeployedSnapshot() throws Exception
    {
        deployTestResource(DeviceProviderKind.LVM_THIN);
        satelliteOnline();

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
        private final List<String> nodeNames;
        private String rscName;
        private String snapName;

        CreateSnapshotCall(long... expectedRcs)
        {
            super(
                ApiConsts.MASK_SNAPSHOT,
                ApiConsts.MASK_CRT,
                expectedRcs
            );
            nodeNames = new ArrayList<>();
            rscName = TEST_RSC_NAME;
            snapName = TEST_SNAP_NAME;
        }

        @Override
        public ApiCallRc executeApiCall()
        {
            return collect(
                snapCrtApiCallHandlerProvider.get().createSnapshot(
                    nodeNames,
                    rscName,
                    snapName,
                    Collections.emptyMap()
                )
            );
        }

        public CreateSnapshotCall setRscName(String rscNameRef)
        {
            rscName = rscNameRef;
            return this;
        }

        public CreateSnapshotCall setSnapName(String snapNameRef)
        {
            snapName = snapNameRef;
            return this;
        }
    }

    private class DeleteSnapshotCall extends AbsApiCallTester
    {
        private String rscName;
        private String snapName;

        DeleteSnapshotCall(long... expectedRcs)
        {
            super(
                ApiConsts.MASK_SNAPSHOT,
                ApiConsts.MASK_DEL,
                expectedRcs
            );
            rscName = TEST_RSC_NAME;
            snapName = TEST_SNAP_NAME;
        }

        @Override
        public ApiCallRc executeApiCall()
        {
            return collect(
                snapDelApiCallHandlerProvider.get().deleteSnapshot(
                    rscName,
                    snapName,
                    null,
                    false
                )
            );
        }

        public DeleteSnapshotCall setRscName(String rscNameRef)
        {
            rscName = rscNameRef;
            return this;
        }
    }

    private class RestoreSnapshotCall extends AbsApiCallTester
    {
        private String fromRscName;
        private String snapName;
        private String toRscName;

        RestoreSnapshotCall(long... expectedRcs)
        {
            super(
                // exceptions of the restore handler are converted with a resource ("restoring") context
                ApiConsts.MASK_RSC,
                ApiConsts.MASK_CRT,
                expectedRcs
            );
            fromRscName = TEST_RSC_NAME;
            snapName = TEST_SNAP_NAME;
            toRscName = TEST_TARGET_RSC_NAME;
        }

        @Override
        public ApiCallRc executeApiCall()
        {
            return collect(
                snapRestoreApiCallHandlerProvider.get().restoreSnapshot(
                    Collections.emptyList(),
                    fromRscName,
                    snapName,
                    toRscName,
                    Collections.emptyMap()
                )
            );
        }

        public RestoreSnapshotCall setFromRscName(String fromRscNameRef)
        {
            fromRscName = fromRscNameRef;
            return this;
        }

        public RestoreSnapshotCall setToRscName(String toRscNameRef)
        {
            toRscName = toRscNameRef;
            return this;
        }
    }

    private class RollbackSnapshotCall extends AbsApiCallTester
    {
        private String rscName;
        private String snapName;

        RollbackSnapshotCall(long... expectedRcs)
        {
            super(
                ApiConsts.MASK_SNAPSHOT,
                ApiConsts.MASK_MOD,
                expectedRcs
            );
            rscName = TEST_RSC_NAME;
            snapName = TEST_SNAP_NAME;
        }

        @Override
        public ApiCallRc executeApiCall()
        {
            return collect(
                snapRollbackApiCallHandlerProvider.get().rollbackSnapshot(
                    rscName,
                    snapName,
                    null
                )
            );
        }

        public RollbackSnapshotCall setRscName(String rscNameRef)
        {
            rscName = rscNameRef;
            return this;
        }
    }
}
