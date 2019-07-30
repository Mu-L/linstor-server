package com.linbit.linstor.api.pojo;

import com.linbit.linstor.core.objects.ResourceDefinition;
import com.linbit.linstor.core.objects.SnapshotDefinition;
import com.linbit.linstor.core.objects.SnapshotVolumeDefinition;
import com.linbit.linstor.core.objects.SnapshotDefinition.SnapshotDfnApi;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SnapshotDfnListItemPojo implements SnapshotDefinition.SnapshotDfnListItemApi
{
    private final SnapshotDfnApi snapshotDfnApi;
    private final List<String> nodeNames;

    public SnapshotDfnListItemPojo(
        SnapshotDfnApi snapshotDfnPojoRef,
        List<String> nodeNamesRef
    )
    {
        snapshotDfnApi = snapshotDfnPojoRef;
        nodeNames = nodeNamesRef;
    }

    @Override
    public ResourceDefinition.RscDfnApi getRscDfn()
    {
        return snapshotDfnApi.getRscDfn();
    }

    @Override
    public UUID getUuid()
    {
        return snapshotDfnApi.getUuid();
    }

    @Override
    public String getSnapshotName()
    {
        return snapshotDfnApi.getSnapshotName();
    }

    @Override
    public long getFlags()
    {
        return snapshotDfnApi.getFlags();
    }

    @Override
    public Map<String, String> getProps()
    {
        return snapshotDfnApi.getProps();
    }

    @Override
    public List<SnapshotVolumeDefinition.SnapshotVlmDfnApi> getSnapshotVlmDfnList()
    {
        return snapshotDfnApi.getSnapshotVlmDfnList();
    }

    @Override
    public List<String> getNodeNames()
    {
        return nodeNames;
    }
}
