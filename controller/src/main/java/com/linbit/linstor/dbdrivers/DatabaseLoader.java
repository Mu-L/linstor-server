package com.linbit.linstor.dbdrivers;

import com.linbit.ImplementationError;
import com.linbit.InvalidNameException;
import com.linbit.ServiceName;
import com.linbit.linstor.annotation.SystemContext;
import com.linbit.linstor.api.ApiConsts;
import com.linbit.linstor.core.ControllerCoreModule;
import com.linbit.linstor.core.CoreModule;
import com.linbit.linstor.core.identifier.FreeSpaceMgrName;
import com.linbit.linstor.core.identifier.KeyValueStoreName;
import com.linbit.linstor.core.identifier.NodeName;
import com.linbit.linstor.core.identifier.ResourceGroupName;
import com.linbit.linstor.core.identifier.ResourceName;
import com.linbit.linstor.core.identifier.SnapshotName;
import com.linbit.linstor.core.identifier.StorPoolName;
import com.linbit.linstor.core.identifier.VolumeNumber;
import com.linbit.linstor.core.objects.DrbdLayerGenericDbDriver;
import com.linbit.linstor.core.objects.FreeSpaceMgr;
import com.linbit.linstor.core.objects.KeyValueStore;
import com.linbit.linstor.core.objects.KeyValueStoreDataGenericDbDriver;
import com.linbit.linstor.core.objects.LuksLayerGenericDbDriver;
import com.linbit.linstor.core.objects.NetInterfaceData;
import com.linbit.linstor.core.objects.NetInterfaceDataGenericDbDriver;
import com.linbit.linstor.core.objects.Node;
import com.linbit.linstor.core.objects.NodeConnectionData;
import com.linbit.linstor.core.objects.NodeConnectionDataGenericDbDriver;
import com.linbit.linstor.core.objects.NodeDataGenericDbDriver;
import com.linbit.linstor.core.objects.NvmeLayerGenericDbDriver;
import com.linbit.linstor.core.objects.Resource;
import com.linbit.linstor.core.objects.ResourceConnectionData;
import com.linbit.linstor.core.objects.ResourceConnectionDataGenericDbDriver;
import com.linbit.linstor.core.objects.ResourceData;
import com.linbit.linstor.core.objects.ResourceDataGenericDbDriver;
import com.linbit.linstor.core.objects.ResourceDefinition;
import com.linbit.linstor.core.objects.ResourceDefinitionDataGenericDbDriver;
import com.linbit.linstor.core.objects.ResourceGroup;
import com.linbit.linstor.core.objects.ResourceGroupDataGenericDbDriver;
import com.linbit.linstor.core.objects.ResourceLayerIdGenericDbDriver;
import com.linbit.linstor.core.objects.Snapshot;
import com.linbit.linstor.core.objects.SnapshotDataGenericDbDriver;
import com.linbit.linstor.core.objects.SnapshotDefinition;
import com.linbit.linstor.core.objects.SnapshotDefinitionDataGenericDbDriver;
import com.linbit.linstor.core.objects.SnapshotVolume;
import com.linbit.linstor.core.objects.SnapshotVolumeDataGenericDbDriver;
import com.linbit.linstor.core.objects.SnapshotVolumeDefinition;
import com.linbit.linstor.core.objects.SnapshotVolumeDefinitionGenericDbDriver;
import com.linbit.linstor.core.objects.StorPool;
import com.linbit.linstor.core.objects.StorPoolDataGenericDbDriver;
import com.linbit.linstor.core.objects.StorPoolDefinition;
import com.linbit.linstor.core.objects.StorPoolDefinitionDataGenericDbDriver;
import com.linbit.linstor.core.objects.StorageLayerGenericDbDriver;
import com.linbit.linstor.core.objects.Volume;
import com.linbit.linstor.core.objects.VolumeConnectionData;
import com.linbit.linstor.core.objects.VolumeConnectionDataGenericDbDriver;
import com.linbit.linstor.core.objects.VolumeDataGenericDbDriver;
import com.linbit.linstor.core.objects.VolumeDefinition;
import com.linbit.linstor.core.objects.VolumeDefinitionDataGenericDbDriver;
import com.linbit.linstor.core.objects.VolumeGroupData;
import com.linbit.linstor.core.objects.VolumeGroupDataGenericDbDriver;
import com.linbit.linstor.core.objects.ResourceLayerIdGenericDbDriver.RscLayerInfoData;
import com.linbit.linstor.core.objects.StorPool.InitMaps;
import com.linbit.linstor.layer.CtrlLayerDataHelper;
import com.linbit.linstor.layer.LayerPayload;
import com.linbit.linstor.propscon.InvalidKeyException;
import com.linbit.linstor.security.AccessContext;
import com.linbit.linstor.security.AccessDeniedException;
import com.linbit.linstor.storage.interfaces.categories.resource.RscLayerObject;
import com.linbit.linstor.storage.kinds.DeviceLayerKind;
import com.linbit.utils.Pair;
import com.linbit.utils.Triple;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Robert Altnoeder &lt;robert.altnoeder@linbit.com&gt;
 */
@Singleton
public class DatabaseLoader implements DatabaseDriver
{
    public static final ServiceName DFLT_SERVICE_INSTANCE_NAME;

    static
    {
        try
        {
            DFLT_SERVICE_INSTANCE_NAME = new ServiceName("GernericDatabaseService");
        }
        catch (InvalidNameException nameExc)
        {
            throw new ImplementationError(
                "The builtin default service instance name is not a valid ServiceName",
                nameExc
            );
        }
    }

    private final AccessContext dbCtx;
    private final ResourceGroupDataGenericDbDriver rscGrpDriver;
    private final NodeDataGenericDbDriver nodeDriver;
    private final NetInterfaceDataGenericDbDriver netIfDriver;
    private final NodeConnectionDataGenericDbDriver nodeConnDriver;
    private final ResourceDefinitionDataGenericDbDriver rscDfnDriver;
    private final ResourceDataGenericDbDriver rscDriver;
    private final ResourceConnectionDataGenericDbDriver rscConnDriver;
    private final VolumeDefinitionDataGenericDbDriver vlmDfnDriver;
    private final VolumeDataGenericDbDriver vlmDriver;
    private final VolumeConnectionDataGenericDbDriver vlmConnDriver;
    private final StorPoolDefinitionDataGenericDbDriver storPoolDfnDriver;
    private final StorPoolDataGenericDbDriver storPoolDriver;
    private final SnapshotDefinitionDataGenericDbDriver snapshotDefinitionDriver;
    private final SnapshotVolumeDefinitionGenericDbDriver snapshotVolumeDefinitionDriver;
    private final SnapshotDataGenericDbDriver snapshotDriver;
    private final SnapshotVolumeDataGenericDbDriver snapshotVolumeDriver;
    private final KeyValueStoreDataGenericDbDriver keyValueStoreDataGenericDbDriver;
    private final ResourceLayerIdGenericDbDriver rscLayerObjDriver;
    private final DrbdLayerGenericDbDriver drbdLayerDriver;
    private final LuksLayerGenericDbDriver luksLayerDriver;
    private final StorageLayerGenericDbDriver storageLayerDriver;
    private final NvmeLayerGenericDbDriver nvmeLayerDriver;
    private final Provider<CtrlLayerDataHelper> ctrlLayerDataHelper;

    private final CoreModule.NodesMap nodesMap;
    private final CoreModule.ResourceDefinitionMap rscDfnMap;
    private final CoreModule.ResourceGroupMap rscGrpMap;
    private final CoreModule.StorPoolDefinitionMap storPoolDfnMap;
    private final ControllerCoreModule.FreeSpaceMgrMap freeSpaceMgrMap;
    private final CoreModule.KeyValueStoreMap keyValueStoreMap;
    private VolumeGroupDataGenericDbDriver vlmGrpDriver;


    @Inject
    public DatabaseLoader(
        @SystemContext AccessContext privCtx,
        ResourceGroupDataGenericDbDriver rscGrpDriverRef,
        NodeDataGenericDbDriver nodeDriverRef,
        NetInterfaceDataGenericDbDriver netIfDriverRef,
        NodeConnectionDataGenericDbDriver nodeConnDriverRef,
        ResourceDefinitionDataGenericDbDriver resesourceDefinitionDriverRef,
        ResourceDataGenericDbDriver resourceDriverRef,
        ResourceConnectionDataGenericDbDriver rscConnDriverRef,
        VolumeGroupDataGenericDbDriver vlmGrpDriverRef,
        VolumeDefinitionDataGenericDbDriver vlmDfnDriverRef,
        VolumeDataGenericDbDriver volumeDriverRef,
        VolumeConnectionDataGenericDbDriver vlmConnDriverRef,
        StorPoolDefinitionDataGenericDbDriver storPoolDefinitionDriverRef,
        StorPoolDataGenericDbDriver storPoolDriverRef,
        SnapshotDefinitionDataGenericDbDriver snapshotDefinitionDriverRef,
        SnapshotVolumeDefinitionGenericDbDriver snapshotVolumeDefinitionDriverRef,
        SnapshotDataGenericDbDriver snapshotDriverRef,
        SnapshotVolumeDataGenericDbDriver snapshotVolumeDriverRef,
        KeyValueStoreDataGenericDbDriver keyValueStoreDataGenericDbDriverRef,
        ResourceLayerIdGenericDbDriver rscLayerObjDriverRef,
        DrbdLayerGenericDbDriver drbdLayerDriverRef,
        LuksLayerGenericDbDriver luksLayerDriverRef,
        StorageLayerGenericDbDriver storageLayerDriverRef,
        NvmeLayerGenericDbDriver nvmeLayerDriverRef,
        Provider<CtrlLayerDataHelper> ctrlLayerDataHelperRef,
        CoreModule.NodesMap nodesMapRef,
        CoreModule.ResourceDefinitionMap rscDfnMapRef,
        CoreModule.ResourceGroupMap rscGrpMapRef,
        CoreModule.StorPoolDefinitionMap storPoolDfnMapRef,
        ControllerCoreModule.FreeSpaceMgrMap freeSpaceMgrMapRef,
        CoreModule.KeyValueStoreMap keyValueStoreMapRef
    )
    {
        dbCtx = privCtx;
        rscGrpDriver = rscGrpDriverRef;
        nodeDriver = nodeDriverRef;
        netIfDriver = netIfDriverRef;
        nodeConnDriver = nodeConnDriverRef;
        rscDfnDriver = resesourceDefinitionDriverRef;
        rscDriver = resourceDriverRef;
        rscConnDriver = rscConnDriverRef;
        vlmGrpDriver = vlmGrpDriverRef;
        vlmDfnDriver = vlmDfnDriverRef;
        vlmDriver = volumeDriverRef;
        vlmConnDriver = vlmConnDriverRef;
        storPoolDfnDriver = storPoolDefinitionDriverRef;
        storPoolDriver = storPoolDriverRef;
        snapshotDefinitionDriver = snapshotDefinitionDriverRef;
        snapshotVolumeDefinitionDriver = snapshotVolumeDefinitionDriverRef;
        snapshotDriver = snapshotDriverRef;
        snapshotVolumeDriver = snapshotVolumeDriverRef;
        keyValueStoreDataGenericDbDriver = keyValueStoreDataGenericDbDriverRef;
        rscLayerObjDriver = rscLayerObjDriverRef;
        drbdLayerDriver = drbdLayerDriverRef;
        luksLayerDriver = luksLayerDriverRef;
        storageLayerDriver = storageLayerDriverRef;
        nvmeLayerDriver = nvmeLayerDriverRef;
        ctrlLayerDataHelper = ctrlLayerDataHelperRef;
        nodesMap = nodesMapRef;
        rscDfnMap = rscDfnMapRef;
        rscGrpMap = rscGrpMapRef;
        storPoolDfnMap = storPoolDfnMapRef;
        freeSpaceMgrMap = freeSpaceMgrMapRef;
        keyValueStoreMap = keyValueStoreMapRef;
    }

    /**
     * This method should only be called with an locked reconfiguration write lock
     */
    @Override
    public void loadAll() throws DatabaseException
    {
        try
        {
            // load the resource groups
            Map<ResourceGroup, ResourceGroup.InitMaps> loadedRscGroupsMap =
                Collections.unmodifiableMap(rscGrpDriver.loadAll());

            // temporary map to restore rscDfn <-> rscGroup relations
            Map<ResourceGroupName, ResourceGroup> tmpRscGroups =
                mapByName(loadedRscGroupsMap, ResourceGroup::getName);

            List<VolumeGroupData> vlmGrpList =
                Collections.unmodifiableList(vlmGrpDriver.loadAll(tmpRscGroups));
            for (VolumeGroupData vlmGrp : vlmGrpList)
            {
                loadedRscGroupsMap.get(vlmGrp.getResourceGroup()).getVlmGrpMap().put(
                    vlmGrp.getVolumeNumber(),
                    vlmGrp
                );
            }

            // load the main objects (nodes, rscDfns, storPoolDfns)
            Map<Node, Node.InitMaps> loadedNodesMap =
                Collections.unmodifiableMap(nodeDriver.loadAll());
            Map<ResourceDefinition, ResourceDefinition.InitMaps> loadedRscDfnsMap =
                Collections.unmodifiableMap(rscDfnDriver.loadAll(tmpRscGroups));
            Map<StorPoolDefinition, StorPoolDefinition.InitMaps> loadedStorPoolDfnsMap =
                Collections.unmodifiableMap(storPoolDfnDriver.loadAll());

            // add the rscDfns into the corresponding rscGroup rscDfn-map
            for (ResourceDefinition rscDfn : loadedRscDfnsMap.keySet())
            {
                loadedRscGroupsMap.get(rscDfn.getResourceGroup()).getRscDfnMap()
                    .put(rscDfn.getName(), rscDfn);
            }

            // build temporary maps for easier restoring of the remaining objects
            Map<NodeName, Node> tmpNodesMap =
                mapByName(loadedNodesMap, Node::getName);
            Map<ResourceName, ResourceDefinition> tmpRscDfnMap =
                mapByName(loadedRscDfnsMap, ResourceDefinition::getName);
            Map<StorPoolName, StorPoolDefinition> tmpStorPoolDfnMap =
                mapByName(loadedStorPoolDfnsMap, StorPoolDefinition::getName);

            // loading net interfaces
            List<NetInterfaceData> loadedNetIfs = netIfDriver.loadAll(tmpNodesMap);
            for (NetInterfaceData netIf : loadedNetIfs)
            {
                Node node = netIf.getNode();
                loadedNodesMap.get(node).getNetIfMap()
                    .put(netIf.getName(), netIf);

                String curStltConnName = node.getProps(dbCtx).getProp(ApiConsts.KEY_CUR_STLT_CONN_NAME);
                if (netIf.getName().value.equalsIgnoreCase(curStltConnName))
                {
                    node.setActiveStltConn(dbCtx, netIf);
                }
            }

            List<NodeConnectionData> loadedNodeConns = nodeConnDriver.loadAll(tmpNodesMap);
            for (NodeConnectionData nodeConn : loadedNodeConns)
            {
                Node sourceNode = nodeConn.getSourceNode(dbCtx);
                Node targetNode = nodeConn.getTargetNode(dbCtx);
                loadedNodesMap.get(sourceNode).getNodeConnMap().put(targetNode.getName(), nodeConn);
                loadedNodesMap.get(targetNode).getNodeConnMap().put(sourceNode.getName(), nodeConn);
            }

            // loading free space managers
            Map<FreeSpaceMgrName, FreeSpaceMgr> tmpFreeSpaceMgrMap = storPoolDriver.loadAllFreeSpaceMgrs();

            // loading storage pools
            Map<StorPool, StorPool.InitMaps> loadedStorPools = Collections.unmodifiableMap(storPoolDriver.loadAll(
                tmpNodesMap,
                tmpStorPoolDfnMap,
                tmpFreeSpaceMgrMap
            ));
            for (StorPool storPool : loadedStorPools.keySet())
            {
                loadedNodesMap.get(storPool.getNode()).getStorPoolMap()
                    .put(storPool.getName(), storPool);
                loadedStorPoolDfnsMap.get(storPool.getDefinition(dbCtx)).getStorPoolMap()
                    .put(storPool.getNode().getName(), storPool);
            }


            // temporary storPool map
            Map<Pair<NodeName, StorPoolName>, StorPool> tmpStorPoolMap =
                mapByName(loadedStorPools, storPool -> new Pair<>(
                    storPool.getNode().getName(),
                    storPool.getName()
                )
            );

            // loading resources
            Map<Resource, Resource.InitMaps> loadedResources =
                Collections.unmodifiableMap(rscDriver.loadAll(tmpNodesMap, tmpRscDfnMap));
            for (Resource rsc : loadedResources.keySet())
            {
                loadedNodesMap.get(rsc.getAssignedNode()).getRscMap()
                    .put(rsc.getDefinition().getName(), rsc);
                loadedRscDfnsMap.get(rsc.getDefinition()).getRscMap()
                    .put(rsc.getAssignedNode().getName(), rsc);
            }

            // temporary resource map
            Map<Pair<NodeName, ResourceName>, Resource> tmpRscMap =
                mapByName(loadedResources, rsc -> new Pair<>(
                    rsc.getAssignedNode().getName(),
                    rsc.getDefinition().getName()
                )
            );

            // loading resource connections
            List<ResourceConnectionData> loadedRscConns = rscConnDriver.loadAll(tmpRscMap);
            for (ResourceConnectionData rscConn : loadedRscConns)
            {
                Resource sourceResource = rscConn.getSourceResource(dbCtx);
                Resource targetResource = rscConn.getTargetResource(dbCtx);
                loadedResources.get(sourceResource).getRscConnMap().put(targetResource.getKey(), rscConn);
                loadedResources.get(targetResource).getRscConnMap().put(sourceResource.getKey(), rscConn);
            }

            // loading volume definitions
            Map<VolumeDefinition, VolumeDefinition.InitMaps> loadedVlmDfnMap =
                Collections.unmodifiableMap(vlmDfnDriver.loadAll(tmpRscDfnMap));

            for (VolumeDefinition vlmDfn : loadedVlmDfnMap.keySet())
            {
                loadedRscDfnsMap.get(vlmDfn.getResourceDefinition()).getVlmDfnMap()
                    .put(vlmDfn.getVolumeNumber(), vlmDfn);
            }

            // temporary volume definition map
            Map<Pair<ResourceName, VolumeNumber>, VolumeDefinition> tmpVlmDfnMap =
                mapByName(loadedVlmDfnMap, vlmDfn -> new Pair<>(
                    vlmDfn.getResourceDefinition().getName(),
                    vlmDfn.getVolumeNumber()
                )
            );

            // loading volumes
            Map<Volume, Volume.InitMaps> loadedVolumes = Collections.unmodifiableMap(
                vlmDriver.loadAll(
                    tmpRscMap,
                    tmpVlmDfnMap
                )
            );

            for (Volume vlm : loadedVolumes.keySet())
            {
                loadedResources.get(vlm.getResource()).getVlmMap()
                    .put(vlm.getVolumeDefinition().getVolumeNumber(), vlm);
                loadedVlmDfnMap.get(vlm.getVolumeDefinition()).getVlmMap()
                    .put(Resource.getStringId(vlm.getResource()), vlm);
            }

            // temporary volume map
            Map<Triple<NodeName, ResourceName, VolumeNumber>, Volume> tmpVlmMap =
                mapByName(loadedVolumes, vlm -> new Triple<>(
                    vlm.getResource().getAssignedNode().getName(),
                    vlm.getResourceDefinition().getName(),
                    vlm.getVolumeDefinition().getVolumeNumber()
                )
            );

            List<VolumeConnectionData> loadedVlmConns = vlmConnDriver.loadAll(tmpVlmMap);
            for (VolumeConnectionData vlmConn : loadedVlmConns)
            {
                Volume sourceVolume = vlmConn.getSourceVolume(dbCtx);
                Volume targetVolume = vlmConn.getTargetVolume(dbCtx);
                loadedVolumes.get(sourceVolume).getVolumeConnections().put(targetVolume.getKey(), vlmConn);
                loadedVolumes.get(targetVolume).getVolumeConnections().put(sourceVolume.getKey(), vlmConn);
            }

            // loading snapshot definitions
            Map<SnapshotDefinition, SnapshotDefinition.InitMaps> loadedSnapshotDfns =
                snapshotDefinitionDriver.loadAll(tmpRscDfnMap);
            for (SnapshotDefinition snapshotDfn : loadedSnapshotDfns.keySet())
            {
                loadedRscDfnsMap.get(snapshotDfn.getResourceDefinition()).getSnapshotDfnMap()
                    .put(snapshotDfn.getName(), snapshotDfn);
            }

            // temporary snapshot definition map
            Map<Pair<ResourceName, SnapshotName>, SnapshotDefinition> tmpSnapshotDfnMap =
                mapByName(loadedSnapshotDfns, snapshotDfn -> new Pair<>(
                        snapshotDfn.getResourceName(),
                        snapshotDfn.getName()
                    )
                );

            // loading snapshot volume definitions
            Map<SnapshotVolumeDefinition, SnapshotVolumeDefinition.InitMaps> loadedSnapshotVolumeDefinitions =
                snapshotVolumeDefinitionDriver.loadAll(tmpSnapshotDfnMap);
            for (SnapshotVolumeDefinition snapshotVolumeDefinition : loadedSnapshotVolumeDefinitions.keySet())
            {
                loadedSnapshotDfns.get(snapshotVolumeDefinition.getSnapshotDefinition())
                    .getSnapshotVolumeDefinitionMap()
                    .put(snapshotVolumeDefinition.getVolumeNumber(), snapshotVolumeDefinition);
            }

            Map<Triple<ResourceName, SnapshotName, VolumeNumber>, SnapshotVolumeDefinition> tmpSnapshotVlmDfnMap =
                mapByName(loadedSnapshotVolumeDefinitions, snapshotVlmDfn -> new Triple<>(
                    snapshotVlmDfn.getResourceName(),
                    snapshotVlmDfn.getSnapshotName(),
                    snapshotVlmDfn.getVolumeNumber()
                )
            );

            // loading snapshots
            Map<Snapshot, Snapshot.InitMaps> loadedSnapshots = snapshotDriver.loadAll(tmpNodesMap, tmpSnapshotDfnMap);
            for (Snapshot snapshot : loadedSnapshots.keySet())
            {
                loadedNodesMap.get(snapshot.getNode()).getSnapshotMap()
                    .put(new SnapshotDefinition.Key(snapshot.getSnapshotDefinition()), snapshot);
                loadedSnapshotDfns.get(snapshot.getSnapshotDefinition()).getSnapshotMap()
                    .put(snapshot.getNodeName(), snapshot);
            }

            Map<Triple<NodeName, ResourceName, SnapshotName>, Snapshot> tmpSnapshotMap =
                mapByName(loadedSnapshots, snapshot -> new Triple<>(
                    snapshot.getNodeName(),
                    snapshot.getResourceName(),
                    snapshot.getSnapshotName()
                )
            );

            // loading snapshot volumes
            List<SnapshotVolume> loadedSnapshotVolumes =
                snapshotVolumeDriver.loadAll(
                    tmpSnapshotMap,
                    tmpSnapshotVlmDfnMap,
                    tmpStorPoolMap
                );
            for (SnapshotVolume snapshotVolume : loadedSnapshotVolumes)
            {
                loadedSnapshots.get(snapshotVolume.getSnapshot()).getSnapshotVlmMap()
                    .put(snapshotVolume.getVolumeNumber(), snapshotVolume);
                loadedSnapshotVolumeDefinitions.get(snapshotVolume.getSnapshotVolumeDefinition()).getSnapshotVlmMap()
                    .put(snapshotVolume.getNodeName(), snapshotVolume);
            }

            // load and put key value store map
            Map<KeyValueStore, KeyValueStore.InitMaps> loadedKeyValueStoreMap =
                Collections.unmodifiableMap(keyValueStoreDataGenericDbDriver.loadAll());
            Map<KeyValueStoreName, KeyValueStore> tmpKeyValueStoreMap =
                mapByName(loadedKeyValueStoreMap, KeyValueStore::getName);
            keyValueStoreMap.putAll(tmpKeyValueStoreMap);

            // temporary storPool map
            Map<Pair<NodeName, StorPoolName>, Pair<StorPool, StorPool.InitMaps>> tmpStorPoolMapForLayers =
                new TreeMap<>();
            for (Entry<StorPool, StorPool.InitMaps> entry : loadedStorPools.entrySet())
            {
                StorPool storPool = entry.getKey();
                tmpStorPoolMapForLayers.put(
                    new Pair<>(
                        storPool.getNode().getName(),
                        storPool.getName()
                    ),
                    new Pair<>(
                        storPool,
                        entry.getValue()
                    )
                );
            }

            // load layer objects
            loadLayerObects(tmpRscDfnMap, tmpSnapshotMap, tmpStorPoolMapForLayers);

            nodesMap.putAll(tmpNodesMap);
            rscDfnMap.putAll(tmpRscDfnMap);
            rscGrpMap.putAll(tmpRscGroups);
            storPoolDfnMap.putAll(tmpStorPoolDfnMap);
            freeSpaceMgrMap.putAll(tmpFreeSpaceMgrMap);
        }
        catch (AccessDeniedException exc)
        {
            throw new ImplementationError("dbCtx has not enough privileges", exc);
        }
        catch (InvalidKeyException exc)
        {
            throw new ImplementationError("Invalid hardcoded props key", exc);
        }
    }

    private <NAME, DATA> TreeMap<NAME, DATA> mapByName(
        Map<DATA, ?> map, Function<? super DATA, NAME> nameMapper
    )
    {
        return map.keySet().stream().collect(
            Collectors.toMap(nameMapper, Function.identity(), throwingMerger(), TreeMap::new));
    }

    private void loadLayerObects(
        Map<ResourceName, ResourceDefinition> tmpRscDfnMapRef,
        Map<Triple<NodeName, ResourceName, SnapshotName>, Snapshot> tmpSnapshotMapRef,
        Map<Pair<NodeName, StorPoolName>, Pair<StorPool, InitMaps>> tmpStorPoolMapRef
    )
        throws DatabaseException, AccessDeniedException
    {
        storageLayerDriver.fetchForLoadAll(tmpStorPoolMapRef);

        // load RscDfnLayerObjects and VlmDfnLayerObjects
        drbdLayerDriver.loadLayerData(tmpRscDfnMapRef);
        storageLayerDriver.loadLayerData(tmpRscDfnMapRef);
        // no *DfnLayerObjects for nvme
        // no *DfnLayerObjects for luks

        List<Resource> resourcesWithLayerData = loadRscLayerData(tmpRscDfnMapRef, tmpStorPoolMapRef);

        drbdLayerDriver.clearLoadCache();
        storageLayerDriver.clearLoadAllCache();

        CtrlLayerDataHelper layerDataHelper = ctrlLayerDataHelper.get();
        LayerPayload payload = new LayerPayload();
        for (Resource rsc : resourcesWithLayerData)
        {
            // initialize all non-persisted, but later serialized variables
            List<DeviceLayerKind> layerStack = layerDataHelper.getLayerStack(rsc);
            layerDataHelper.ensureStackDataExists((ResourceData) rsc, layerStack, payload);
        }
    }

    private List<Resource> loadRscLayerData(
        Map<ResourceName, ResourceDefinition> tmpRscDfnMapRef,
        Map<Pair<NodeName, StorPoolName>, Pair<StorPool, InitMaps>> tmpStorPoolMapRef
    )
        throws DatabaseException, AccessDeniedException, ImplementationError
    {
        // load RscLayerObjects and VlmLayerObjects
        List<RscLayerInfoData> rscLayerInfoList = rscLayerObjDriver.loadAllResourceIds();

        Set<Integer> parentIds = null;
        boolean loadNext = true;
        Map<Integer, Pair<RscLayerObject, Set<RscLayerObject>>> rscLayerObjectChildren = new HashMap<>();

        List<Resource> resourcesWithLayerData = new ArrayList<>();
        while (loadNext)
        {
            // we need to load in a top-down fashion.
            List<RscLayerInfoData> nextRscInfoToLoad = nextRscLayerToLoad(rscLayerInfoList, parentIds);
            loadNext = !nextRscInfoToLoad.isEmpty();

            parentIds = new HashSet<>();
            for (RscLayerInfoData rli : nextRscInfoToLoad)
            {
                Pair<? extends RscLayerObject, Set<RscLayerObject>> rscLayerObjectPair;
                ResourceDefinition rscDfn = tmpRscDfnMapRef.get(rli.resourceName);
                Resource rsc = rscDfn.getResource(dbCtx, rli.nodeName);

                resourcesWithLayerData.add(rsc);

                RscLayerObject parent = null;
                Set<RscLayerObject> currentRscLayerDatasChildren = null;
                if (rli.parentId != null)
                {
                    Pair<RscLayerObject, Set<RscLayerObject>> pair = rscLayerObjectChildren.get(rli.parentId);

                    parent = pair.objA;
                    currentRscLayerDatasChildren = pair.objB;
                }
                switch (rli.kind)
                {
                    case DRBD:
                        rscLayerObjectPair = drbdLayerDriver.load(
                            rsc,
                            rli.id,
                            rli.rscSuffix,
                            parent,
                            tmpStorPoolMapRef
                        );
                        break;
                    case LUKS:
                        rscLayerObjectPair = luksLayerDriver.load(
                            rsc,
                            rli.id,
                            rli.rscSuffix,
                            parent
                        );
                        break;
                    case STORAGE:
                        rscLayerObjectPair = storageLayerDriver.load(
                            rsc,
                            rli.id,
                            rli.rscSuffix,
                            parent
                        );
                        break;
                    case NVME:
                        rscLayerObjectPair = nvmeLayerDriver.load(
                            rsc,
                            rli.id,
                            rli.rscSuffix,
                            parent
                        );
                        break;
                    default:
                        throw new ImplementationError("Unhandled case for device kind '" + rli.kind + "'");
                }
                RscLayerObject rscLayerObject = rscLayerObjectPair.objA;
                rscLayerObjectChildren.put(rli.id, new Pair<>(rscLayerObject, rscLayerObjectPair.objB));
                if (parent == null)
                {
                    rsc.setLayerData(dbCtx, rscLayerObject);
                }
                else
                {
                    currentRscLayerDatasChildren.add(rscLayerObject);
                }

                // rli will be the parent for the next iteration
                parentIds.add(rli.id);
            }
        }
        return resourcesWithLayerData;
    }

    private List<RscLayerInfoData> nextRscLayerToLoad(
        List<RscLayerInfoData> rscLayerInfoListRef,
        Set<Integer> ids
    )
    {
        List<RscLayerInfoData> ret;
        if (ids == null)
        {
            // root rscLayerObjects
            ret = rscLayerInfoListRef.stream().filter(rlo -> rlo.parentId == null).collect(Collectors.toList());
        }
        else
        {
            ret = rscLayerInfoListRef.stream().filter(rlo -> ids.contains(rlo.parentId)).collect(Collectors.toList());
        }
        return ret;
    }

    @Override
    public ServiceName getDefaultServiceInstanceName()
    {
        return DFLT_SERVICE_INSTANCE_NAME;
    }

    public static List<String> asStrList(Collection<DeviceLayerKind> layerStackRef)
    {
        List<String> ret = new ArrayList<>();
        for (DeviceLayerKind kind : layerStackRef)
        {
            ret.add(kind.name());
        }
        return ret;
    }

    public static List<DeviceLayerKind> asDevLayerKindList(Collection<String> strList)
    {
        List<DeviceLayerKind> ret = new ArrayList<>();
        for (String str : strList)
        {
            ret.add(DeviceLayerKind.valueOf(str));
        }
        return ret;
    }

    public static void handleAccessDeniedException(AccessDeniedException accDeniedExc)
        throws ImplementationError
    {
        throw new ImplementationError(
            "Database's access context has insufficient permissions",
            accDeniedExc
        );
    }

    private static <T> BinaryOperator<T> throwingMerger()
    {
        return (key, value) ->
        {
            throw new ImplementationError("At least two objects have the same name.\n" +
                "That should have caused an sql exception when inserting. Key: '" + key + "'",
                null
            );
        };
    }
}
