package com.linbit.linstor.core.repository;

import com.linbit.linstor.annotation.Nullable;
import com.linbit.linstor.core.CoreModule;
import com.linbit.linstor.core.identifier.ResourceGroupName;
import com.linbit.linstor.core.objects.ResourceGroup;

import javax.inject.Inject;
import javax.inject.Singleton;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Holds the singleton KeyValueStore map protection instance, allowing it to be initialized from the database
 * after dependency injection has been performed.
 */
@Singleton
public class ResourceGroupProtectionRepository implements ResourceGroupRepository
{
    private final CoreModule.ResourceGroupMap rscGrpMap;

    // can't initialize objProt in constructor because of chicken-egg-problem
    @SuppressFBWarnings("NP_NONNULL_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR")
    @Inject
    public ResourceGroupProtectionRepository(CoreModule.ResourceGroupMap rscGrpRef)
    {
        rscGrpMap = rscGrpRef;
    }

    public void setObjectProtection()
    {
        if (rscGrpMapObjProt != null)
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
    public @Nullable ResourceGroup get(
        ResourceGroupName rscGrpName
    )
    {
        checkProtSet();
        return rscGrpMap.get(rscGrpName);
    }

    @Override
    public void put(ResourceGroup rscGrp)
    {
        checkProtSet();
        rscGrpMap.put(rscGrp.getName(), rscGrp);
    }

    @Override
    public void remove(ResourceGroupName rscGrpName)
    {
        checkProtSet();
        rscGrpMap.remove(rscGrpName);
    }

    @Override
    public CoreModule.ResourceGroupMap getMapForView()
    {
        checkProtSet();
        return rscGrpMap;
    }

    private void checkProtSet()
    {
        if (rscGrpMapObjProt == null)
        {
            throw new IllegalStateException("Object protection not yet set");
        }
    }
}
