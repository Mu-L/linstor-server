package com.linbit.linstor.api.pojo;

import com.linbit.linstor.annotation.Nullable;
import com.linbit.linstor.api.interfaces.RscLayerDataApi;
import com.linbit.linstor.api.interfaces.VlmLayerDataApi;
import com.linbit.linstor.layer.LayerIgnoreReason;
import com.linbit.linstor.storage.interfaces.categories.resource.VlmProviderObject;
import com.linbit.linstor.storage.kinds.DeviceLayerKind;
import com.linbit.linstor.storage.kinds.DeviceProviderKind;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class NvmeRscPojo implements RscLayerDataApi
{
    @JsonIgnore
    private final int id;
    private final List<RscLayerDataApi> children;
    private final String rscNameSuffix;
    private final List<NvmeVlmPojo> vlms;
    @JsonIgnore
    private final boolean suspend;
    @JsonIgnore
    private final Set<LayerIgnoreReason> ignoreReasons;

    public NvmeRscPojo(
        int idRef,
        List<RscLayerDataApi> childrenRef,
        String rscNameSuffixRef,
        List<NvmeVlmPojo> vlmsRef,
        boolean suspendRef,
        Set<LayerIgnoreReason> ignoreReasonsRef
    )
    {
        id = idRef;
        children = childrenRef;
        rscNameSuffix = rscNameSuffixRef;
        vlms = vlmsRef;
        suspend = suspendRef;
        ignoreReasons = Collections.unmodifiableSet(ignoreReasonsRef);
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public NvmeRscPojo(
        @JsonProperty("children") List<RscLayerDataApi> childrenRef,
        @JsonProperty("rscNameSuffix") String rscNameSuffixRef,
        @JsonProperty("volumeList") List<NvmeVlmPojo> vlmsRef
    )
    {
        id = BACK_DFLT_ID;
        children = childrenRef;
        rscNameSuffix = rscNameSuffixRef;
        vlms = vlmsRef;
        suspend = false;
        ignoreReasons = null;
    }

    @Override
    public int getId()
    {
        return id;
    }

    @Override
    public List<RscLayerDataApi> getChildren()
    {
        return children;
    }

    @Override
    public String getRscNameSuffix()
    {
        return rscNameSuffix;
    }

    @Override
    public DeviceLayerKind getLayerKind()
    {
        return DeviceLayerKind.NVME;
    }

    @Override
    public boolean getSuspend()
    {
        return suspend;
    }

    @Override
    public Set<LayerIgnoreReason> getIgnoreReasons()
    {
        return ignoreReasons;
    }

    @Override
    public List<NvmeVlmPojo> getVolumeList()
    {
        return vlms;
    }

    public static class NvmeVlmPojo implements VlmLayerDataApi
    {
        private final int vlmNr;
        @JsonIgnore
        private final @Nullable String devicePath;
        @JsonIgnore
        private final @Nullable String backingDisk;
        @JsonIgnore
        private final long allocatedSize;
        @JsonIgnore
        private final long usableSize;
        @JsonIgnore
        private final @Nullable String diskState;
        @JsonIgnore
        private final long discGran;
        @JsonIgnore
        private final boolean exists;

        public NvmeVlmPojo(
            int vlmNrRef,
            @Nullable String devicePathRef,
            @Nullable String backingDiskRef,
            long allocatedSizeRef,
            long usableSizeRef,
            @Nullable String diskStateRef,
            long discGranRef,
            boolean existsRef
        )
        {
            vlmNr = vlmNrRef;
            devicePath = devicePathRef;
            backingDisk = backingDiskRef;
            allocatedSize = allocatedSizeRef;
            usableSize = usableSizeRef;
            diskState = diskStateRef;
            discGran = discGranRef;
            exists = existsRef;
        }

        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public NvmeVlmPojo(
            @JsonProperty("vlmNr") int vlmNrRef
        )
        {
            vlmNr = vlmNrRef;
            devicePath = null;
            backingDisk = null;
            allocatedSize = VlmProviderObject.UNINITIALIZED_SIZE;
            usableSize = VlmProviderObject.UNINITIALIZED_SIZE;
            diskState = null;
            discGran = VlmProviderObject.UNINITIALIZED_SIZE;
            exists = false;
        }

        @Override
        public int getVlmNr()
        {
            return vlmNr;
        }

        @Override
        public DeviceLayerKind getLayerKind()
        {
            return DeviceLayerKind.NVME;
        }

        @Override
        public DeviceProviderKind getProviderKind()
        {
            return DeviceProviderKind.FAIL_BECAUSE_NOT_A_VLM_PROVIDER_BUT_A_VLM_LAYER;
        }

        @Override
        public String getDevicePath()
        {
            return devicePath;
        }

        public String getBackingDisk()
        {
            return backingDisk;
        }

        @Override
        public long getAllocatedSize()
        {
            return allocatedSize;
        }

        @Override
        public long getUsableSize()
        {
            return usableSize;
        }

        @Override
        public String getDiskState()
        {
            return diskState;
        }

        @Override
        public long getDiscGran()
        {
            return discGran;
        }

        @Override
        public boolean exists()
        {
            return exists;
        }
    }
}
