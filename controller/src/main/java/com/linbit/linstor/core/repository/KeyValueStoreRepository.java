package com.linbit.linstor.core.repository;

import com.linbit.linstor.annotation.Nullable;
import com.linbit.linstor.core.CoreModule;
import com.linbit.linstor.core.identifier.KeyValueStoreName;
import com.linbit.linstor.core.objects.KeyValueStore;

/**
 * Provides access to {@link KeyValueStore}s with automatic security checks.
 */
public interface KeyValueStoreRepository extends ProtectedObject
{
    void requireAccess(AccessType requested);

    @Nullable
    KeyValueStore get(KeyValueStoreName nameRef);

    void put(KeyValueStoreName kvsName, KeyValueStore kvs);

    void remove(KeyValueStoreName kvsName);

    CoreModule.KeyValueStoreMap getMapForView();
}
