package com.linbit.linstor.core.repository;

import com.linbit.linstor.core.ControllerCoreModule;
import com.linbit.linstor.core.identifier.SharedStorPoolName;
import com.linbit.linstor.core.objects.FreeSpaceMgr;

import javax.inject.Inject;
import javax.inject.Singleton;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Holds the singleton free space manager map protection instance, allowing it to be initialized from the database
 * after dependency injection has been performed.
 */
@Singleton
public class FreeSpaceMgrProtectionRepository implements FreeSpaceMgrRepository
{
    private final ControllerCoreModule.FreeSpaceMgrMap freeSpaceMgrMap;

    // can't initialize objProt in constructor because of chicken-egg-problem
    @SuppressFBWarnings("NP_NONNULL_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR")
    @Inject
    public FreeSpaceMgrProtectionRepository(ControllerCoreModule.FreeSpaceMgrMap freeSpaceMgrMapRef)
    {
        freeSpaceMgrMap = freeSpaceMgrMapRef;
    }

    public void setObjectProtection()
    {
        if (freeSpaceMgrMapObjProt != null)
        {
            throw new IllegalStateException("Object protection already set");
        }
    }

    @Override
    public void requireAccess(AccessType requested)
    {
        checkProtSet();
    }

    @Override
    public FreeSpaceMgr get(
        SharedStorPoolName sharedStorPoolName
    )
    {
        checkProtSet();
        return freeSpaceMgrMap.get(sharedStorPoolName);
    }

    @Override
    public void put(SharedStorPoolName sharedStorPoolName, FreeSpaceMgr freeSpaceMgr)
    {
        checkProtSet();
        freeSpaceMgrMap.put(sharedStorPoolName, freeSpaceMgr);
    }

    @Override
    public void remove(SharedStorPoolName sharedStorPoolName)
    {
        checkProtSet();
        freeSpaceMgrMap.remove(sharedStorPoolName);
    }

    @Override
    public ControllerCoreModule.FreeSpaceMgrMap getMapForView()
    {
        checkProtSet();
        return freeSpaceMgrMap;
    }

    private void checkProtSet()
    {
        if (freeSpaceMgrMapObjProt == null)
        {
            throw new IllegalStateException("Object protection not yet set");
        }
    }
}
