package com.linbit.linstor.api;

import com.linbit.linstor.InternalApiConsts;
import com.linbit.linstor.api.utils.AbsApiCallTester;
import com.linbit.linstor.core.ApiTestBase;
import com.linbit.linstor.core.apicallhandler.controller.CtrlRscCrtApiHelper;
import com.linbit.linstor.core.apicallhandler.controller.CtrlRscMakeAvailableApiCallHandler;
import com.linbit.linstor.core.apicallhandler.controller.FreeCapacityFetcher;
import com.linbit.linstor.annotation.Nullable;
import com.linbit.linstor.core.identifier.NodeName;
import com.linbit.linstor.core.identifier.ResourceName;
import com.linbit.linstor.core.identifier.SharedStorPoolName;
import com.linbit.linstor.core.identifier.StorPoolName;
import com.linbit.linstor.core.objects.FreeSpaceMgr;
import com.linbit.linstor.core.objects.Node;
import com.linbit.linstor.core.objects.Resource;
import com.linbit.linstor.core.objects.ResourceConnection;
import com.linbit.linstor.core.objects.ResourceDefinition;
import com.linbit.linstor.core.objects.StorPool;
import com.linbit.linstor.core.objects.StorPoolDefinition;
import com.linbit.linstor.layer.LayerPayload;
import com.linbit.linstor.layer.LayerPayload.DrbdRscDfnPayload;
import com.linbit.linstor.netcom.Peer;
import com.linbit.linstor.satellitestate.SatelliteResourceState;
import com.linbit.linstor.satellitestate.SatelliteState;
import com.linbit.linstor.storage.interfaces.layers.drbd.DrbdRscDfnObject.TransportType;
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
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.google.inject.testing.fieldbinder.Bind;
import org.junit.After;
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
public class RscMakeAvailableApiTest extends ApiTestBase
{
    private static final String PROP_KEY_TWO_PRIMARIES = "allow-two-primaries";
    private static final String PROP_KEY_PROTOCOL = "protocol";

    @Inject
    private Provider<CtrlRscMakeAvailableApiCallHandler> rscMakeAvailableApiCallHandlerProvider;
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
    private final StorPoolName testStorPoolName;
    private final StorPoolName testDisklessStorPoolName;

    private Node testSatelliteNode;
    private ResourceDefinition testRscDfn;
    private SatelliteState satelliteState;
    private SatelliteState satelliteState2;

    public RscMakeAvailableApiTest() throws Exception
    {
        testNodeName = new NodeName("TestSatellite");
        testNode2Name = new NodeName("TestSatellite2");
        testRscName = new ResourceName("TestRsc");
        testStorPoolName = new StorPoolName("TestStorPool");
        testDisklessStorPoolName = new StorPoolName("TestDisklessPool");
    }

    @Before
    @Override
    public void setUp() throws Exception
    {
        super.setUp();

        Mockito.when(freeCapacityFetcher.fetchThinFreeCapacities(any()))
            .thenReturn(Mono.just(Collections.emptyMap()));

        satelliteState = new SatelliteState();
        satelliteState2 = new SatelliteState();
        stubSatellitePeer(mockSatellite, mockExtToolsMgr, satelliteState, true);
        stubSatellitePeer(mockSatellite2, mockExtToolsMgr, satelliteState2, true);
        stubAllExtToolsSupported(mockExtToolsMgr);

        testSatelliteNode = nodeFactory.create(
            testNodeName,
            Node.Type.SATELLITE,
            null
        );
        testSatelliteNode.setPeer(mockSatellite);
        nodesMap.put(testNodeName, testSatelliteNode);

        LayerPayload payload = new LayerPayload();
        DrbdRscDfnPayload drbdRscDfn = payload.getDrbdRscDfn();
        drbdRscDfn.sharedSecret = "NotTellingYou";
        drbdRscDfn.transportType = TransportType.IP;
        testRscDfn = resourceDefinitionFactory.create(
            testRscName,
            null,
            null,
            Arrays.asList(DeviceLayerKind.DRBD, DeviceLayerKind.STORAGE),
            payload,
            createDefaultResourceGroup()
        );
        rscDfnMap.put(testRscName, testRscDfn);

        ctrlConf.setProp(
            InternalApiConsts.KEY_CLUSTER_LOCAL_ID,
            randomUUID().toString(),
            ApiConsts.NAMESPC_CLUSTER
        );

        commitAndCleanUp(true);
    }

    @After
    @Override
    public void tearDown() throws Exception
    {
        commitAndCleanUp(false);
    }

    @Test
    public void makeAvailableUnknownRscDfn() throws Exception
    {
        evaluateTest(
            new MakeAvailableCall(ApiConsts.FAIL_NOT_FOUND_RSC_DFN)
                .setRscName("UnknownRsc")
        );
    }

    @Test
    public void makeAvailableUnknownNode() throws Exception
    {
        evaluateTest(
            new MakeAvailableCall(ApiConsts.FAIL_NOT_FOUND_NODE)
                .setNodeName("UnknownNode")
        );
    }

    @Test
    public void makeAvailableAlreadyDeployed() throws Exception
    {
        addStorPool();
        createResourceOnNode();

        evaluateTest(
            new MakeAvailableCall(
                // "Resource already deployed as requested"
                ApiConsts.MASK_SUCCESS
            )
        );

        assertThat(testSatelliteNode.getResource(testRscName)).isNotNull();
        assertThat(testRscDfn.getResourceCount()).isEqualTo(1);
    }

    @Test
    public void makeAvailableLayerStackMismatch() throws Exception
    {
        addStorPool();
        createResourceOnNode();

        evaluateTest(
            new MakeAvailableCall(ApiConsts.FAIL_INVLD_LAYER_STACK)
                .setLayerStack("storage")
        );
    }

    @Test
    public void makeAvailableNoStorPoolFound() throws Exception
    {
        // no storage pool exists on the target node, so the autoplacer cannot place the resource
        evaluateTest(
            new MakeAvailableCall(ApiConsts.FAIL_NOT_FOUND_STOR_POOL)
        );

        assertThat(testRscDfn.getResourceCount()).isEqualTo(0);
    }

    @Test
    public void makeAvailableDeployNewResource() throws Exception
    {
        Mockito.when(mockPeer.isOnline()).thenReturn(true);
        addStorPool();

        evaluateTest(
            new MakeAvailableCall(
                // StorPoolName property set on the new resource
                ApiConsts.CREATED,
                // Registered
                ApiConsts.CREATED,
                // Deployed
                ApiConsts.MODIFIED,
                // No volumes => WARN_NOT_FOUND response
                ApiConsts.WARN_NOT_FOUND,
                // updated resync-after entries
                ApiConsts.MASK_INFO
            )
        );

        assertThat(testSatelliteNode.getResource(testRscName)).isNotNull();
        assertThat(testRscDfn.getResourceCount()).isEqualTo(1);
    }

    @Test
    public void makeAvailableRevertsDeleteFlags() throws Exception
    {
        addStorPool();
        createResourceOnNode();

        enterScope();
        Resource rsc = testSatelliteNode.getResource(testRscName);
        rsc.getStateFlags().enableFlags(Resource.Flags.DELETE);
        commitAndCleanUp(true);

        evaluateTest(
            new MakeAvailableCall(
                // "Resource already deployed as requested"
                ApiConsts.MASK_SUCCESS
            )
        );

        assertThat(rsc.getStateFlags().isSet(Resource.Flags.DELETE)).isFalse();
    }

    /*
     * auto_manage_dual_primary (live migration) tests
     */

    @Test
    public void makeAvailableDualPrimaryWithoutDrbd() throws Exception
    {
        evaluateTest(
            new MakeAvailableCall(ApiConsts.FAIL_INVLD_LAYER_STACK)
                .setLayerStack("storage")
                .setAutoManageDualPrimary(true)
        );
    }

    @Test
    public void makeAvailableDualPrimaryNoSourcePlainAttach() throws Exception
    {
        // the resource is not in use anywhere, so there is no live migration to prepare and the
        // resource is simply made available: clients that cannot tell a live-migration attach from
        // a plain attach (e.g. Proxmox) always set the option
        Mockito.when(mockPeer.isOnline()).thenReturn(true);
        addStorPool();

        evaluateTest(
            new MakeAvailableCall(
                // StorPoolName property set on the new resource
                ApiConsts.CREATED,
                // Registered
                ApiConsts.CREATED,
                // Deployed
                ApiConsts.MODIFIED,
                // No volumes => WARN_NOT_FOUND response
                ApiConsts.WARN_NOT_FOUND,
                // updated resync-after entries
                ApiConsts.MASK_INFO,
                // not in use on another node, no dual-primary preparation needed
                ApiConsts.MASK_INFO
            )
                .setAutoManageDualPrimary(true)
        );

        assertThat(testSatelliteNode.getResource(testRscName)).isNotNull();
        assertThat(testRscDfn.getResourceCount()).isEqualTo(1);
        // nothing was armed for dual-primary
        assertThat(getLiveMigrateMarker(InternalApiConsts.KEY_LIVE_MIGRATE_SOURCE_NODE)).isNull();
        assertThat(testRscDfn.getProps().getProp(PROP_KEY_TWO_PRIMARIES, ApiConsts.NAMESPC_DRBD_NET_OPTIONS))
            .isNull();
    }

    @Test
    public void makeAvailableDualPrimaryTargetAlreadyInUse() throws Exception
    {
        addStorPool();
        createResourceOnNode();
        setInUse(satelliteState, Boolean.TRUE);

        evaluateTest(
            new MakeAvailableCall(
                // "Resource already deployed as requested"
                ApiConsts.MASK_SUCCESS,
                // already in use on the target node, no dual-primary preparation needed
                ApiConsts.MASK_INFO
            )
                .setAutoManageDualPrimary(true)
        );

        assertThat(getLiveMigrateMarker(InternalApiConsts.KEY_LIVE_MIGRATE_SOURCE_NODE)).isNull();
    }

    @Test
    public void makeAvailableDualPrimarySetsConnProps() throws Exception
    {
        addStorPool();
        createResourceOnNode();
        Node node2 = createSecondNode();
        createStorPool(node2, testDisklessStorPoolName, DeviceProviderKind.DISKLESS);
        createRscOnNode(testNode2Name, Resource.Flags.DRBD_DISKLESS.flagValue, testDisklessStorPoolName);

        // the workload runs on the first node, the resource is migrated to the second one
        setInUse(satelliteState, Boolean.TRUE);

        evaluateTest(
            new MakeAvailableCall()
                .setNodeName(testNode2Name.displayValue)
                .setAutoManageDualPrimary(true),
            false
        );

        ResourceConnection rscConn = getRscConn(testSatelliteNode, node2);
        assertThat(rscConn).isNotNull();
        assertThat(rscConn.getProps().getProp(PROP_KEY_TWO_PRIMARIES, ApiConsts.NAMESPC_DRBD_NET_OPTIONS))
            .isEqualTo("yes");
        // the effective protocol already was C, nothing to override
        assertThat(rscConn.getProps().getProp(PROP_KEY_PROTOCOL, ApiConsts.NAMESPC_DRBD_NET_OPTIONS)).isNull();

        assertThat(getLiveMigrateMarker(InternalApiConsts.KEY_LIVE_MIGRATE_SOURCE_NODE))
            .isEqualTo(testNodeName.displayValue);
        assertThat(getLiveMigrateMarker(InternalApiConsts.KEY_LIVE_MIGRATE_TARGET_NODE))
            .isEqualTo(testNode2Name.displayValue);
        assertThat(getLiveMigrateMarker(InternalApiConsts.KEY_LIVE_MIGRATE_PROTOCOL_SET_ON)).isNull();

        // an identical second call is an idempotent re-apply
        evaluateTest(
            new MakeAvailableCall()
                .setNodeName(testNode2Name.displayValue)
                .setAutoManageDualPrimary(true),
            false
        );
        assertThat(rscConn.getProps().getProp(PROP_KEY_TWO_PRIMARIES, ApiConsts.NAMESPC_DRBD_NET_OPTIONS))
            .isEqualTo("yes");
    }

    @Test
    public void makeAvailableDualPrimaryEnforcesProtocolC() throws Exception
    {
        addStorPool();
        createResourceOnNode();
        Node node2 = createSecondNode();
        createStorPool(node2, testDisklessStorPoolName, DeviceProviderKind.DISKLESS);
        createRscOnNode(testNode2Name, Resource.Flags.DRBD_DISKLESS.flagValue, testDisklessStorPoolName);

        enterScope();
        testRscDfn.getProps().setProp(PROP_KEY_PROTOCOL, "A", ApiConsts.NAMESPC_DRBD_NET_OPTIONS);
        commitAndCleanUp(true);

        setInUse(satelliteState, Boolean.TRUE);

        evaluateTest(
            new MakeAvailableCall()
                .setNodeName(testNode2Name.displayValue)
                .setAutoManageDualPrimary(true),
            false
        );

        ResourceConnection rscConn = getRscConn(testSatelliteNode, node2);
        assertThat(rscConn).isNotNull();
        assertThat(rscConn.getProps().getProp(PROP_KEY_PROTOCOL, ApiConsts.NAMESPC_DRBD_NET_OPTIONS))
            .isEqualTo("C");
        // the user-set protocol of the rsc-dfn must not be touched
        assertThat(testRscDfn.getProps().getProp(PROP_KEY_PROTOCOL, ApiConsts.NAMESPC_DRBD_NET_OPTIONS))
            .isEqualTo("A");
        assertThat(getLiveMigrateMarker(InternalApiConsts.KEY_LIVE_MIGRATE_PROTOCOL_SET_ON))
            .isEqualTo(InternalApiConsts.SET_ON_RSC_CONN);
        assertThat(getLiveMigrateMarker(InternalApiConsts.KEY_LIVE_MIGRATE_PREV_PROTOCOL)).isNull();
    }

    @Test
    public void makeAvailableDualPrimaryBothDisklessUsesRscDfn() throws Exception
    {
        // between two diskless resources no DRBD connection section is generated, so the props have
        // to be set on rsc-dfn level instead of the rsc-conn
        createStorPool(testSatelliteNode, testDisklessStorPoolName, DeviceProviderKind.DISKLESS);
        createRscOnNode(testNodeName, Resource.Flags.DRBD_DISKLESS.flagValue, testDisklessStorPoolName);
        Node node2 = createSecondNode();
        createStorPool(node2, testDisklessStorPoolName, DeviceProviderKind.DISKLESS);
        createRscOnNode(testNode2Name, Resource.Flags.DRBD_DISKLESS.flagValue, testDisklessStorPoolName);

        setInUse(satelliteState, Boolean.TRUE);

        evaluateTest(
            new MakeAvailableCall()
                .setNodeName(testNode2Name.displayValue)
                .setAutoManageDualPrimary(true),
            false
        );

        assertThat(testRscDfn.getProps().getProp(PROP_KEY_TWO_PRIMARIES, ApiConsts.NAMESPC_DRBD_NET_OPTIONS))
            .isEqualTo("yes");
        assertThat(getLiveMigrateMarker(InternalApiConsts.KEY_LIVE_MIGRATE_SOURCE_NODE))
            .isEqualTo(testNodeName.displayValue);
    }

    @Test
    public void makeAvailableDualPrimaryConflictingMigration() throws Exception
    {
        addStorPool();
        createResourceOnNode();
        Node node2 = createSecondNode();
        createStorPool(node2, testDisklessStorPoolName, DeviceProviderKind.DISKLESS);
        createRscOnNode(testNode2Name, Resource.Flags.DRBD_DISKLESS.flagValue, testDisklessStorPoolName);
        setInUse(satelliteState, Boolean.TRUE);

        // another migration (different target) is already prepared
        enterScope();
        testRscDfn.getProps().setProp(
            InternalApiConsts.KEY_LIVE_MIGRATE_SOURCE_NODE,
            testNodeName.displayValue,
            InternalApiConsts.NAMESPC_LIVE_MIGRATE
        );
        testRscDfn.getProps().setProp(
            InternalApiConsts.KEY_LIVE_MIGRATE_TARGET_NODE,
            "SomeOtherTarget",
            InternalApiConsts.NAMESPC_LIVE_MIGRATE
        );
        commitAndCleanUp(true);

        evaluateTest(
            new MakeAvailableCall(ApiConsts.FAIL_EXISTS_LIVE_MIGRATE)
                .setNodeName(testNode2Name.displayValue)
                .setAutoManageDualPrimary(true)
        );
    }

    @Test
    public void makeAvailableDualPrimaryMultipleInUse() throws Exception
    {
        addStorPool();
        createResourceOnNode();
        Node node2 = createSecondNode();
        createStorPool(node2, testDisklessStorPoolName, DeviceProviderKind.DISKLESS);
        createRscOnNode(testNode2Name, Resource.Flags.DRBD_DISKLESS.flagValue, testDisklessStorPoolName);

        // without live-migrate markers an in-use on two nodes leaves the migration source undecidable
        setInUse(satelliteState, Boolean.TRUE);
        setInUse(satelliteState2, Boolean.TRUE);

        evaluateTest(
            new MakeAvailableCall(ApiConsts.FAIL_EXISTS_LIVE_MIGRATE)
                .setNodeName(testNode2Name.displayValue)
                .setAutoManageDualPrimary(true)
        );
    }

    /*
     * helpers
     */

    private void addStorPool() throws Exception
    {
        createStorPool(testSatelliteNode, testStorPoolName, DeviceProviderKind.LVM);
    }

    private StorPool createStorPool(Node node, StorPoolName storPoolName, DeviceProviderKind kind) throws Exception
    {
        enterScope();

        StorPoolDefinition storPoolDfn = storPoolDfnMap.get(storPoolName);
        if (storPoolDfn == null)
        {
            storPoolDfn = storPoolDefinitionFactory.create(storPoolName);
            storPoolDfnMap.put(storPoolName, storPoolDfn);
        }
        FreeSpaceMgr fsm = freeSpaceMgrFactory.getInstance(
            new SharedStorPoolName(node.getName(), storPoolName)
        );
        StorPool storPool = storPoolFactory.create(
            node,
            storPoolDfn,
            kind,
            fsm,
            false
        );
        storPool.getFreeSpaceTracker().setCapacityInfo(10_000_000, 10_000_000);

        commitAndCleanUp(true);

        return storPool;
    }

    private void createResourceOnNode() throws Exception
    {
        createRscOnNode(testNodeName, 0L, testStorPoolName);
    }

    private void createRscOnNode(NodeName nodeName, long flags, StorPoolName storPoolName) throws Exception
    {
        enterScope();

        Map<String, String> rscProps = new TreeMap<>();
        rscProps.put(ApiConsts.KEY_STOR_POOL_NAME, storPoolName.displayValue);
        ctrlRscCrtApiHelper.createResourceDb(
            nodeName.displayValue,
            testRscName.displayValue,
            flags,
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

        commitAndCleanUp(true);
    }

    private Node createSecondNode() throws Exception
    {
        enterScope();

        Node node = nodeFactory.create(
            testNode2Name,
            Node.Type.SATELLITE,
            null
        );
        node.setPeer(mockSatellite2);
        nodesMap.put(testNode2Name, node);

        commitAndCleanUp(true);

        return node;
    }

    private void setInUse(SatelliteState stltStateRef, @Nullable Boolean inUse)
    {
        stltStateRef.setOnResource(testRscName, SatelliteResourceState::setInUse, inUse);
    }

    private @Nullable String getLiveMigrateMarker(String key) throws Exception
    {
        return testRscDfn.getProps().getProp(key, InternalApiConsts.NAMESPC_LIVE_MIGRATE);
    }

    private @Nullable ResourceConnection getRscConn(Node nodeA, Node nodeB)
    {
        Resource rscA = nodeA.getResource(testRscName);
        Resource rscB = nodeB.getResource(testRscName);
        ResourceConnection rscConn = null;
        if (rscA != null && rscB != null)
        {
            rscConn = rscA.getAbsResourceConnection(rscB);
        }
        return rscConn;
    }

    private class MakeAvailableCall extends AbsApiCallTester
    {
        private String nodeName;
        private String rscName;
        private List<String> layerStack;
        private boolean diskful;
        private boolean autoManageDualPrimary;

        MakeAvailableCall(long... expectedRcs)
        {
            super(
                ApiConsts.MASK_RSC,
                ApiConsts.MASK_CRT,
                expectedRcs
            );
            nodeName = testNodeName.displayValue;
            rscName = testRscName.displayValue;
            layerStack = new ArrayList<>();
            diskful = false;
            autoManageDualPrimary = false;
        }

        MakeAvailableCall setNodeName(String nodeNameRef)
        {
            nodeName = nodeNameRef;
            return this;
        }

        MakeAvailableCall setRscName(String rscNameRef)
        {
            rscName = rscNameRef;
            return this;
        }

        MakeAvailableCall setLayerStack(String... layers)
        {
            layerStack = Arrays.asList(layers);
            return this;
        }

        MakeAvailableCall setDiskful(boolean diskfulRef)
        {
            diskful = diskfulRef;
            return this;
        }

        MakeAvailableCall setAutoManageDualPrimary(boolean autoManageDualPrimaryRef)
        {
            autoManageDualPrimary = autoManageDualPrimaryRef;
            return this;
        }

        @Override
        public ApiCallRc executeApiCall()
        {
            ApiCallRcImpl apiCallRc = new ApiCallRcImpl();
            rscMakeAvailableApiCallHandlerProvider.get().makeResourceAvailable(
                nodeName,
                rscName,
                layerStack,
                diskful,
                null,
                false,
                Collections.emptyList(),
                autoManageDualPrimary
            )
                .contextWrite(contextWrite())
                .toStream()
                .forEach(apiCallRc::addEntries);
            return apiCallRc;
        }
    }
}
