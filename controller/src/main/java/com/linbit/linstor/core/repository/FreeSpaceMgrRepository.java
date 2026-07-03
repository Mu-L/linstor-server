package com.linbit.linstor.core.repository;

import com.linbit.linstor.core.ControllerCoreModule;
import com.linbit.linstor.core.identifier.SharedStorPoolName;
import com.linbit.linstor.core.objects.FreeSpaceMgr;

/**
 * Provides access to free space managers with automatic security checks.
 */
public interface FreeSpaceMgrRepository
{
    ObjectProtection getObjProt();

    void requireAccess(AccessType requested);

    FreeSpaceMgr get(SharedStorPoolName sharedStorPoolName);

    void put(SharedStorPoolName sharedStorPoolName, FreeSpaceMgr freeSpaceMgr);

    void remove(SharedStorPoolName sharedStorPoolName);

    ControllerCoreModule.FreeSpaceMgrMap getMapForView();
}
