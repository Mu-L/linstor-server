package com.linbit.linstor.event.serializer.protobuf.common;

import com.linbit.linstor.InternalApiConsts;
import com.linbit.linstor.api.interfaces.serializer.CommonSerializer;
import com.linbit.linstor.event.LinstorEvent;
import com.linbit.linstor.event.WatchableObject;
import com.linbit.linstor.event.common.ReplicationStateEvent;
import com.linbit.linstor.event.serializer.EventSerializer;
import com.linbit.linstor.event.serializer.protobuf.ProtobufEventSerializer;
import com.linbit.linstor.layer.drbd.drbdstate.ReplState;
import com.linbit.utils.PairNonNull;

import javax.inject.Inject;
import javax.inject.Singleton;

@ProtobufEventSerializer(
    eventName = InternalApiConsts.EVENT_REPLICATION_STATE,
    objectType = WatchableObject.VOLUME
)
@Singleton
public class ReplicationStateEventSerializer
    implements EventSerializer, EventSerializer.Serializer<PairNonNull<String, ReplState>>
{
    private final CommonSerializer commonSerializer;
    private final ReplicationStateEvent replicationStateEvent;

    @Inject
    public ReplicationStateEventSerializer(
        CommonSerializer commonSerializerRef,
        ReplicationStateEvent replicationStateEventRef
    )
    {
        commonSerializer = commonSerializerRef;
        replicationStateEvent = replicationStateEventRef;
    }

    @Override
    public Serializer<PairNonNull<String, ReplState>> get()
    {
        return this;
    }

    @Override
    public byte[] writeEventValue(PairNonNull<String, ReplState> replStatePair)
    {
        return commonSerializer.headerlessBuilder()
            .replicationState(replStatePair.objA, replStatePair.objB.toString())
            .build();
    }

    @Override
    public LinstorEvent<PairNonNull<String, ReplState>> getEvent()
    {
        return replicationStateEvent.get();
    }
}
