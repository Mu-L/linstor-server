package com.linbit.linstor.core.repository;

import com.linbit.linstor.annotation.Nullable;
import com.linbit.linstor.core.CoreModule;
import com.linbit.linstor.core.identifier.KeyValueStoreName;
import com.linbit.linstor.core.objects.KeyValueStore;

import javax.inject.Inject;
import javax.inject.Singleton;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Holds the singleton KeyValueStore map protection instance, allowing it to be initialized from the database
 * after dependency injection has been performed.
 */
@Singleton
public class KeyValueStoreProtectionRepository implements KeyValueStoreRepository
{
    private final CoreModule.KeyValueStoreMap kvsMap;

    // can't initialize objProt in constructor because of chicken-egg-problem
    @SuppressFBWarnings("NP_NONNULL_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR")
    @Inject
    public KeyValueStoreProtectionRepository(CoreModule.KeyValueStoreMap kvsMapRef)
    {
        kvsMap = kvsMapRef;
    }

    public void setObjectProtection()
    {
        if (kvsMapObjProt != null)
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
    public @Nullable KeyValueStore get(
        KeyValueStoreName kvsName
    )
    {
        checkProtSet();
        return kvsMap.get(kvsName);
    }

    @Override
    public void put(KeyValueStoreName kvsName, KeyValueStore kvs)
    {
        checkProtSet();
        kvsMap.put(kvsName, kvs);
    }

    @Override
    public void remove(KeyValueStoreName kvsName)
    {
        checkProtSet();
        kvsMap.remove(kvsName);
    }

    @Override
    public CoreModule.KeyValueStoreMap getMapForView()
    {
        checkProtSet();
        return kvsMap;
    }

    private void checkProtSet()
    {
        if (kvsMapObjProt == null)
        {
            throw new IllegalStateException("Object protection not yet set");
        }
    }
}
