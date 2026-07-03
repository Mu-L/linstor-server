package com.linbit.linstor.core.apicallhandler;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.linbit.ImplementationError;
import com.linbit.linstor.api.interfaces.serializer.CommonSerializer;
import com.linbit.linstor.api.interfaces.serializer.CtrlStltSerializer;
import com.linbit.linstor.api.protobuf.serializer.ProtoCommonSerializer;
import com.linbit.linstor.api.protobuf.serializer.ProtoCtrlStltSerializer;

import javax.inject.Singleton;

public class ApiCallHandlerModule extends AbstractModule
{
    @Override
    protected void configure()
    {
        bind(CommonSerializer.class).to(ProtoCommonSerializer.class);
        bind(CtrlStltSerializer.class).to(ProtoCtrlStltSerializer.class);
    }

    @Provides
    @Singleton
    public AccessContext apiCtx(AccessContext initCtx)
    {
        AccessContext apiCtx = initCtx.clone();
        apiCtx.getEffectivePrivs().enablePrivileges(
            Privilege.PRIV_OBJ_VIEW,
            Privilege.PRIV_OBJ_USE,
            Privilege.PRIV_OBJ_CHANGE,
            Privilege.PRIV_OBJ_CONTROL,
            Privilege.PRIV_MAC_OVRD
        );
        return apiCtx;
    }
}
