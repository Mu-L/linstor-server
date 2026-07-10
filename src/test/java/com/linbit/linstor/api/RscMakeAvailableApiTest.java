package com.linbit.linstor.api;

import com.linbit.linstor.InternalApiConsts;
import com.linbit.linstor.api.utils.AbsApiCallTester;
import com.linbit.linstor.core.ApiTestBase;
import com.linbit.linstor.core.apicallhandler.controller.CtrlRscCrtApiHelper;
import com.linbit.linstor.core.apicallhandler.controller.CtrlRscMakeAvailableApiCallHandler;
import com.linbit.linstor.core.apicallhandler.controller.FreeCapacityFetcher;
import com.linbit.linstor.core.identifier.NodeName;
import com.linbit.linstor.core.identifier.ResourceName;
import com.linbit.linstor.core.identifier.SharedStorPoolName;
import com.linbit.linstor.core.identifier.StorPoolName;
import com.linbit.linstor.core.objects.FreeSpaceMgr;
import com.linbit.linstor.core.objects.Node;
import com.linbit.linstor.core.objects.Resource;
import com.linbit.linstor.core.objects.ResourceDefinition;
import com.linbit.linstor.core.objects.StorPool;
import com.linbit.linstor.core.objects.StorPoolDefinition;
import com.linbit.linstor.layer.LayerPayload;
import com.linbit.linstor.layer.LayerPayload.DrbdRscDfnPayload;
import com.linbit.linstor.netcom.Peer;
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
    protected ExtToolsManager mockExtToolsMgr;

    private final NodeName testNodeName;
    private final ResourceName testRscName;
    private final StorPoolName testStorPoolName;

    private Node testSatelliteNode;
    private ResourceDefinition testRscDfn;

    public RscMakeAvailableApiTest() throws Exception
    {
        testNodeName = new NodeName("TestSatellite");
        testRscName = new ResourceName("TestRsc");
        testStorPoolName = new StorPoolName("TestStorPool");
    }

    @Before
    @Override
    public void setUp() throws Exception
    {
        super.setUp();

        Mockito.when(freeCapacityFetcher.fetchThinFreeCapacities(any()))
            .thenReturn(Mono.just(Collections.emptyMap()));

        stubSatellitePeer(mockSatellite, mockExtToolsMgr, new SatelliteState(), true);
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

    private void addStorPool() throws Exception
    {
        enterScope();

        StorPoolDefinition storPoolDfn = storPoolDefinitionFactory.create(testStorPoolName);
        storPoolDfnMap.put(testStorPoolName, storPoolDfn);
        FreeSpaceMgr fsm = freeSpaceMgrFactory.getInstance(
            new SharedStorPoolName(testNodeName, testStorPoolName)
        );
        StorPool storPool = storPoolFactory.create(
            testSatelliteNode,
            storPoolDfn,
            DeviceProviderKind.LVM,
            fsm,
            false
        );
        storPool.getFreeSpaceTracker().setCapacityInfo(10_000_000, 10_000_000);

        commitAndCleanUp(true);
    }

    private void createResourceOnNode() throws Exception
    {
        enterScope();

        Map<String, String> rscProps = new TreeMap<>();
        rscProps.put(ApiConsts.KEY_STOR_POOL_NAME, testStorPoolName.displayValue);
        ctrlRscCrtApiHelper.createResourceDb(
            testNodeName.displayValue,
            testRscName.displayValue,
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

        commitAndCleanUp(true);
    }

    private class MakeAvailableCall extends AbsApiCallTester
    {
        private String nodeName;
        private String rscName;
        private List<String> layerStack;
        private boolean diskful;

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
                Collections.emptyList()
            )
                .contextWrite(contextWrite())
                .toStream()
                .forEach(apiCallRc::addEntries);
            return apiCallRc;
        }
    }
}
