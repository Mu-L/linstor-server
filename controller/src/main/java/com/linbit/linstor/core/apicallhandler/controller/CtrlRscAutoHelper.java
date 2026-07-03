package com.linbit.linstor.core.apicallhandler.controller;

import com.linbit.linstor.annotation.Nullable;
import com.linbit.linstor.api.ApiCallRc;
import com.linbit.linstor.api.ApiCallRcImpl;
import com.linbit.linstor.api.interfaces.AutoSelectFilterApi;
import com.linbit.linstor.core.apicallhandler.ScopeRunner;
import com.linbit.linstor.core.apicallhandler.controller.internal.CtrlSatelliteUpdateCaller;
import com.linbit.linstor.core.apicallhandler.response.CtrlResponseUtils;
import com.linbit.linstor.core.apicallhandler.response.ResponseContext;
import com.linbit.linstor.core.identifier.NodeName;
import com.linbit.linstor.core.objects.Resource;
import com.linbit.linstor.core.objects.ResourceDefinition;
import com.linbit.linstor.core.repository.ResourceDefinitionRepositoryImpl;
import com.linbit.linstor.logging.ErrorReporter;
import com.linbit.locks.LockGuardFactory;
import com.linbit.locks.LockGuardFactory.LockObj;
import com.linbit.locks.LockGuardFactory.LockType;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import reactor.core.publisher.Flux;

@Singleton
public class CtrlRscAutoHelper
{
    private final ErrorReporter errorReporter;
    private final CtrlApiDataLoader dataLoader;
    private final CtrlRscCrtApiHelper rscCrtHelper;
    private final CtrlRscDeleteApiHelper rscDelHelper;
    private final CtrlResyncAfterHelper resyncAfterHelper;
    private final CtrlSatelliteUpdateCaller ctrlSatelliteUpdateCaller;

    private final List<AutoHelper> autohelperList;
    private final ScopeRunner scopeRunner;
    private final LockGuardFactory lockGuardFactory;
    private final ResourceDefinitionRepositoryImpl rscDfnRepo;
    private final CtrlTransactionHelper ctrlTxHelper;

    public static class AutoHelperResult
    {
        private @Nullable Flux<ApiCallRc> flux;
        private boolean preventUpdateSatellitesForResourceDelete;

        private AutoHelperResult()
        {
        }

        public @Nullable Flux<ApiCallRc> getFlux()
        {
            return flux;
        }

        public boolean isPreventUpdateSatellitesForResourceDelete()
        {
            return preventUpdateSatellitesForResourceDelete;
        }
    }

    public enum AutoHelperType {
        DrbdProxy,
        TieBreaker,
        AutoQuorum,
        AutoRePlace,
        VerifyAlgorithm,
        All,
    }

    @Inject
    public CtrlRscAutoHelper(
        CtrlRscAutoQuorumHelper autoQuorumHelperRef,
        CtrlRscAutoTieBreakerHelper autoTieBreakerRef,
        CtrlRscAutoDrbdProxyHelper autoDrbdProxyHelperRef,
        CtrlRscAutoRePlaceRscHelper autoRePlaceRscHelperRef,
        CtrlRscDfnAutoVerifyAlgoHelper autoVerifyAlgoHelperRef,
        CtrlResyncAfterHelper resyncAfterHelperRef,
        CtrlApiDataLoader dataLoaderRef,
        CtrlRscCrtApiHelper rscCrtHelperRef,
        CtrlRscDeleteApiHelper rscDelHelperRef,
        CtrlSatelliteUpdateCaller ctrlSatelliteUpdateCallerRef,
        ScopeRunner scopeRunnerRef,
        LockGuardFactory lockGuardFactoryRef,
        ResourceDefinitionRepositoryImpl rscDfnRepoRef,
        CtrlTransactionHelper ctrlTxHelperRef,
        ErrorReporter errorReporterRef
    )
    {
        ctrlTxHelper = ctrlTxHelperRef;
        autohelperList = Arrays
            .asList(
                autoDrbdProxyHelperRef,
                autoRePlaceRscHelperRef,
                autoVerifyAlgoHelperRef,
                // run autotiebreaker + autoquorum as last
                autoTieBreakerRef,
                autoQuorumHelperRef
            );

        dataLoader = dataLoaderRef;
        rscCrtHelper = rscCrtHelperRef;
        rscDelHelper = rscDelHelperRef;
        ctrlSatelliteUpdateCaller = ctrlSatelliteUpdateCallerRef;
        scopeRunner = scopeRunnerRef;
        lockGuardFactory = lockGuardFactoryRef;
        rscDfnRepo = rscDfnRepoRef;
        resyncAfterHelper = resyncAfterHelperRef;
        errorReporter = errorReporterRef;
    }

    public AutoHelperResult manage(ApiCallRcImpl apiCallRcImplRef, ResponseContext context, String rscNameStrRef)
    {
        return manage(new AutoHelperContext(apiCallRcImplRef, context, dataLoader.loadRscDfn(rscNameStrRef, true)));
    }

    public Flux<ApiCallRc> manageAll(AutoHelperContext autoCtxWithoutRscDfn)
    {
        return scopeRunner.fluxInTransactionalScope(
            "Create storage pool",
            lockGuardFactory.buildDeferred(LockType.WRITE, LockObj.NODES_MAP, LockObj.RSC_DFN_MAP),
            () -> manageAllInTransaction(autoCtxWithoutRscDfn)
        );
    }

    private Flux<ApiCallRc> manageAllInTransaction(AutoHelperContext autoCtxWithoutRscDfn)
    {
        List<Flux<ApiCallRc>> fluxList = new ArrayList<>();
        for (ResourceDefinition rscDfn : rscDfnRepo.getMapForView().values())
        {
            AutoHelperResult result = manage(
                new AutoHelperContext(
                    autoCtxWithoutRscDfn.responses,
                    autoCtxWithoutRscDfn.responseContext,
                    rscDfn
                )
            );
            fluxList.add(result.flux);
        }
        ctrlTxHelper.commit();
        return Flux.merge(fluxList);
    }

    public Flux<ApiCallRc> manageInOwnTransaction(AutoHelperContext autoCtx)
    {
        return scopeRunner.fluxInTransactionalScope(
            "Create storage pool",
            lockGuardFactory.buildDeferred(LockType.WRITE, LockObj.NODES_MAP, LockObj.RSC_DFN_MAP),
            () -> manage(autoCtx).flux
        );
    }

    public AutoHelperResult manage(AutoHelperContext ctx)
    {
        return manage(ctx, Collections.singleton(AutoHelperType.All));
    }

    public AutoHelperResult manage(AutoHelperContext ctx, Set<AutoHelperType> typeFilter)
    {
        AutoHelperResult result = new AutoHelperResult();
        boolean fluxUpdateApplied = false;

        for (AutoHelper autohelper : autohelperList)
        {
            if (typeFilter.contains(AutoHelperType.All) || typeFilter.contains(autohelper.getType()))
            {
                autohelper.manage(ctx);
            }
        }

        ctx.additionalFluxList.add(resyncAfterHelper.fluxManage());

        if (!ctx.resourcesToCreate.isEmpty())
        {
            ctx.additionalFluxList.add(
                rscCrtHelper.deployResources(
                    ctx.responseContext,
                    ctx.resourcesToCreate
                )
            );
            fluxUpdateApplied = true;
        }

        if (!ctx.nodeNamesForDelete.isEmpty())
        {
            ctx.additionalFluxList.add(
                rscDelHelper.updateSatellitesForResourceDelete(
                    ctx.responseContext,
                    ctx.nodeNamesForDelete,
                    ctx.rscDfn.getName()
                )
            );
            fluxUpdateApplied = true;
        }

        if (ctx.requiresUpdateFlux && !fluxUpdateApplied)
        {
            ctx.additionalFluxList.add(
                ctrlSatelliteUpdateCaller.updateSatellites(ctx.rscDfn, Flux.empty())
                    .transform(
                        updateResponses -> CtrlResponseUtils.combineResponses(
                            errorReporter,
                            updateResponses,
                            ctx.rscDfn.getName(),
                            "Resource {1} updated on node {0}"
                        )
                    )
            );
        }

        result.flux = Flux.merge(ctx.additionalFluxList);
        result.preventUpdateSatellitesForResourceDelete = ctx.preventUpdateSatellitesForResourceDelete;
        return result;
    }

    public @Nullable Resource getTiebreakerResource(String nodeNameRef, String nameRef)
    {
        @Nullable Resource ret = null;
        @Nullable Resource rsc = dataLoader.loadRsc(nodeNameRef, nameRef, false);
        if (rsc != null && rsc.getStateFlags().isSet(Resource.Flags.TIE_BREAKER))
        {
            ret = rsc;
        }
        return ret;
    }

    public static class AutoHelperContext
    {
        final ApiCallRcImpl responses;
        final ResponseContext responseContext;
        final ResourceDefinition rscDfn;

        @Nullable AutoSelectFilterApi selectFilter;

        TreeSet<Resource> resourcesToCreate = new TreeSet<>();
        TreeSet<NodeName> nodeNamesForDelete = new TreeSet<>();

        List<Flux<ApiCallRc>> additionalFluxList = new ArrayList<>();

        boolean requiresUpdateFlux = false;

        boolean preventUpdateSatellitesForResourceDelete = false;
        boolean keepTiebreaker;

        public AutoHelperContext(
            ApiCallRcImpl responsesRef,
            ResponseContext contextRef,
            ResourceDefinition definitionRef
        )
        {
            responses = responsesRef;
            responseContext = contextRef;
            rscDfn = definitionRef;
        }

        public AutoHelperContext withSelectFilter(AutoSelectFilterApi selectFilterRef)
        {
            selectFilter = selectFilterRef;
            return this;
        }

        public AutoHelperContext withKeepTiebreaker(boolean keepTiebreakerRef)
        {
            keepTiebreaker = keepTiebreakerRef;
            return this;
        }
    }

    interface AutoHelper
    {
        void manage(AutoHelperContext ctx);
        AutoHelperType getType();
    }
}
