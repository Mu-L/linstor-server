package com.linbit.linstor.core.apicallhandler.controller.utils;

import com.linbit.linstor.PriorityProps;
import com.linbit.linstor.api.ApiCallRc;
import com.linbit.linstor.api.ApiConsts;
import com.linbit.linstor.core.apicallhandler.controller.CtrlSnapshotDeleteApiCallHandler;
import com.linbit.linstor.core.objects.Resource;
import com.linbit.linstor.core.objects.ResourceDefinition;
import com.linbit.linstor.propscon.ReadOnlyProps;
import com.linbit.linstor.tasks.AutoSnapshotTask;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import reactor.core.publisher.Flux;

public class ResourceDefinitionUtils
{
    public static int getResourceCount(
        ResourceDefinition rscDfn,
        Predicate<Resource> predicate
    )
    {
        int count = 0;
        Iterator<Resource> rscIt = rscDfn.iterateResource();
        while (rscIt.hasNext())
        {
            Resource rsc = rscIt.next();
            if (predicate.test(rsc))
            {
                count++;
            }
        }
        return count;
    }

    public static Flux<ApiCallRc> handleAutoSnapProps(
        AutoSnapshotTask autoSnapshotTaskRef,
        CtrlSnapshotDeleteApiCallHandler ctrlSnapDeleteHandlerRef,
        Map<String, String> overrideProps,
        Set<String> deletePropKeys,
        Set<String> deletedNamespaces,
        Collection<ResourceDefinition> affectedRscDfnListRef,
        ReadOnlyProps ctrlProps,
        boolean forceCheckAllRscDfnRef
    )
    {
        Flux<ApiCallRc> retFlux = Flux.empty();
        String autoSnapKey = ApiConsts.NAMESPC_AUTO_SNAPSHOT + "/" + ApiConsts.KEY_RUN_EVERY;
        String autoSnapVal = overrideProps.get(autoSnapKey);
        boolean namespaceDeleted = deletedNamespaces.contains(ApiConsts.NAMESPC_AUTO_SNAPSHOT);

        Set<ResourceDefinition> modifiedRscDfnSet = new HashSet<>();
        if (autoSnapVal != null || forceCheckAllRscDfnRef)
        {
            for (ResourceDefinition rscDfn : affectedRscDfnListRef)
            {
                PriorityProps prioProps = new PriorityProps(
                    rscDfn.getProps(),
                    rscDfn.getResourceGroup().getProps(),
                    ctrlProps
                );
                String prioPropVal = prioProps.getProp(autoSnapKey);
                if (prioPropVal != null)
                {
                    retFlux = retFlux.concatWith(
                        autoSnapshotTaskRef.addAutoSnapshotting(
                            rscDfn.getName().displayValue,
                            Long.parseLong(prioPropVal)
                        )
                    );
                    modifiedRscDfnSet.add(rscDfn);
                }
            }
        }
        if (deletePropKeys.contains(autoSnapKey) || namespaceDeleted || forceCheckAllRscDfnRef)
        {
            for (ResourceDefinition rscDfn : affectedRscDfnListRef)
            {
                if (!modifiedRscDfnSet.contains(rscDfn))
                {
                    PriorityProps prioProps = new PriorityProps(
                        rscDfn.getProps(),
                        rscDfn.getResourceGroup().getProps(),
                        ctrlProps
                    );
                    String prioPropVal = prioProps.getProp(autoSnapKey);
                    if (prioPropVal == null)
                    {
                        autoSnapshotTaskRef.removeAutoSnapshotting(rscDfn.getName().displayValue);
                    }
                    else
                    {
                        retFlux = retFlux.concatWith(
                            autoSnapshotTaskRef.addAutoSnapshotting(
                                rscDfn.getName().displayValue,
                                Long.parseLong(prioPropVal)
                            )
                        );
                    }
                    modifiedRscDfnSet.add(rscDfn);
                }
            }
        }

        String autoSnapKeepKey = ApiConsts.NAMESPC_AUTO_SNAPSHOT + "/" + ApiConsts.KEY_KEEP;
        if (overrideProps.containsKey(autoSnapKeepKey) || deletePropKeys.contains(autoSnapKeepKey) ||
            forceCheckAllRscDfnRef)
        {
            for (ResourceDefinition rscDfn : affectedRscDfnListRef)
            {
                if (!modifiedRscDfnSet.contains(rscDfn))
                {
                    retFlux = retFlux.concatWith(ctrlSnapDeleteHandlerRef.cleanupOldAutoSnapshots(rscDfn));
                }
            }
        }
        return retFlux;
    }
}
