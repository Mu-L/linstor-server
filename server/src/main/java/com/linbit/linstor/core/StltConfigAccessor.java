package com.linbit.linstor.core;

import com.linbit.ImplementationError;
import com.linbit.linstor.annotation.Nullable;
import com.linbit.linstor.api.ApiConsts;
import com.linbit.linstor.propscon.InvalidKeyException;
import com.linbit.linstor.propscon.Props;
import com.linbit.linstor.propscon.ReadOnlyProps;
import com.linbit.linstor.propscon.ReadOnlyPropsImpl;

import javax.inject.Inject;
import javax.inject.Named;

public class StltConfigAccessor
{
    private ReadOnlyProps stltProps;

    @Inject
    public StltConfigAccessor(
        @Named(LinStor.SATELLITE_PROPS) ReadOnlyProps stltPropsRef
    )
    {
        stltProps = stltPropsRef;
    }

    public boolean useDmStats()
    {
        String dmStatsStr = null;
        try
        {
            dmStatsStr = stltProps.getProp(ApiConsts.KEY_DMSTATS, ApiConsts.NAMESPC_STORAGE_DRIVER);
        }
        catch (InvalidKeyException exc)
        {
            throw new ImplementationError("Hardcoded invalid property keys", exc);
        }
        return dmStatsStr != null && Boolean.parseBoolean(dmStatsStr);
    }

    public String getNodeName()
    {
        String name = null;
        try
        {
            name = stltProps.getProp(LinStor.KEY_NODE_NAME);
        }
        catch (InvalidKeyException exc)
        {
            throw new ImplementationError("Hardcoded property key is invalid", exc);
        }
        return name;
    }

    public ReadOnlyProps getReadonlyProps()
    {
        return getReadonlyProps(null);
    }

    public ReadOnlyProps getReadonlyProps(@Nullable String namespace)
    {
        ReadOnlyProps roRet;
        @Nullable ReadOnlyProps ns = stltProps.getNamespace(namespace);
        if (ns != null)
        {
            if (ns instanceof Props)
            {
                roRet = new ReadOnlyPropsImpl((Props) ns);
            }
            else
            {
                roRet = ns;
            }
        }
        else
        {
            roRet = ReadOnlyPropsImpl.emptyRoProps();
        }
        return roRet;
    }
}
