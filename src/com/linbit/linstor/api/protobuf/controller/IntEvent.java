package com.linbit.linstor.api.protobuf.controller;

import com.linbit.linstor.api.ApiCall;
import com.linbit.linstor.api.ApiConsts;
import com.linbit.linstor.api.protobuf.ProtobufApiCall;
import com.linbit.linstor.event.EventProcessor;
import com.linbit.linstor.proto.MsgEventOuterClass;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;

@ProtobufApiCall(
    name = ApiConsts.API_EVENT,
    description = "Handles an event"
)
public class IntEvent implements ApiCall
{
    private final EventProcessor eventProcessor;

    @Inject
    public IntEvent(
        EventProcessor eventProcessorRef
    )
    {
        eventProcessor = eventProcessorRef;
    }

    @Override
    public void execute(InputStream msgDataIn)
        throws IOException
    {
        MsgEventOuterClass.MsgEvent msgEvent = MsgEventOuterClass.MsgEvent.parseDelimitedFrom(msgDataIn);

        eventProcessor.handleEvent(
            msgEvent.getEventAction(),
            msgEvent.getEventName(),
            msgEvent.hasNodeName() ? msgEvent.getNodeName() : null,
            msgEvent.hasResourceName() ? msgEvent.getResourceName() : null,
            msgEvent.hasVolumeNumber() ? msgEvent.getVolumeNumber() : null,
            msgDataIn
        );
    }
}
