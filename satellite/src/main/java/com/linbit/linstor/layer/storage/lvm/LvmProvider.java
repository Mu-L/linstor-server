package com.linbit.linstor.layer.storage.lvm;

import com.linbit.ImplementationError;
import com.linbit.linstor.PriorityProps;
import com.linbit.linstor.annotation.Nullable;
import com.linbit.linstor.api.ApiConsts;
import com.linbit.linstor.api.SpaceInfo;
import com.linbit.linstor.core.devmgr.StltReadOnlyInfo.ReadOnlyVlmProviderInfo;
import com.linbit.linstor.core.identifier.ResourceName;
import com.linbit.linstor.core.identifier.StorPoolName;
import com.linbit.linstor.core.identifier.VolumeNumber;
import com.linbit.linstor.core.objects.Resource;
import com.linbit.linstor.core.objects.ResourceDefinition;
import com.linbit.linstor.core.objects.ResourceGroup;
import com.linbit.linstor.core.objects.Snapshot;
import com.linbit.linstor.core.objects.SnapshotDefinition;
import com.linbit.linstor.core.objects.SnapshotVolume;
import com.linbit.linstor.core.objects.SnapshotVolumeDefinition;
import com.linbit.linstor.core.objects.StorPool;
import com.linbit.linstor.core.objects.Volume;
import com.linbit.linstor.core.objects.VolumeDefinition;
import com.linbit.linstor.core.pojos.LocalPropsChangePojo;
import com.linbit.linstor.dbdrivers.DatabaseException;
import com.linbit.linstor.interfaces.StorPoolInfo;
import com.linbit.linstor.layer.DeviceLayerUtils;
import com.linbit.linstor.layer.storage.AbsStorageProvider;
import com.linbit.linstor.layer.storage.StorageLayerSizeCalculator;
import com.linbit.linstor.layer.storage.lvm.utils.LvmCommands;
import com.linbit.linstor.layer.storage.lvm.utils.LvmCommands.LvmVolumeType;
import com.linbit.linstor.layer.storage.lvm.utils.LvmUtils;
import com.linbit.linstor.layer.storage.lvm.utils.LvmUtils.LvsInfo;
import com.linbit.linstor.layer.storage.lvm.utils.LvmUtils.VgsInfo;
import com.linbit.linstor.layer.storage.utils.LsBlkUtils;
import com.linbit.linstor.layer.storage.utils.PmemUtils;
import com.linbit.linstor.layer.storage.utils.StorageConfigReader;
import com.linbit.linstor.propscon.InvalidKeyException;
import com.linbit.linstor.propscon.InvalidValueException;
import com.linbit.linstor.propscon.Props;
import com.linbit.linstor.propscon.ReadOnlyProps;
import com.linbit.linstor.security.AccessDeniedException;
import com.linbit.linstor.storage.StorageConstants;
import com.linbit.linstor.storage.StorageException;
import com.linbit.linstor.storage.data.provider.AbsStorageVlmData;
import com.linbit.linstor.storage.data.provider.StorageRscData;
import com.linbit.linstor.storage.data.provider.lvm.LvmData;
import com.linbit.linstor.storage.interfaces.categories.resource.VlmProviderObject;
import com.linbit.linstor.storage.interfaces.categories.resource.VlmProviderObject.Size;
import com.linbit.linstor.storage.kinds.DeviceProviderKind;
import com.linbit.linstor.storage.utils.MkfsUtils;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class LvmProvider extends AbsStorageProvider<LvsInfo, LvmData<Resource>, LvmData<Snapshot>>
{
    private static final int TOLERANCE_FACTOR = 3;
    // FIXME: FORMAT should be private, only made public for LayeredSnapshotHelper
    public static final String FORMAT_RSC_TO_LVM_ID = "%s%s_%05d";
    public static final String FORMAT_SNAP_TO_LVM_ID = FORMAT_RSC_TO_LVM_ID + "_%s";
    private static final String FORMAT_LVM_ID_WIPE_IN_PROGRESS = "%s-linstor_wiping_in_progress-%d";
    private static final String FORMAT_DEV_PATH = "/dev/%s/%s";

    private static final String DFLT_LVCREATE_TYPE = "linear";

    private static final AtomicLong DELETED_ID = new AtomicLong(0);

    protected LvmProvider(
        AbsStorageProviderInit superInitRef,
        String subTypeDescr,
        DeviceProviderKind subTypeKind
    )
    {
        super(superInitRef, subTypeDescr, subTypeKind);
    }

    @Inject
    public LvmProvider(AbsStorageProviderInit superInitRef)
    {
        super(superInitRef, "LVM", DeviceProviderKind.LVM);
    }

    @Override
    public DeviceProviderKind getDeviceProviderKind()
    {
        return DeviceProviderKind.LVM;
    }

    @Override
    protected void updateStates(List<LvmData<Resource>> vlmDataList, List<LvmData<Snapshot>> snapVlmDataList)
        throws StorageException, AccessDeniedException, DatabaseException
    {
        final Map<String, VgsInfo> extentSizes = LvmUtils.getVgsInfo(
            extCmdFactory,
            getAffectedVolumeGroups(vlmDataList, snapVlmDataList),
            false
        );

        List<LvmData<?>> combinedList = new ArrayList<>();
        combinedList.addAll(vlmDataList);
        combinedList.addAll(snapVlmDataList);

        for (LvmData<?> vlmData : combinedList)
        {
            final LvsInfo info = infoListCache.get(getFullQualifiedIdentifier(vlmData));
            updateInfo(vlmData, info);

            // final VlmStorageState<T> vlmState = vlmStorStateFactory.create((T) info, vlm);

            if (info != null)
            {
                final long expectedSize = vlmData.getExpectedSize();
                final long actualSize = info.size;
                if (actualSize != expectedSize)
                {
                    if (actualSize < expectedSize)
                    {
                        vlmData.setSizeState(Size.TOO_SMALL);
                    }
                    else
                    {
                        Size sizeState = Size.TOO_LARGE;

                        final long toleratedSize =
                            expectedSize + extentSizes.get(info.volumeGroup).vgExtentSize * TOLERANCE_FACTOR;
                        if (actualSize < toleratedSize)
                        {
                            sizeState = Size.TOO_LARGE_WITHIN_TOLERANCE;
                        }
                        vlmData.setSizeState(sizeState);
                    }
                }
                else
                {
                    vlmData.setSizeState(Size.AS_EXPECTED);
                }
            }
        }
    }

    protected String getFullQualifiedIdentifier(LvmData<?> vlmDataRef)
    {
        return vlmDataRef.getVolumeGroup() +
            File.separator +
            asIdentifierRaw(vlmDataRef);
    }

    @SuppressWarnings("unchecked")
    protected String asIdentifierRaw(LvmData<?> vlmData)
    {
        String identifier;
        if (vlmData.getVolume() instanceof Volume)
        {
            identifier = asLvIdentifier((LvmData<Resource>) vlmData);
        }
        else
        {
            identifier = asSnapLvIdentifier((LvmData<Snapshot>) vlmData);
        }
        return identifier;
    }

    /*
     * Expected to be overridden (extended) by LvmThinProvider
     */
    @SuppressWarnings({ "unchecked" })
    protected void updateInfo(LvmData<?> vlmDataRef, @Nullable LvsInfo info)
        throws DatabaseException, AccessDeniedException, StorageException
    {
        boolean setDevicePath;
        String lvcreateOptions;
        if (vlmDataRef.getVolume() instanceof Volume)
        {
            LvmData<Resource> vlmData = (LvmData<Resource>) vlmDataRef;
            vlmDataRef.setIdentifier(asLvIdentifier(vlmData));
            setDevicePath = !vlmData.getVolume().getAbsResource().getStateFlags()
                .isSet(storDriverAccCtx, Resource.Flags.INACTIVE);
            /*
             * while the volume is cloning, we do not want to set the device path so that other tools do not try to
             * access it
             */
            setDevicePath &= !isCloning(vlmData);

            lvcreateOptions = getLvcreateOptions(vlmData);
        }
        else
        {
            vlmDataRef.setIdentifier(asSnapLvIdentifier((LvmData<Snapshot>) vlmDataRef));
            setDevicePath = true; // TODO: not sure about this default...

            lvcreateOptions = getLvcreateSnapshotOptions(vlmDataRef);
        }

        if (info == null)
        {
            vlmDataRef.setExists(false);
            vlmDataRef.setVolumeGroup(extractVolumeGroup(vlmDataRef));
            vlmDataRef.setDevicePath(null);
            vlmDataRef.setAllocatedSize(-1);
            // vlmData.setUsableSize(-1);
            vlmDataRef.setAttributes(null);

            List<String> additionalOptions = MkfsUtils.shellSplit(lvcreateOptions);
            String[] additionalOptionsArr = new String[additionalOptions.size()];
            additionalOptions.toArray(additionalOptionsArr);
            updateStripesPropIfNeeded(vlmDataRef, findStripesInAdditionalArgs(additionalOptionsArr));
        }
        else
        {
            vlmDataRef.setExists(true);
            vlmDataRef.setVolumeGroup(info.volumeGroup);
            if (setDevicePath)
            {
                vlmDataRef.setDevicePath(info.path);
            }
            else
            {
                vlmDataRef.setDevicePath(null);
            }
            vlmDataRef.setIdentifier(info.identifier);
            vlmDataRef.setAllocatedSize(info.size);
            vlmDataRef.setUsableSize(info.size);
            vlmDataRef.setAttributes(info.attributes);

            if (!info.attributes.contains("a") && setDevicePath)
            {
                LvmUtils.execWithRetry(
                    extCmdFactory,
                    Collections.singleton(vlmDataRef.getVolumeGroup()),
                    config -> LvmCommands.activateVolume(
                        extCmdFactory.create(),
                        vlmDataRef.getVolumeGroup(),
                        vlmDataRef.getIdentifier(),
                        config
                    )
                );
                LvmUtils.recacheNextLvs();
            }
            // deactivating a volume MUST NOT happen within the prepare step
            // as other layers might still hold the device open

            updateStripesPropIfNeeded(vlmDataRef, info.stripes);
        }
    }

    private void updateStripesPropIfNeeded(LvmData<?> vlmDataRef, @Nullable Integer stripesRef)
        throws AccessDeniedException, DatabaseException
    {
        if (stripesRef != null && stripesRef > 1)
        {
            // only update if non-default
            Props props = StorageLayerSizeCalculator.getProps(storDriverAccCtx, vlmDataRef);
            String propKey = StorageLayerSizeCalculator.getStripesPropKey(vlmDataRef);
            @Nullable String propValue = props.getProp(propKey);
            String currentStripesStr = Integer.toString(stripesRef);
            if (propValue == null || !propValue.equals(currentStripesStr))
            {
                try
                {
                    props.setProp(propKey, currentStripesStr);
                }
                catch (InvalidKeyException | InvalidValueException exc)
                {
                    throw new ImplementationError(exc);
                }
            }
        }
    }

    protected String extractVolumeGroup(LvmData<?> vlmData)
    {
        return getVolumeGroup(vlmData.getStorPool());
    }

    @Override
    protected void createLvImpl(LvmData<Resource> vlmData)
        throws StorageException, AccessDeniedException, DatabaseException
    {
        List<String> additionalOptions = MkfsUtils.shellSplit(getLvcreateOptions(vlmData));
        String[] additionalOptionsArr = new String[additionalOptions.size()];
        additionalOptions.toArray(additionalOptionsArr);

        if (additionalOptions.contains("--config"))
        {
            // no retry, use only users '--config' settings
            LvmCommands.createFat(
                extCmdFactory.create(),
                vlmData.getVolumeGroup(),
                asLvIdentifier(vlmData),
                vlmData.getExpectedSize(),
                null, // config is contained in additionalOptions
                additionalOptionsArr
            );
        }
        else
        {
            LvmUtils.execWithRetry(
                extCmdFactory,
                Collections.singleton(vlmData.getVolumeGroup()),
                config -> LvmCommands.createFat(
                    extCmdFactory.create(),
                    vlmData.getVolumeGroup(),
                    asLvIdentifier(vlmData),
                    vlmData.getExpectedSize(),
                    config,
                    additionalOptionsArr
                )
            );
        }
        LvmUtils.recacheNext();

        updateStripesPropIfNeeded(vlmData, findStripesInAdditionalArgs(additionalOptionsArr));
    }

    protected String getLvCreateType(LvmData<Resource> vlmDataRef)
    {
        String type;
        try
        {
            type = getPrioProps(vlmDataRef)
                .getProp(
                    ApiConsts.KEY_STOR_POOL_LVCREATE_TYPE,
                    ApiConsts.NAMESPC_STORAGE_DRIVER,
                    DFLT_LVCREATE_TYPE
                );
        }
        catch (AccessDeniedException | InvalidKeyException exc)
        {
            throw new ImplementationError(exc);
        }
        return type;
    }

    @SuppressWarnings("unchecked")
    protected PriorityProps getPrioProps(LvmData<?> vlmDataRef) throws AccessDeniedException
    {
        return vlmDataRef.getRscLayerObject().getAbsResource() instanceof Resource ?
            getPrioPropsRsc((LvmData<Resource>) vlmDataRef) :
            getPrioPropsSnap((LvmData<Snapshot>) vlmDataRef);
    }

    protected PriorityProps getPrioPropsRsc(LvmData<Resource> vlmDataRef) throws AccessDeniedException
    {
        Volume vlm = (Volume) vlmDataRef.getVolume();
        Resource rsc = vlm.getAbsResource();
        ResourceDefinition rscDfn = vlm.getResourceDefinition();
        ResourceGroup rscGrp = rscDfn.getResourceGroup();
        VolumeDefinition vlmDfn = vlm.getVolumeDefinition();
        return new PriorityProps(
            vlm.getProps(storDriverAccCtx),
            rsc.getProps(storDriverAccCtx),
            vlmDataRef.getStorPool().getProps(storDriverAccCtx),
            rsc.getNode().getProps(storDriverAccCtx),
            vlmDfn.getProps(storDriverAccCtx),
            rscDfn.getProps(storDriverAccCtx),
            rscGrp.getVolumeGroupProps(storDriverAccCtx, vlmDfn.getVolumeNumber()),
            rscGrp.getProps(storDriverAccCtx),
            stltConfigAccessor.getReadonlyProps()
        );
    }

    protected PriorityProps getPrioPropsSnap(LvmData<Snapshot> vlmDataRef) throws AccessDeniedException
    {
        SnapshotVolume snapVlm = (SnapshotVolume) vlmDataRef.getVolume();
        Snapshot snap = snapVlm.getAbsResource();
        ResourceDefinition rscDfn = snapVlm.getResourceDefinition();
        ResourceGroup rscGrp = rscDfn.getResourceGroup();
        SnapshotVolumeDefinition snapVlmDfn = snapVlm.getSnapshotVolumeDefinition();
        SnapshotDefinition snapDfn = snap.getSnapshotDefinition();
        return new PriorityProps(
            snapVlm.getSnapVlmProps(storDriverAccCtx),
            snapVlm.getVlmProps(storDriverAccCtx),
            snap.getSnapProps(storDriverAccCtx),
            snap.getRscProps(storDriverAccCtx),
            vlmDataRef.getStorPool().getProps(storDriverAccCtx),
            snap.getNode().getProps(storDriverAccCtx),
            snapVlmDfn.getSnapVlmDfnProps(storDriverAccCtx),
            snapVlmDfn.getVlmDfnProps(storDriverAccCtx),
            snapDfn.getSnapDfnProps(storDriverAccCtx),
            snapDfn.getRscDfnProps(storDriverAccCtx),
            // we have to skip vlmDfn (not snapVlmDfn) since vlmDfn might have been removed in the meantime
            // we can still include rscDfn, since a rscDfn cannot be removed while it has snapshots
            rscDfn.getProps(storDriverAccCtx),
            rscGrp.getVolumeGroupProps(storDriverAccCtx, snapVlmDfn.getVolumeNumber()),
            rscGrp.getProps(storDriverAccCtx),
            stltConfigAccessor.getReadonlyProps()
        );
    }

    protected String getLvcreateOptions(LvmData<Resource> vlmDataRef)
    {
        return getProp(
            vlmDataRef,
            ApiConsts.NAMESPC_STORAGE_DRIVER,
            ApiConsts.KEY_STOR_POOL_LVCREATE_OPTIONS,
            ""
        );
    }

    protected String getLvcreateSnapshotOptions(LvmData<?> vlmDataRef)
    {
        return getProp(
            vlmDataRef,
            ApiConsts.NAMESPC_STORAGE_DRIVER,
            ApiConsts.KEY_STOR_POOL_LVCREATE_SNAPSHOT_OPTIONS,
            ""
        );
    }

    protected String getProp(LvmData<?> vlmDataRef, String namespace, String key, String dfltValue)
    {
        String options;
        try
        {
            options = getPrioProps(vlmDataRef).getProp(key, namespace, dfltValue);
        }
        catch (AccessDeniedException | InvalidKeyException exc)
        {
            throw new ImplementationError(exc);
        }
        return options;
    }

    @Override
    protected void resizeLvImpl(LvmData<Resource> vlmData)
        throws StorageException, AccessDeniedException
    {
        LvmUtils.execWithRetry(
            extCmdFactory,
            Collections.singleton(vlmData.getVolumeGroup()),
            config -> LvmCommands.resize(
                extCmdFactory.create(),
                vlmData.getVolumeGroup(),
                asLvIdentifier(vlmData),
                vlmData.getExpectedSize(),
                config
            )
        );
        LvmUtils.recacheNext();
    }

    @Override
    protected void deleteLvImpl(LvmData<Resource> vlmData, String oldLvmId)
        throws StorageException, DatabaseException, AccessDeniedException
    {
        String devicePath = vlmData.getDevicePath();
        String volumeGroup = vlmData.getVolumeGroup();

        if (true)
        {
            wipeHandler.quickWipe(devicePath);
            LvmUtils.execWithRetry(
                extCmdFactory,
                Collections.singleton(vlmData.getVolumeGroup()),
                config -> LvmCommands.delete(
                    extCmdFactory.create(),
                    volumeGroup,
                    oldLvmId,
                    config,
                    LvmVolumeType.VOLUME
                )
            );
            vlmData.setExists(false);
            LvmUtils.recacheNext();
        }
        else
        {
            // TODO use this path once async wiping is implemented

            // devicePath is the "current" devicePath. as we will rename it right now
            // we will have to adjust the devicePath
            int lastIndexOf = devicePath.lastIndexOf(oldLvmId);

            // just make sure to not colide with any other ongoing wipe-lv-name
            String newLvmId = String.format(
                FORMAT_LVM_ID_WIPE_IN_PROGRESS,
                asLvIdentifier(vlmData),
                DELETED_ID.incrementAndGet()
            );
            devicePath = devicePath.substring(0, lastIndexOf) + newLvmId;

            LvmUtils.execWithRetry(
                extCmdFactory,
                Collections.singleton(vlmData.getVolumeGroup()),
                config -> LvmCommands.rename(
                    extCmdFactory.create(),
                    volumeGroup,
                    oldLvmId,
                    newLvmId,
                    config
                )
            );
            LvmUtils.recacheNext();

            vlmData.setExists(false);

            wipeHandler.asyncWipe(
                devicePath,
                ignored ->
                {
                    LvmUtils.execWithRetry(
                        extCmdFactory,
                        Collections.singleton(vlmData.getVolumeGroup()),
                        config -> LvmCommands.delete(
                            extCmdFactory.create(),
                            volumeGroup,
                            newLvmId,
                            config,
                            LvmVolumeType.VOLUME
                        )
                    );
                    LvmUtils.recacheNext();
                }
            );
        }
    }

    @Override
    protected void deactivateLvImpl(LvmData<Resource> vlmDataRef, String lvIdRef)
        throws StorageException, AccessDeniedException, DatabaseException
    {
        LvmUtils.execWithRetry(
            extCmdFactory,
            Collections.singleton(vlmDataRef.getVolumeGroup()),
            config -> LvmCommands.deactivateVolume(
                extCmdFactory.create(),
                vlmDataRef.getVolumeGroup(),
                vlmDataRef.getIdentifier(),
                config
            )
        );
        LvmUtils.recacheNextLvs();
    }

    @Override
    protected Map<String, Long> getFreeSpacesImpl() throws StorageException
    {
        Map<String, Long> ret = new HashMap<>();
        Map<String, VgsInfo> vgsInfoMap = LvmUtils.getVgsInfo(
            extCmdFactory,
            changedStoragePoolStrings,
            false
        );
        for (String storPool : changedStoragePoolStrings)
        {
            @Nullable VgsInfo vgsInfo = vgsInfoMap.get(storPool);
            if (vgsInfo == null)
            {
                ret.put(storPool, SIZE_OF_NOT_FOUND_STOR_POOL);
            }
            else
            {
                ret.put(storPool, vgsInfo.vgFree);
            }
        }
        return ret;
    }

    @Override
    protected Map<String, LvsInfo> getInfoListImpl(
        List<LvmData<Resource>> vlmDataList,
        List<LvmData<Snapshot>> snapVlms
    )
        throws StorageException, AccessDeniedException
    {
        if (hasSharedVolumeGroups(vlmDataList, snapVlms))
        {
            LvmCommands.vgscan(extCmdFactory.create(), true);
        }
        Map<String, Map<String, LvsInfo>> lvsInfoMap = LvmUtils.getLvsInfo(
            extCmdFactory,
            getAffectedVolumeGroups(vlmDataList, snapVlms)
        );
        HashMap<String /* fullQualfiedIdentifier */, LvsInfo> ret = new HashMap<>();
        for (Map<String, LvsInfo> lvMap : lvsInfoMap.values())
        {
            for (LvsInfo lvInfo : lvMap.values())
            {
                ret.put(lvInfo.volumeGroup + File.separator + lvInfo.identifier, lvInfo);
            }
        }
        return ret;
    }

    @Override
    public String getDevicePath(String storageName, String lvId)
    {
        return String.format(FORMAT_DEV_PATH, storageName, lvId);
    }

    @Override
    protected String asLvIdentifier(
        @Nullable StorPoolName ignoredSpName,
        ResourceName resourceName,
        String rscNameSuffix,
        VolumeNumber volumeNumber
    )
    {
        return String.format(
            FORMAT_RSC_TO_LVM_ID,
            resourceName.displayValue,
            rscNameSuffix,
            volumeNumber.value
        );
    }

    @Override
    protected String asSnapLvIdentifier(LvmData<Snapshot> snapVlmDataRef)
    {
        StorageRscData<Snapshot> snapData = snapVlmDataRef.getRscLayerObject();
        return asSnapLvIdentifierRaw(
            snapData.getResourceName().displayValue,
            snapData.getResourceNameSuffix(),
            snapVlmDataRef.getVlmNr().value,
            snapData.getAbsResource().getSnapshotName().displayValue
        );
    }

    protected String asSnapLvIdentifierRaw(String rscNameRef, String rscNameSuffixRef, int vlmNrRef, String snapNameRef)
    {
        return String.format(
            FORMAT_SNAP_TO_LVM_ID,
            rscNameRef,
            rscNameSuffixRef,
            vlmNrRef,
            snapNameRef
        );
    }

    @Override
    protected String getStorageName(StorPool storPoolRef)
    {
        return getVolumeGroup(storPoolRef);
    }

    protected @Nullable String getVolumeGroup(StorPoolInfo storPool)
    {
        String volumeGroup;
        try
        {
            volumeGroup = DeviceLayerUtils.getNamespaceStorDriver(storPool.getReadOnlyProps(storDriverAccCtx))
                .getProp(StorageConstants.CONFIG_LVM_VOLUME_GROUP_KEY).split("/")[0];
        }
        catch (InvalidKeyException | AccessDeniedException exc)
        {
            throw new ImplementationError(exc);
        }
        return volumeGroup;
    }

    @Override
    protected boolean updateDmStats()
    {
        return true; // LVM driver should call dmstats commands
    }

    @Override
    public SpaceInfo getSpaceInfo(StorPoolInfo storPool) throws StorageException, AccessDeniedException
    {
        String vg = getVolumeGroup(storPool);
        if (vg == null)
        {
            throw new StorageException("Unset volume group for " + storPool);
        }
        @Nullable VgsInfo vgsInfo = LvmUtils.getVgsInfo(
            extCmdFactory,
            Collections.singleton(vg),
            false
        ).get(vg);

        @Nullable Long capacity = null;
        @Nullable Long freespace = null;
        if (vgsInfo != null)
        {
            capacity = vgsInfo.vgSize;
            freespace = vgsInfo.vgFree;
        }
        return SpaceInfo.buildOrThrowOnError(capacity, freespace, storPool);
    }

    /*
     * Expected to be overridden by LvmThinProvider (maybe additionally called)
     */
    @Override
    public @Nullable LocalPropsChangePojo checkConfig(StorPoolInfo storPool)
        throws StorageException, AccessDeniedException
    {
        ReadOnlyProps publicStorDriverNamespace = DeviceLayerUtils.getNamespaceStorDriver(
            storPool.getReadOnlyProps(storDriverAccCtx)
        );
        StorageConfigReader.checkVolumeGroupEntry(extCmdFactory, publicStorDriverNamespace);
        StorageConfigReader.checkToleranceFactor(publicStorDriverNamespace);

        return null;
    }

    protected void checkExtentSize(StorPoolInfo storPool, LocalPropsChangePojo ret)
        throws StorageException, ImplementationError
    {
        String lvmVG = getVolumeGroup(storPool);
        Map<String, VgsInfo> extentSizeInKibMap = LvmUtils.getVgsInfo(
            extCmdFactory,
            Collections.singleton(lvmVG),
            false
        );
        @Nullable VgsInfo vgsInfo = extentSizeInKibMap.get(lvmVG);
        if (vgsInfo != null)
        {
            markAllocGranAsChangedIfNeeded(vgsInfo.vgExtentSize, storPool, ret);
        }
    }

    @Override
    public @Nullable LocalPropsChangePojo update(StorPool storPoolRef)
        throws AccessDeniedException, DatabaseException, StorageException
    {
        LocalPropsChangePojo ret = new LocalPropsChangePojo();
        List<String> pvs = LvmUtils.getPhysicalVolumes(extCmdFactory, getVolumeGroup(storPoolRef));
        if (PmemUtils.supportsDax(extCmdFactory.create(), pvs))
        {
            storPoolRef.setPmem(true);
        }
        storPoolRef.setVDO(LsBlkUtils.parentIsVDO(extCmdFactory.create(), pvs));
        checkExtentSize(storPoolRef, ret);

        return ret;
    }

    private Set<String> getAffectedVolumeGroups(
        Collection<LvmData<Resource>> vlmDataList,
        Collection<LvmData<Snapshot>> snapVlms
    )
    {
        ArrayList<LvmData<?>> combinedList = new ArrayList<>();
        combinedList.addAll(vlmDataList);
        combinedList.addAll(snapVlms);

        Set<String> volumeGroups = new HashSet<>();
        for (LvmData<?> vlmData : combinedList)
        {
            String volumeGroup = vlmData.getVolumeGroup();
            if (volumeGroup == null)
            {
                volumeGroup = getVolumeGroup(vlmData.getStorPool());
                vlmData.setVolumeGroup(volumeGroup);
            }
            if (volumeGroup != null)
            {
                volumeGroups.add(volumeGroup);
            }
        }
        return volumeGroups;
    }

    private boolean hasSharedVolumeGroups(
        Collection<LvmData<Resource>> vlmDataList,
        Collection<LvmData<Snapshot>> snapVlms
    )
    {
        boolean ret = false;
        ArrayList<LvmData<?>> combinedList = new ArrayList<>();
        combinedList.addAll(vlmDataList);
        combinedList.addAll(snapVlms);

        for (LvmData<?> vlmData : combinedList)
        {
            StorPool storPool = vlmData.getStorPool();
            if (storPool.getSharedStorPoolName().isShared())
            {
                ret = true;
                break;
            }
        }
        return ret;
    }

    @Override
    protected boolean waitForSnapshotDevice()
    {
        return true;
    }

    @Override
    protected void setDevicePath(LvmData<Resource> vlmData, String devPath) throws DatabaseException
    {
        vlmData.setDevicePath(devPath);
    }

    @Override
    protected void createSnapshotForCloneImpl(
        LvmData<Resource> vlmData,
        String cloneRscName)
        throws StorageException, AccessDeniedException, DatabaseException
    {
        final String srcId = asLvIdentifier(vlmData);
        final String srcFullSnapshotName = getCloneSnapshotNameFull(vlmData, cloneRscName, "_");

        if (!infoListCache.containsKey(vlmData.getVolumeGroup() + "/" + srcFullSnapshotName))
        {
            LvmUtils.execWithRetry(
                extCmdFactory,
                Collections.singleton(vlmData.getVolumeGroup()),
                config -> LvmCommands.createSnapshot(
                    extCmdFactory.create(),
                    vlmData.getVolumeGroup(),
                    srcId,
                    srcFullSnapshotName,
                    config,
                    vlmData.getAllocatedSize()
                )
            );

            LvmUtils.execWithRetry(
                extCmdFactory,
                Collections.singleton(vlmData.getVolumeGroup()),
                config -> LvmCommands.addTag(
                    extCmdFactory.create(),
                    vlmData.getVolumeGroup(),
                    srcFullSnapshotName,
                    LvmCommands.LVM_TAG_CLONE_SNAPSHOT,
                    config
                )
            );
            LvmUtils.recacheNextLvs();
        }
        else
        {
            errorReporter.logInfo("Clone base snapshot %s already found, reusing.", srcFullSnapshotName);
        }
    }

    @Override
    protected void setAllocatedSize(LvmData<Resource> vlmData, long size) throws DatabaseException
    {
        vlmData.setAllocatedSize(size);
    }

    @Override
    protected void setUsableSize(LvmData<Resource> vlmData, long size) throws DatabaseException
    {
        vlmData.setUsableSize(size);
    }

    @Override
    protected void setExpectedUsableSize(LvmData<Resource> vlmData, long size)
    {
        vlmData.setExpectedSize(size);
    }

    @Override
    protected String getStorageName(LvmData<Resource> vlmDataRef) throws DatabaseException
    {
        return vlmDataRef.getVolumeGroup();
    }

    @Override
    protected long getExtentSize(AbsStorageVlmData<?> vlmDataRef) throws StorageException
    {
        String vlmGrp = getVolumeGroup(vlmDataRef.getStorPool());
        Map<String, VgsInfo> vgs = LvmUtils.getVgsInfo(
            extCmdFactory,
            Collections.singleton(vlmGrp),
            false
        );
        @Nullable VgsInfo vgsInfo = vgs.get(vlmGrp);
        long extentSize;
        if (vgsInfo != null)
        {
            extentSize = vgsInfo.vgExtentSize;
        }
        else
        {
            throw new StorageException("VolumeGroup " + vlmGrp + " not found");
        }
        return extentSize;
    }

    @Override
    protected int getExtentSizeMulFactor(VlmProviderObject<?> vlmDataRef)
    {
        return getStripeCount(vlmDataRef);
    }

    private int getStripeCount(VlmProviderObject<?> vlmDataRef)
    {
        @Nullable Integer stripeCount = null;
        @SuppressWarnings("unchecked")
        LvmData<Resource> lvmData = (LvmData<Resource>) vlmDataRef;
        if (vlmDataRef.exists())
        {
            @Nullable LvsInfo lvsInfo = infoListCache.get(getFullQualifiedIdentifier(lvmData));
            if (lvsInfo != null)
            {
                stripeCount = lvsInfo.stripes;
            }
        }
        if (stripeCount == null)
        {
            List<String> additionalOptions = MkfsUtils.shellSplit(getLvcreateOptions(lvmData));
            String[] additionalOptionsArr = new String[additionalOptions.size()];
            additionalOptions.toArray(additionalOptionsArr);
            stripeCount = findStripesInAdditionalArgs(additionalOptionsArr);
        }
        if (stripeCount == null)
        {
            stripeCount = DFLT_STRIPES;
        }
        return stripeCount;
    }

    private @Nullable Integer findStripesInAdditionalArgs(String[] additionalOptionsArr)
    {
        @Nullable Integer stripeCount = null;
        try
        {
            for (int idx = 0; idx < additionalOptionsArr.length; idx++)
            {
                String arg = additionalOptionsArr[idx];
                if (arg.equals("-i") || arg.equals("--stripes"))
                {
                    stripeCount = Integer.parseInt(additionalOptionsArr[idx + 1]);
                    break;
                }
            }
        }
        catch (NumberFormatException nfe)
        {
            errorReporter.reportError(nfe);
        }
        return stripeCount;
    }

    @Override
    public Map<ReadOnlyVlmProviderInfo, Long> fetchAllocatedSizes(List<ReadOnlyVlmProviderInfo> vlmDataListRef)
        throws StorageException, AccessDeniedException
    {
        return fetchOrigAllocatedSizes(vlmDataListRef);
    }

    @Override
    public void openForClone(VlmProviderObject<?> vlm, @Nullable String cloneName, boolean readOnly)
        throws StorageException, AccessDeniedException, DatabaseException
    {
        LvmData<Resource> srcData = (LvmData<Resource>) vlm;
        if (cloneName != null)
        {
            // use snapshot path
            vlm.setCloneDevicePath(getDevicePath(
                srcData.getVolumeGroup(), getCloneSnapshotNameFull(srcData, cloneName, "_")));
        }
        else
        {
            createLvImpl(srcData);
            String devicePath = getDevicePath(srcData.getVolumeGroup(), asLvIdentifier(srcData));
            try
            {
                waitUntilDeviceCreated(srcData, devicePath);
            }
            catch (AccessDeniedException exc)
            {
                throw new StorageException("Unable to run openForClone::waitUntilDeviceCreated", exc);
            }
            vlm.setCloneDevicePath(devicePath);
        }
    }

    @Override
    public void closeForClone(VlmProviderObject<?> vlm, @Nullable String cloneName) throws StorageException
    {
        vlm.setCloneDevicePath(null);
    }
}
