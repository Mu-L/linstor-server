package com.linbit.linstor.proto.apidata;

import com.linbit.linstor.api.interfaces.VlmLayerDataApi;
import com.linbit.linstor.core.objects.Volume;
import com.linbit.linstor.core.objects.Volume.VlmApi;
import com.linbit.linstor.proto.common.VlmOuterClass.Vlm;
import com.linbit.linstor.storage.kinds.DeviceProviderKind;
import com.linbit.utils.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class VlmApiData implements VlmApi
{
    private final Vlm vlm;

    public VlmApiData(Vlm vlmRef)
    {
        vlm = vlmRef;
    }

    @Override
    public UUID getVlmUuid()
    {
        return vlm.hasVlmUuid() ? UUID.fromString(vlm.getVlmUuid()) : null;
    }

    @Override
    public UUID getVlmDfnUuid()
    {
        return vlm.hasVlmDfnUuid() ? UUID.fromString(vlm.getVlmDfnUuid()) : null;
    }

    @Override
    public String getStorPoolName()
    {
        return null; // only needed for compat, not available on protobuf (as that is java-internal only)
    }

    @Override
    public String getDevicePath()
    {
        return vlm.getDevicePath();
    }

    @Override
    public int getVlmNr()
    {
        return vlm.getVlmNr();
    }

    @Override
    public long getFlags()
    {
        return Volume.VlmFlags.fromStringList(vlm.getVlmFlagsList());
    }

    @Override
    public DeviceProviderKind getStorPoolDeviceProviderKind()
    {
        return null; // only needed for compat, not available on protobuf (as that is java-internal only)
    }

    @Override
    public Map<String, String> getVlmProps()
    {
        return vlm.getVlmPropsMap();
    }

    @Override
    public Optional<Long> getAllocatedSize()
    {
        return vlm.hasAllocatedSize() ?
            Optional.of(vlm.getAllocatedSize()) :
            Optional.empty();
    }

    @Override
    public Optional<Long> getUsableSize()
    {
        return vlm.hasUsableSize() ?
            Optional.of(vlm.getUsableSize()) :
            Optional.empty();
    }

    @Override
    public List<Pair<String, VlmLayerDataApi>> getVlmLayerData()
    {
        return null; // only needed for compat, not available on protobuf (as that is java-internal only)
    }

    public static Vlm toVlmProto(final VlmApi vlmApi)
    {
        Vlm.Builder builder = Vlm.newBuilder();
        builder.setVlmUuid(vlmApi.getVlmUuid().toString());
        builder.setVlmDfnUuid(vlmApi.getVlmDfnUuid().toString());
        builder.setVlmNr(vlmApi.getVlmNr());
        if (vlmApi.getDevicePath() != null)
        {
            builder.setDevicePath(vlmApi.getDevicePath());
        }
        builder.addAllVlmFlags(Volume.VlmFlags.toStringList(vlmApi.getFlags()));
        builder.putAllVlmProps(vlmApi.getVlmProps());
        vlmApi.getAllocatedSize().ifPresent(builder::setAllocatedSize);

        return builder.build();
    }

    public static List<Vlm> toVlmProtoList(final List<? extends VlmApi> volumedefs)
    {
        ArrayList<Vlm> protoVlm = new ArrayList<>();
        for (VlmApi vlmapi : volumedefs)
        {
            protoVlm.add(VlmApiData.toVlmProto(vlmapi));
        }
        return protoVlm;
    }

    public static List<VlmApi> toApiList(final List<Vlm> volumedefs)
    {
        ArrayList<VlmApi> apiVlms = new ArrayList<>();
        for (Vlm vlm : volumedefs)
        {
            apiVlms.add(new VlmApiData(vlm));
        }
        return apiVlms;
    }
}
