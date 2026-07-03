package com.linbit.linstor.core.repository;

import com.linbit.linstor.annotation.Nullable;
import com.linbit.linstor.core.CoreModule;
import com.linbit.linstor.core.identifier.StorPoolName;
import com.linbit.linstor.core.objects.StorPoolDefinition;

import javax.inject.Inject;
import javax.inject.Singleton;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Holds the singleton stor pool definition map protection instance, allowing it to be initialized from the database
 * after dependency injection has been performed.
 */
@Singleton
public class StorPoolDefinitionProtectionRepository implements StorPoolDefinitionRepository
{
    private final CoreModule.StorPoolDefinitionMap storPoolDefinitionMap;

    // can't initialize objProt in constructor because of chicken-egg-problem
    @SuppressFBWarnings("NP_NONNULL_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR")
    @Inject
    public StorPoolDefinitionProtectionRepository(CoreModule.StorPoolDefinitionMap storPoolDefinitionMapRef)
    {
        storPoolDefinitionMap = storPoolDefinitionMapRef;
    }

    public void setObjectProtection()
    {
        if (storPoolDefinitionMapObjProt != null)
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
    public @Nullable StorPoolDefinition get(
        StorPoolName storPoolName
    )
    {
        checkProtSet();
        return storPoolDefinitionMap.get(storPoolName);
    }

    @Override
    public void put(StorPoolName storPoolName, StorPoolDefinition storPoolDefinition)
    {
        checkProtSet();
        storPoolDefinitionMap.put(storPoolName, storPoolDefinition);
    }

    @Override
    public void remove(StorPoolName storPoolName)
    {
        checkProtSet();
        storPoolDefinitionMap.remove(storPoolName);
    }

    @Override
    public CoreModule.StorPoolDefinitionMap getMapForView()
    {
        checkProtSet();
        return storPoolDefinitionMap;
    }

    private void checkProtSet()
    {
        if (storPoolDefinitionMapObjProt == null)
        {
            throw new IllegalStateException("Object protection not yet set");
        }
    }
}
