package com.linbit.linstor.layer.storage.zfs;

import com.linbit.ImplementationError;
import com.linbit.linstor.annotation.Nullable;
import com.linbit.linstor.api.SpaceInfo;
import com.linbit.linstor.core.objects.Resource;
import com.linbit.linstor.core.pojos.LocalPropsChangePojo;
import com.linbit.linstor.dbdrivers.DatabaseException;
import com.linbit.linstor.interfaces.StorPoolInfo;
import com.linbit.linstor.layer.DeviceLayerUtils;
import com.linbit.linstor.layer.storage.zfs.utils.ZfsCommands;
import com.linbit.linstor.layer.storage.zfs.utils.ZfsUtils;
import com.linbit.linstor.layer.storage.zfs.utils.ZfsUtils.ZfsInfo;
import com.linbit.linstor.propscon.InvalidKeyException;
import com.linbit.linstor.security.AccessDeniedException;
import com.linbit.linstor.storage.StorageConstants;
import com.linbit.linstor.storage.StorageException;
import com.linbit.linstor.storage.data.provider.zfs.ZfsData;
import com.linbit.linstor.storage.kinds.DeviceProviderKind;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.Collections;
import java.util.HashMap;

@Singleton
public class ZfsThinProvider extends ZfsProvider
{
    @Inject
    public ZfsThinProvider(AbsStorageProviderInit superInitRef)
    {
        super(superInitRef, "ZFS-Thin", DeviceProviderKind.ZFS_THIN);
    }

    @Override
    public DeviceProviderKind getDeviceProviderKind()
    {
        return DeviceProviderKind.ZFS_THIN;
    }

    @Override
    protected void createLvImpl(ZfsData<Resource> vlmData)
        throws StorageException
    {
        ZfsCommands.create(
            extCmdFactory.create(),
            vlmData.getZPool(),
            asLvIdentifier(vlmData),
            vlmData.getExpectedSize(),
            true,
            getZfscreateOptions(vlmData)
        );
    }

    @Override
    protected @Nullable String getZPool(StorPoolInfo storPool)
    {
        String zPool;
        try
        {
            zPool = DeviceLayerUtils.getNamespaceStorDriver(storPool.getReadOnlyProps(storDriverAccCtx))
                .getProp(StorageConstants.CONFIG_ZFS_THIN_POOL_KEY);
        }
        catch (InvalidKeyException | AccessDeniedException exc)
        {
            throw new ImplementationError(exc);
        }
        return zPool;
    }

    @Override
    public @Nullable LocalPropsChangePojo checkConfig(StorPoolInfo storPool)
        throws StorageException, AccessDeniedException
    {
        String thinZpoolName = getZPool(storPool);
        if (thinZpoolName == null)
        {
            throw new StorageException(
                "zPool name not given for storPool '" +
                    storPool.getName().displayValue + "'"
            );
        }
        thinZpoolName = thinZpoolName.trim();
        HashMap<String, ZfsInfo> zfsList = ZfsUtils.getThinZPoolsList(
            extCmdFactory.create(),
            Collections.singleton(thinZpoolName)
        );
        if (!zfsList.containsKey(thinZpoolName))
        {
            throw new StorageException("no zfs dataset found with name '" + thinZpoolName + "'");
        }

        return null;
    }

    @Override
    public SpaceInfo getSpaceInfo(StorPoolInfo storPool) throws StorageException, AccessDeniedException
    {
        String zPool = getZPool(storPool);
        if (zPool == null)
        {
            throw new StorageException("Unset thin zfs dataset for " + storPool);
        }

        long capacity = ZfsUtils.getZPoolTotalSize(
            extCmdFactory.create(),
            Collections.singleton(zPool)
        ).get(zPool);

        long freeSpace = ZfsUtils.getThinZPoolsList(
            extCmdFactory.create(),
            Collections.singleton(zPool)
        ).get(zPool).usableSize;

        return SpaceInfo.buildOrThrowOnError(capacity, freeSpace, storPool);
    }

    @Override
    protected void setAllocatedSize(ZfsData<Resource> vlmDataRef, long sizeRef) throws DatabaseException
    {
        // this method is called (for now) only with an input from "blockdev --getsize64 ..."
        // however, we want to ignore that "allocatedSize", and use instead the size from "zfs list ..."
    }
}
