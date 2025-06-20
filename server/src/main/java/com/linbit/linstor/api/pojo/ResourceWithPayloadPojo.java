package com.linbit.linstor.api.pojo;

import com.linbit.linstor.annotation.Nullable;
import com.linbit.linstor.core.apis.ResourceWithPayloadApi;

import java.util.List;

public class ResourceWithPayloadPojo implements ResourceWithPayloadApi
{
    private final RscPojo rscPojo;
    private final List<String> layerStackStr;
    private final @Nullable Integer drbdNodeId;

    public ResourceWithPayloadPojo(RscPojo rscPojoRef, List<String> layerStackStrRef, @Nullable Integer drbdNodeIdRef)
    {
        rscPojo = rscPojoRef;
        layerStackStr = layerStackStrRef;
        drbdNodeId = drbdNodeIdRef;
    }

    @Override
    public RscPojo getRscApi()
    {
        return rscPojo;
    }

    @Override
    public List<String> getLayerStack()
    {
        return layerStackStr;
    }

    @Override
    public @Nullable Integer getDrbdNodeId()
    {
        return drbdNodeId;
    }

}
