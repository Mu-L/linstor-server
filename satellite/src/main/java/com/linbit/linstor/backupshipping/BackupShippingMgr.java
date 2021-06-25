package com.linbit.linstor.backupshipping;

import com.linbit.ImplementationError;
import com.linbit.InvalidNameException;
import com.linbit.linstor.InternalApiConsts;
import com.linbit.linstor.annotation.SystemContext;
import com.linbit.linstor.api.ApiConsts;
import com.linbit.linstor.core.CoreModule.RemoteMap;
import com.linbit.linstor.core.identifier.RemoteName;
import com.linbit.linstor.core.objects.Remote;
import com.linbit.linstor.core.objects.Remote.RemoteType;
import com.linbit.linstor.core.objects.Snapshot;
import com.linbit.linstor.core.objects.SnapshotVolume;
import com.linbit.linstor.propscon.InvalidKeyException;
import com.linbit.linstor.security.AccessContext;
import com.linbit.linstor.security.AccessDeniedException;
import com.linbit.linstor.storage.interfaces.categories.resource.VlmProviderObject;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class BackupShippingMgr
{
    private final AccessContext accCtx;
    private final RemoteMap remoteMap;
    private final Map<RemoteType, AbsBackupShippingService> services;
    private final BackupShippingL2LService backupShippingL2L;

    @Inject
    public BackupShippingMgr(
        @SystemContext AccessContext accCtxRef,
        RemoteMap remoteMapRef,
        BackupShippingS3Service backupShippingS3Ref,
        BackupShippingL2LService backupShippingL2LRef
    )
    {
        accCtx = accCtxRef;
        remoteMap = remoteMapRef;
        backupShippingL2L = backupShippingL2LRef;
        services = new HashMap<>();

        services.put(RemoteType.S3, backupShippingS3Ref);
        services.put(RemoteType.SATELLTE, backupShippingL2LRef);
    }

    public AbsBackupShippingService getService(RemoteType remoteType)
    {
        return services.get(remoteType);
    }

    public AbsBackupShippingService getService(Remote remote)
    {
        return getService(remote.getType());
    }

    public AbsBackupShippingService getService(VlmProviderObject<Snapshot> snapVlmRef)
    {
        return getService(((SnapshotVolume) snapVlmRef.getVolume()).getSnapshot());
    }

    public @Nullable AbsBackupShippingService getService(Snapshot snapshotRef)
    {
        AbsBackupShippingService service = null;
        try
        {
            String remoteNameStr = snapshotRef.getProps(accCtx).getProp(
                InternalApiConsts.KEY_BACKUP_TARGET_REMOTE,
                ApiConsts.NAMESPC_BACKUP_SHIPPING
            );

            if (remoteNameStr != null)
            {
                Remote remote = remoteMap.get(new RemoteName(remoteNameStr, true));
                if (remote == null)
                {
                    throw new ImplementationError("Remote must not be null if the property is set");
                }

                service = getService(remote);
            }
        }
        catch (InvalidKeyException | InvalidNameException | AccessDeniedException exc)
        {
            throw new ImplementationError(exc);
        }
        return service;
    }

    public void allBackupPartsRegistered(Snapshot snapshotRef)
    {
        AbsBackupShippingService backupShippingService = getService(snapshotRef);
        if (backupShippingService != null)
        {
            backupShippingService.allBackupPartsRegistered(snapshotRef);
        }
    }

    public void snapshotDeleted(Snapshot snapshotRef)
    {
        AbsBackupShippingService backupShippingService = getService(snapshotRef);
        if (backupShippingService != null)
        {
            backupShippingService.snapshotDeleted(snapshotRef);
        }
    }

    public Collection<AbsBackupShippingService> getAllServices()
    {
        return services.values();
    }
}
