package com.linbit.linstor.core.repository;

import com.linbit.linstor.annotation.Nullable;
import com.linbit.linstor.core.CoreModule;
import com.linbit.linstor.core.CoreModule.ExternalFileMap;
import com.linbit.linstor.core.identifier.ExternalFileName;
import com.linbit.linstor.core.objects.ExternalFile;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ExternalFileProtectionRepository implements ExternalFileRepository
{
    private final CoreModule.ExternalFileMap extFileMap;

    @Inject
    public ExternalFileProtectionRepository(CoreModule.ExternalFileMap extFileMapRef)
    {
        extFileMap = extFileMapRef;
    }

    public void setObjectProtection()
    {
        if (extFileMapObjProt != null)
        {
            throw new IllegalStateException("Object protection already set");
        }
    }

    @Override
    public @Nullable ObjectProtection getObjProt()
    {
        checkProtSet();
        return extFileMapObjProt;
    }

    @Override
    public void requireAccess(AccessType requested)
    {
        checkProtSet();
    }

    @Override
    public @Nullable ExternalFile get(ExternalFileName externalFileNameRef)
    {
        checkProtSet();
        return extFileMap.get(externalFileNameRef);
    }

    @Override
    public void put(ExternalFile externalFileRef)
    {
        checkProtSet();
        extFileMap.put(externalFileRef.getName(), externalFileRef);
    }

    @Override
    public void remove(ExternalFileName externalFileNameRef)
    {
        checkProtSet();
        extFileMap.remove(externalFileNameRef);
    }

    @Override
    public ExternalFileMap getMapForView()
    {
        checkProtSet();
        return extFileMap;
    }

    private void checkProtSet()
    {
        if (extFileMapObjProt == null)
        {
            throw new IllegalStateException("Object protection not yet set");
        }
    }

}
