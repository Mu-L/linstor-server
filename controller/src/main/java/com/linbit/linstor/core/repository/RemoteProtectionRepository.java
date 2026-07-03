package com.linbit.linstor.core.repository;

import com.linbit.linstor.annotation.Nullable;
import com.linbit.linstor.core.CoreModule;
import com.linbit.linstor.core.CoreModule.RemoteMap;
import com.linbit.linstor.core.identifier.RemoteName;
import com.linbit.linstor.core.objects.remotes.AbsRemote;

import javax.inject.Inject;
import javax.inject.Singleton;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@Singleton
public class RemoteProtectionRepository implements RemoteRepository
{
    private final CoreModule.RemoteMap remoteMap;

    // can't initialize objProt in constructor because of chicken-egg-problem
    @SuppressFBWarnings("NP_NONNULL_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR")
    @Inject
    public RemoteProtectionRepository(CoreModule.RemoteMap remoteMapRef)
    {
        remoteMap = remoteMapRef;
    }

    public void setObjectProtection()
    {
        if (remoteMapObjProt != null)
        {
            throw new IllegalStateException("Object protection already set");
        }
    }

    @SuppressFBWarnings("UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR")
    @Override
    public void requireAccess(AccessType requested)
    {
        // spotbugs-warning suppressed, because checkProtSet() does exactly what is needed
        checkProtSet();
    }

    @Override
    public @Nullable AbsRemote get(RemoteName remoteNameRef)
    {
        checkProtSet();
        return remoteMap.get(remoteNameRef);
    }

    @Override
    public void put(AbsRemote remoteRef)
    {
        checkProtSet();
        remoteMap.put(remoteRef.getName(), remoteRef);
    }

    @Override
    public void remove(RemoteName remoteNameRef)
    {
        checkProtSet();
        remoteMap.remove(remoteNameRef);
    }

    @Override
    public RemoteMap getMapForView()
    {
        checkProtSet();
        return remoteMap;
    }

    private void checkProtSet()
    {
        if (remoteMapObjProt == null)
        {
            throw new IllegalStateException("Object protection not yet set");
        }
    }
}
