package com.linbit.linstor.core.repository;

import com.linbit.linstor.annotation.Nullable;
import com.linbit.linstor.core.CoreModule;
import com.linbit.linstor.core.identifier.NodeName;
import com.linbit.linstor.core.objects.Node;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.ArrayList;
import java.util.Collection;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Holds the singleton nodes map protection instance, allowing it to be initialized from the database
 * after dependency injection has been performed.
 */
@Singleton
public class NodeProtectionRepository implements NodeRepository
{
    private final CoreModule.NodesMap nodesMap;
    private final CoreModule.UnameMap uNameMap;

    // can't initialize objProt in constructor because of chicken-egg-problem
    @SuppressFBWarnings("NP_NONNULL_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR")
    @Inject
    public NodeProtectionRepository(CoreModule.NodesMap nodesMapRef, CoreModule.UnameMap uNameMapRef)
    {
        nodesMap = nodesMapRef;
        uNameMap = uNameMapRef;
    }

    public void setObjectProtection()
    {
        if (nodesMapObjProt != null)
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
    public @Nullable Node get(
        NodeName nodeName
    )
    {
        checkProtSet();
        return nodesMap.get(nodeName);
    }

    @Override
    public void put(NodeName nodeName, Node node)
    {
        checkProtSet();
        nodesMap.put(nodeName, node);
    }

    @Override
    public void remove(NodeName nodeName)
    {
        checkProtSet();
        nodesMap.remove(nodeName);

        ArrayList<String> uNamesToRemove = new ArrayList<>();
        for (var unameEntry : uNameMap.entrySet())
        {
            if (unameEntry.getValue().equals(nodeName))
            {
                uNamesToRemove.add(unameEntry.getKey());
            }
        }
        removeUnames(uNamesToRemove);
    }

    @Override
    public CoreModule.NodesMap getMapForView()
    {
        checkProtSet();
        return nodesMap;
    }

    private void checkProtSet()
    {
        if (nodesMapObjProt == null)
        {
            throw new IllegalStateException("Object protection not yet set");
        }
    }

    private void removeUnames(Collection<String> uNames)
    {
        for (var uName : uNames)
        {
            uNameMap.remove(uName);
        }
    }

    @Override
    public void putUname(String uName, NodeName nodeName)
    {
        checkProtSet();
        uNameMap.put(uName, nodeName);
    }

    @Override
    public @Nullable NodeName getUname(String uName)
    {
        checkProtSet();
        return uNameMap.get(uName);
    }

    @Override
    public void removeUname(String uName)
    {
        checkProtSet();
        uNameMap.remove(uName);
    }
}
