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
import com.linbit.linstor.core.identifier.SharedStorPoolName;
import com.linbit.linstor.core.identifier.SnapshotName;
import com.linbit.linstor.core.identifier.StorPoolName;
import com.linbit.linstor.core.identifier.VolumeNumber;
import com.linbit.linstor.core.objects.FreeSpaceMgr;
import com.linbit.linstor.core.objects.Node;
import com.linbit.linstor.core.objects.Resource;
import com.linbit.linstor.core.objects.ResourceDefinition;
import com.linbit.linstor.core.objects.Snapshot;
import com.linbit.linstor.core.objects.SnapshotDefinition;
import com.linbit.linstor.core.objects.SnapshotVolumeDefinition;
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
    private static final String TEST_NODE_2_NAME = "TestSatellite2";
    private static final String TEST_RSC_NAME = "TestRsc";
    private static final String TEST_TARGET_RSC_NAME = "TargetRsc";
    private static final String TEST_SP_NAME = "TestStorPool";
    private static final String TEST_SNAP_NAME = "snap1";
    private static final String SHARED_RSC_NAME = "SharedRsc";
    private static final String SHARED_SP_NAME = "SharedPool";
    private static final String SHARED_SPACE_NAME = "SharedSpace";

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
    protected Peer mockSatellite2;

    @Mock
    protected ExtToolsManager mockExtToolsMgr;

    private final NodeName testNodeName;
    private final NodeName testNode2Name;
    private final ResourceName testRscName;
    private final ResourceName sharedRscName;
    private final SnapshotName testSnapName;
    private final StorPoolName testStorPoolName;

    private Node testNode;
    private Node testNode2;
    private SatelliteState satelliteState;
    private SatelliteState satelliteState2;

    public SnapshotApiTest() throws Exception
    {
        testNodeName = new NodeName(TEST_NODE_NAME);
        testNode2Name = new NodeName(TEST_NODE_2_NAME);
        testRscName = new ResourceName(TEST_RSC_NAME);
        sharedRscName = new ResourceName(SHARED_RSC_NAME);
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
     * shared storage pool snapshot tests: the snapshot data exists once on the shared pool, so the
     * snapshot objects are registered on every node holding a copy of the resource, while only the
     * node with the active copy takes the snapshot of the shared data
     */

    @Test
    public void crtSnapSharedSpRegistersOnAllCopies() throws Exception
    {
        Resource[] rscs = deploySharedResource();
        setRscActive(rscs[0], true);
        satelliteOnline();
        satellite2Online();

        evaluateTest(
            new CreateSnapshotCall()
                .setRscName(SHARED_RSC_NAME),
            false
        );

        SnapshotDefinition snapDfn = rscDfnMap.get(sharedRscName).getSnapshotDfn(testSnapName);
        assertThat(snapDfn).isNotNull();
        assertThat(snapDfn.getFlags().isSet(SnapshotDefinition.Flags.SUCCESSFUL)).isTrue();
        assertThat(snapDfn.getAllSnapshots()).hasSize(2);
        assertThat(snapDfn.getSnapshot(testNodeName)).isNotNull();
        assertThat(snapDfn.getSnapshot(testNode2Name)).isNotNull();
        // the inactive copy only registered the snapshot, its activation state is unchanged
        assertThat(rscs[1].getStateFlags().isSet(Resource.Flags.INACTIVE)).isTrue();
    }

    @Test
    public void crtSnapSharedSpAllInactiveActivatesOne() throws Exception
    {
        // no copy of the shared storage pool is active: one has to be activated before the
        // snapshot can be taken
        Resource[] rscs = deploySharedResource();
        satelliteOnline();
        satellite2Online();

        evaluateTest(
            new CreateSnapshotCall()
                .setRscName(SHARED_RSC_NAME),
            false
        );

        SnapshotDefinition snapDfn = rscDfnMap.get(sharedRscName).getSnapshotDfn(testSnapName);
        assertThat(snapDfn).isNotNull();
        assertThat(snapDfn.getFlags().isSet(SnapshotDefinition.Flags.SUCCESSFUL)).isTrue();
        assertThat(snapDfn.getAllSnapshots()).hasSize(2);

        int activeCount = 0;
        for (Resource rsc : rscs)
        {
            if (!rsc.getStateFlags().isSet(Resource.Flags.INACTIVE))
            {
                activeCount++;
            }
        }
        assertThat(activeCount).isEqualTo(1);
    }

    @Test
    public void crtSnapSharedSpActivationSelfHealsSnapshotObjects() throws Exception
    {
        // a legacy snapshot registered on only one copy (created before the snapshots-follow-copies
        // invariant): activating another copy creates the missing snapshot objects there, and the
        // new snapshot is registered on all copies
        Resource[] rscs = deploySharedResource();
        SnapshotDefinition snap0Dfn = createSnapshotOnNode(rscs[1], "snap0");
        satelliteOnline();
        satellite2Online();

        evaluateTest(
            new CreateSnapshotCall()
                .setRscName(SHARED_RSC_NAME),
            false
        );

        // all copies were inactive: the first copy was activated and received the missing snap0
        assertThat(rscs[0].getStateFlags().isSet(Resource.Flags.INACTIVE)).isFalse();
        assertThat(rscs[1].getStateFlags().isSet(Resource.Flags.INACTIVE)).isTrue();
        assertThat(snap0Dfn.getSnapshot(testNodeName)).isNotNull();
        assertThat(snap0Dfn.getAllSnapshots()).hasSize(2);

        SnapshotDefinition snapDfn = rscDfnMap.get(sharedRscName).getSnapshotDfn(testSnapName);
        assertThat(snapDfn).isNotNull();
        assertThat(snapDfn.getFlags().isSet(SnapshotDefinition.Flags.SUCCESSFUL)).isTrue();
        assertThat(snapDfn.getAllSnapshots()).hasSize(2);
    }

    @Test
    public void crtSnapSharedSpExplicitInactiveNodeRegistersOnAllCopies() throws Exception
    {
        // explicitly requesting the snapshot on a node whose copy is inactive is allowed: the active
        // copy takes the snapshot of the shared data, the objects are registered on both
        Resource[] rscs = deploySharedResource();
        setRscActive(rscs[0], true);
        satelliteOnline();
        satellite2Online();

        evaluateTest(
            new CreateSnapshotCall()
                .setRscName(SHARED_RSC_NAME)
                .setNodeNames(TEST_NODE_2_NAME),
            false
        );

        SnapshotDefinition snapDfn = rscDfnMap.get(sharedRscName).getSnapshotDfn(testSnapName);
        assertThat(snapDfn).isNotNull();
        assertThat(snapDfn.getFlags().isSet(SnapshotDefinition.Flags.SUCCESSFUL)).isTrue();
        assertThat(snapDfn.getAllSnapshots()).hasSize(2);
        // the activation states are unchanged
        assertThat(rscs[0].getStateFlags().isSet(Resource.Flags.INACTIVE)).isFalse();
        assertThat(rscs[1].getStateFlags().isSet(Resource.Flags.INACTIVE)).isTrue();
    }

    @Test
    public void crtSnapSharedSpAllInactiveExplicitNodeActivatesRequested() throws Exception
    {
        // all copies inactive and the snapshot requested on a specific node: that copy is preferred
        // for the activation, even if an older snapshot is not registered there (it is self-healed
        // by the activation)
        Resource[] rscs = deploySharedResource();
        SnapshotDefinition snap0Dfn = createSnapshotOnNode(rscs[0], "snap0");
        satelliteOnline();
        satellite2Online();

        evaluateTest(
            new CreateSnapshotCall()
                .setRscName(SHARED_RSC_NAME)
                .setNodeNames(TEST_NODE_2_NAME),
            false
        );

        assertThat(rscs[0].getStateFlags().isSet(Resource.Flags.INACTIVE)).isTrue();
        assertThat(rscs[1].getStateFlags().isSet(Resource.Flags.INACTIVE)).isFalse();
        assertThat(snap0Dfn.getSnapshot(testNode2Name)).isNotNull();

        SnapshotDefinition snapDfn = rscDfnMap.get(sharedRscName).getSnapshotDfn(testSnapName);
        assertThat(snapDfn).isNotNull();
        assertThat(snapDfn.getFlags().isSet(SnapshotDefinition.Flags.SUCCESSFUL)).isTrue();
        assertThat(snapDfn.getAllSnapshots()).hasSize(2);
    }

    @Test
    public void crtSnapSharedSpRefusedWhileDualActive() throws Exception
    {
        // during the dual-active window of a live migration both copies use the shared data at
        // once: the snapshot has to wait until only one copy is active again
        Resource[] rscs = deploySharedResource();
        setRscActive(rscs[0], true);
        setRscActive(rscs[1], true);
        satelliteOnline();
        satellite2Online();

        evaluateTest(
            new CreateSnapshotCall(ApiConsts.FAIL_IN_USE)
                .setRscName(SHARED_RSC_NAME)
        );

        assertThat(rscDfnMap.get(sharedRscName).getSnapshotDfn(testSnapName)).isNull();
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

    private void satellite2Online()
    {
        setSatelliteOnline(mockSatellite2, true);
    }

    /**
     * Creates a second node and a shared storage pool on both nodes, and deploys a STORAGE-only
     * resource {@link #SHARED_RSC_NAME} on both nodes. Both copies are flagged INACTIVE; use
     * {@link #setRscActive(Resource, boolean)} to activate one.
     *
     * @return the two resources, index 0 on the first node, index 1 on the second
     */
    private Resource[] deploySharedResource() throws Exception
    {
        satelliteState2 = new SatelliteState();
        stubSatellitePeer(mockSatellite2, mockExtToolsMgr, satelliteState2, false);

        enterScope();

        testNode2 = nodeFactory.create(
            testNode2Name,
            Node.Type.SATELLITE,
            null
        );
        testNode2.setPeer(mockSatellite2);
        nodesMap.put(testNode2Name, testNode2);

        StorPoolName sharedSpName = new StorPoolName(SHARED_SP_NAME);
        StorPoolDefinition storPoolDfn = storPoolDefinitionFactory.create(sharedSpName);
        storPoolDfnMap.put(sharedSpName, storPoolDfn);
        FreeSpaceMgr fsm = freeSpaceMgrFactory.getInstance(new SharedStorPoolName(SHARED_SPACE_NAME));
        for (Node node : new Node[] {testNode, testNode2})
        {
            StorPool storPool = storPoolFactory.create(
                node,
                storPoolDfn,
                DeviceProviderKind.LVM,
                fsm,
                false
            );
            storPool.getFreeSpaceTracker().setCapacityInfo(10_000_000, 10_000_000);
        }

        rscDfnMap.put(
            sharedRscName,
            resourceDefinitionTestFactory.builder(SHARED_RSC_NAME)
                .setLayerStack(new ArrayList<>(Collections.singletonList(DeviceLayerKind.STORAGE)))
                .build()
        );
        volumeDefinitionTestFactory.builder(SHARED_RSC_NAME, 0)
            .setSize(100 * 1024L)
            .build();

        leaveScope();

        Resource[] rscs = new Resource[2];
        rscs[0] = createSharedRscOnNode(testNodeName);
        rscs[1] = createSharedRscOnNode(testNode2Name);
        return rscs;
    }

    private Resource createSharedRscOnNode(NodeName nodeName) throws Exception
    {
        enterScope();

        Map<String, String> rscProps = new TreeMap<>();
        rscProps.put(ApiConsts.KEY_STOR_POOL_NAME, SHARED_SP_NAME);
        ctrlRscCrtApiHelper.createResourceDb(
            nodeName.displayValue,
            SHARED_RSC_NAME,
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
        Resource rsc = nodesMap.get(nodeName).getResource(sharedRscName);
        rsc.getStateFlags().enableFlags(Resource.Flags.INACTIVE);

        leaveScope();

        return rsc;
    }

    private void setRscActive(Resource rsc, boolean active) throws Exception
    {
        enterScope();
        if (active)
        {
            rsc.getStateFlags().disableFlags(Resource.Flags.INACTIVE);
        }
        else
        {
            rsc.getStateFlags().enableFlags(Resource.Flags.INACTIVE);
        }
        leaveScope();
    }

    /**
     * Creates a snapshot definition with a single snapshot on the given resource's node. No snapshot
     * volumes are created - the tests using this only care about the snapshot's existence per node.
     */
    private SnapshotDefinition createSnapshotOnNode(Resource rsc, String snapNameRef) throws Exception
    {
        enterScope();

        SnapshotDefinition snapDfn = snapshotDefinitionFactory.create(
            rsc.getResourceDefinition(),
            new SnapshotName(snapNameRef),
            new SnapshotDefinition.Flags[] {SnapshotDefinition.Flags.SUCCESSFUL}
        );
        SnapshotVolumeDefinition snapVlmDfn = snapshotVolumeDefinitionFactory.create(
            snapDfn,
            rsc.getResourceDefinition().getVolumeDfn(new VolumeNumber(0)),
            100 * 1024L,
            new SnapshotVolumeDefinition.Flags[0]
        );
        Snapshot snap = snapshotFactory.create(rsc, snapDfn, new Snapshot.Flags[] {});
        snapshotVolumeFactory.create(rsc, snap, snapVlmDfn);

        leaveScope();

        return snapDfn;
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

        public CreateSnapshotCall setNodeNames(String... nodeNamesRef)
        {
            nodeNames.clear();
            nodeNames.addAll(Arrays.asList(nodeNamesRef));
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
