package com.linbit.linstor.core.repository;

import com.linbit.ImplementationError;
import com.linbit.linstor.annotation.Nullable;
import com.linbit.linstor.core.CoreModule;
import com.linbit.linstor.core.identifier.ResourceName;
import com.linbit.linstor.core.objects.ResourceDefinition;

import javax.inject.Inject;
import javax.inject.Singleton;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Holds the singleton resource definition map protection instance, allowing it to be initialized from the database
 * after dependency injection has been performed.
 */
@Singleton
public class ResourceDefinitionProtectionRepository implements ResourceDefinitionRepository
{
    private final CoreModule.ResourceDefinitionMap rscDfnMap;
    private final CoreModule.ResourceDefinitionMapExtName rscDfnMapExtName;

    // can't initialize objProt in constructor because of chicken-egg-problem
    @SuppressFBWarnings("NP_NONNULL_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR")
    @Inject
    public ResourceDefinitionProtectionRepository(CoreModule.ResourceDefinitionMap rscDfnMapRef,
                                                  CoreModule.ResourceDefinitionMapExtName rscDfnMapExtNameRef)
    {
        rscDfnMap = rscDfnMapRef;
        rscDfnMapExtName = rscDfnMapExtNameRef;
    }

    public void setObjectProtection()
    {
        if (resourceDefinitionMapObjProt != null)
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
    public @Nullable ResourceDefinition get(ResourceName resourceName)
    {
        checkProtSet();
        return rscDfnMap.get(resourceName);
    }

    @Override
    public ResourceDefinition get(byte[] externalName)
    {
        checkProtSet();
        return rscDfnMapExtName.get(externalName);
    }

    @Override
    public void put(ResourceDefinition resourceDefinition)
    {
        checkProtSet();

        ResourceDefinition rscDfn = rscDfnMap.put(resourceDefinition.getName(), resourceDefinition);
        if (rscDfn != null)
        {
            throw new ImplementationError("Resource definition name already exists!");
        }
        if (resourceDefinition.getExternalName() != null)
        {
            rscDfnMapExtName.put(resourceDefinition.getExternalName(), resourceDefinition);
        }
    }


    @Override
    public void remove(ResourceName resourceName, byte[] externalName)
    {
        checkProtSet();
        rscDfnMap.remove(resourceName);
        if (externalName != null)
        {
            rscDfnMapExtName.remove(externalName);
        }
    }

    @Override
    public CoreModule.ResourceDefinitionMap getMapForView()
    {
        checkProtSet();
        return rscDfnMap;
    }

    @Override
    public CoreModule.ResourceDefinitionMapExtName getMapForViewExtName()
    {
        checkProtSet();
        return rscDfnMapExtName;
    }

    private void checkProtSet()
    {
        if (resourceDefinitionMapObjProt == null)
        {
            throw new IllegalStateException("Object protection not yet set");
        }
    }
}
