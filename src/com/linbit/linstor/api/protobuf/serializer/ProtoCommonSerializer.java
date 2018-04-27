package com.linbit.linstor.api.protobuf.serializer;

import com.linbit.linstor.annotation.ApiContext;
import com.linbit.linstor.api.CommonSerializerBuilderImpl;
import com.linbit.linstor.api.interfaces.serializer.CommonSerializer;
import com.linbit.linstor.event.EventIdentifier;
import com.linbit.linstor.logging.ErrorReporter;
import com.linbit.linstor.proto.MsgEventOuterClass;
import com.linbit.linstor.proto.MsgHeaderOuterClass;
import com.linbit.linstor.proto.eventdata.EventRscStateOuterClass;
import com.linbit.linstor.proto.eventdata.EventVlmDiskStateOuterClass;
import com.linbit.linstor.security.AccessContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Singleton
public class ProtoCommonSerializer implements CommonSerializer, CommonSerializerBuilderImpl.CommonSerializerWriter
{
    protected final ErrorReporter errorReporter;
    protected final AccessContext serializerCtx;

    @Inject
    public ProtoCommonSerializer(
        final ErrorReporter errReporterRef,
        final @ApiContext AccessContext serializerCtxRef
    )
    {
        this.errorReporter = errReporterRef;
        this.serializerCtx = serializerCtxRef;
    }

    @Override
    public CommonSerializerBuilder builder()
    {
        return builder(null);
    }

    @Override
    public CommonSerializerBuilder builder(String apiCall)
    {
        return builder(apiCall, null);
    }

    @Override
    public CommonSerializerBuilder builder(String apiCall, Integer msgId)
    {
        return new CommonSerializerBuilderImpl(errorReporter, this, apiCall, msgId);
    }

    @Override
    public void writeHeader(String apiCall, Integer msgId, ByteArrayOutputStream baos) throws IOException
    {
        MsgHeaderOuterClass.MsgHeader.Builder builder = MsgHeaderOuterClass.MsgHeader.newBuilder()
            .setApiCall(apiCall);
        if (msgId != null)
        {
            builder
                .setMsgId(msgId);
        }
        builder
            .build()
            .writeDelimitedTo(baos);
    }

    @Override
    public void writeEvent(
        Integer watchId, EventIdentifier eventIdentifier, String eventStreamAction, ByteArrayOutputStream baos
    )
        throws IOException
    {
        MsgEventOuterClass.MsgEvent.Builder eventBuilder = MsgEventOuterClass.MsgEvent.newBuilder();

        eventBuilder
            .setWatchId(watchId)
            .setEventAction(eventStreamAction)
            .setEventName(eventIdentifier.getEventName());

        if (eventIdentifier.getResourceName() != null)
        {
            eventBuilder.setResourceName(eventIdentifier.getResourceName().displayValue);
        }

        if (eventIdentifier.getNodeName() != null)
        {
            eventBuilder.setNodeName(eventIdentifier.getNodeName().displayValue);
        }

        if (eventIdentifier.getVolumeNumber() != null)
        {
            eventBuilder.setVolumeNumber(eventIdentifier.getVolumeNumber().value);
        }

        eventBuilder.build().writeDelimitedTo(baos);
    }

    @Override
    public void writeVolumeDiskState(String diskState, ByteArrayOutputStream baos)
        throws IOException
    {
        EventVlmDiskStateOuterClass.EventVlmDiskState.newBuilder()
            .setDiskState(diskState)
            .build()
            .writeDelimitedTo(baos);
    }

    @Override
    public void writeResourceStateEvent(Boolean resourceReady, ByteArrayOutputStream baos)
        throws IOException
    {
        EventRscStateOuterClass.EventRscState.newBuilder()
            .setReady(resourceReady)
            .build()
            .writeDelimitedTo(baos);
    }
}
