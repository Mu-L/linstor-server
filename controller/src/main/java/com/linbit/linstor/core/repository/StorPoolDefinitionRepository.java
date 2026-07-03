package com.linbit.linstor.core.repository;

import com.linbit.linstor.annotation.Nullable;
import com.linbit.linstor.core.CoreModule;
import com.linbit.linstor.core.identifier.StorPoolName;
import com.linbit.linstor.core.objects.StorPoolDefinition;

/**
 * Provides access to stor pool definitions with automatic security checks.
 */
public interface StorPoolDefinitionRepository extends ProtectedObject
{
    void requireAccess(AccessType requested);

    @Nullable
    StorPoolDefinition get(StorPoolName nameRef);

    void put(StorPoolName storPoolName, StorPoolDefinition storPoolDefinition);

    void remove(StorPoolName storPoolName);

    CoreModule.StorPoolDefinitionMap getMapForView();
}
