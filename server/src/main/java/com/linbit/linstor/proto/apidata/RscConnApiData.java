package com.linbit.linstor.proto.apidata;

import com.linbit.linstor.core.objects.ResourceConnection;
import com.linbit.linstor.proto.common.RscConnOuterClass;

import java.util.Map;
import java.util.UUID;

public class RscConnApiData implements ResourceConnection.RscConnApi
{
    private RscConnOuterClass.RscConn rscConnProto;

    public RscConnApiData(RscConnOuterClass.RscConn rscConnRef)
    {
        rscConnProto = rscConnRef;
    }

    @Override
    public UUID getUuid()
    {
        return rscConnProto.hasRscConnUuid() ? UUID.fromString(rscConnProto.getRscConnUuid()) : null;
    }

    @Override
    public String getSourceNodeName()
    {
        return rscConnProto.getNodeName1();
    }

    @Override
    public String getTargetNodeName()
    {
        return rscConnProto.getNodeName2();
    }

    @Override
    public String getResourceName()
    {
        return rscConnProto.getRscName();
    }

    @Override
    public Map<String, String> getProps()
    {
        return rscConnProto.getRscConnPropsMap();
    }

    @Override
    public long getFlags()
    {
        return ResourceConnection.RscConnFlags.fromStringList(rscConnProto.getRscConnFlagsList());
    }

    @Override
    public Integer getPort()
    {
        return rscConnProto.hasPort() ? rscConnProto.getPort() : null;
    }

    public static RscConnOuterClass.RscConn toProto(
        final ResourceConnection.RscConnApi apiResourceConn
    )
    {
        RscConnOuterClass.RscConn.Builder rscConnBld = RscConnOuterClass.RscConn.newBuilder();

        rscConnBld.setNodeName1(apiResourceConn.getSourceNodeName());
        rscConnBld.setNodeName2(apiResourceConn.getTargetNodeName());
        rscConnBld.setRscName(apiResourceConn.getResourceName());
        rscConnBld.putAllRscConnProps(apiResourceConn.getProps());
        rscConnBld.setRscConnUuid(apiResourceConn.getUuid().toString());
        rscConnBld.addAllRscConnFlags(ResourceConnection.RscConnFlags.toStringList(apiResourceConn.getFlags()));
        if (apiResourceConn.getPort() != null)
        {
            rscConnBld.setPort(apiResourceConn.getPort());
        }

        return rscConnBld.build();
    }
}
