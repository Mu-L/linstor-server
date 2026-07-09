package com.linbit.linstor.api;

import com.linbit.linstor.api.utils.AbsApiCallTester;
import com.linbit.linstor.core.ApiTestBase;
import com.linbit.linstor.core.apicallhandler.controller.CtrlVlmDfnModifyApiCallHandler;
import com.linbit.linstor.core.apicallhandler.controller.FreeCapacityFetcher;
import com.linbit.linstor.core.objects.ResourceDefinition;
import com.linbit.linstor.core.objects.VolumeDefinition;
import com.linbit.linstor.storage.kinds.DeviceLayerKind;

import javax.inject.Inject;
import javax.inject.Provider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.google.inject.testing.fieldbinder.Bind;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("checkstyle:magicnumber")
public class VlmDfnModifyApiTest extends ApiTestBase
{
    private static final String TEST_RSC_NAME = "TestVlmDfnRsc";
    private static final int TEST_VLM_NR = 0;
    private static final long TEST_VLM_SIZE = 100 * 1024L; // size in KiB

    @Inject private Provider<CtrlVlmDfnModifyApiCallHandler> vlmDfnModifyApiCallHandlerProvider;

    @Bind @Mock
    protected FreeCapacityFetcher freeCapacityFetcher;

    private ResourceDefinition testRscDfn;
    private VolumeDefinition testVlmDfn;

    @Before
    @Override
    public void setUp() throws Exception
    {
        super.setUp();

        testRscDfn = resourceDefinitionTestFactory.builder(TEST_RSC_NAME)
            .setLayerStack(new ArrayList<>(Arrays.asList(DeviceLayerKind.DRBD, DeviceLayerKind.STORAGE)))
            .build();
        rscDfnMap.put(testRscDfn.getName(), testRscDfn);

        testVlmDfn = volumeDefinitionTestFactory.builder(TEST_RSC_NAME, TEST_VLM_NR)
            .setSize(TEST_VLM_SIZE)
            .build();

        leaveScope();
    }

    @Test
    public void modProps() throws Exception
    {
        String auxKey = ApiConsts.NAMESPC_AUXILIARY + "/test";
        evaluateTest(
            new ModifyVlmDfnCall(
                ApiConsts.MASK_CRT | ApiConsts.CREATED, // props set
                ApiConsts.MODIFIED
            )
                .overrideProps(auxKey, "value")
        );
        assertThat(testVlmDfn.getProps().getProp(auxKey)).isEqualTo("value");

        evaluateTest(
            new ModifyVlmDfnCall(
                ApiConsts.MASK_DEL | ApiConsts.DELETED, // props deleted
                ApiConsts.MODIFIED
            )
                .deleteProp(auxKey)
        );
        assertThat(testVlmDfn.getProps().getProp(auxKey)).isNull();
    }

    @Test
    public void modInvalidProp() throws Exception
    {
        evaluateTest(
            new ModifyVlmDfnCall(ApiConsts.FAIL_INVLD_PROP)
                .overrideProps("ThisIsNotAWhitelistedKey", "value")
        );
    }

    @Test
    public void modWrongUuid() throws Exception
    {
        evaluateTest(
            new ModifyVlmDfnCall(ApiConsts.FAIL_UUID_VLM_DFN)
                .vlmDfnUuid(randomUUID())
        );
    }

    @Test
    public void modUnknownVlmNr() throws Exception
    {
        evaluateTest(
            new ModifyVlmDfnCall(ApiConsts.FAIL_NOT_FOUND_VLM_DFN)
                .vlmNr(4)
        );
    }

    @Test
    public void modInvalidVlmNr() throws Exception
    {
        evaluateTest(
            new ModifyVlmDfnCall(ApiConsts.FAIL_INVLD_VLM_NR)
                .vlmNr(-1)
        );
    }

    @Test
    public void modUnknownRscDfn() throws Exception
    {
        evaluateTest(
            new ModifyVlmDfnCall(ApiConsts.FAIL_NOT_FOUND_RSC_DFN)
                .rscName("UnknownRsc")
        );
    }

    @Test
    public void modGrowSize() throws Exception
    {
        // growing a volume definition without deployed volumes only updates the size
        evaluateTest(
            new ModifyVlmDfnCall(ApiConsts.MODIFIED)
                .size(TEST_VLM_SIZE * 2)
        );

        assertThat(testVlmDfn.getVolumeSize()).isEqualTo(TEST_VLM_SIZE * 2);
        assertThat(testVlmDfn.getFlags().isSet(VolumeDefinition.Flags.RESIZE)).isFalse();
        assertThat(testVlmDfn.getFlags().isSet(VolumeDefinition.Flags.RESIZE_SHRINK)).isFalse();
    }

    @Test
    public void modShrinkSizeWithoutDeployedVolumes() throws Exception
    {
        // without deployed volumes there is no layer / provider that could veto shrinking,
        // so the volume definition simply shrinks. The RESIZE_SHRINK flag (which includes the
        // RESIZE bit) triggers the resize workflow, which immediately finishes and clears the
        // flags again since no satellites are involved
        evaluateTest(
            new ModifyVlmDfnCall(ApiConsts.MODIFIED)
                .size(TEST_VLM_SIZE / 2)
        );

        assertThat(testVlmDfn.getVolumeSize()).isEqualTo(TEST_VLM_SIZE / 2);
        assertThat(testVlmDfn.getFlags().isSet(VolumeDefinition.Flags.RESIZE)).isFalse();
        assertThat(testVlmDfn.getFlags().isSet(VolumeDefinition.Flags.RESIZE_SHRINK)).isFalse();
    }

    @Test
    public void modSameSize() throws Exception
    {
        evaluateTest(
            new ModifyVlmDfnCall(
                ApiConsts.WARN_VLMDFN_RESIZE_SAME_SIZE,
                ApiConsts.MODIFIED
            )
                .size(TEST_VLM_SIZE)
        );

        assertThat(testVlmDfn.getVolumeSize()).isEqualTo(TEST_VLM_SIZE);
    }

    @Test
    public void modGrowSizeWithExactSizeSet() throws Exception
    {
        enterScope();
        testRscDfn.getProps().setProp(
            ApiConsts.KEY_DRBD_EXACT_SIZE,
            "True",
            ApiConsts.NAMESPC_DRBD_OPTIONS
        );
        leaveScope();

        evaluateTest(
            new ModifyVlmDfnCall(ApiConsts.FAIL_INVLD_PROP)
                .size(TEST_VLM_SIZE * 2)
        );
        assertThat(testVlmDfn.getVolumeSize()).isEqualTo(TEST_VLM_SIZE);
    }

    @Test
    public void modGrossSizeFlag() throws Exception
    {
        evaluateTest(
            new ModifyVlmDfnCall(ApiConsts.MODIFIED)
                .flags(VolumeDefinition.Flags.GROSS_SIZE.name())
        );
        assertThat(testVlmDfn.getFlags().isSet(VolumeDefinition.Flags.GROSS_SIZE)).isTrue();

        evaluateTest(
            new ModifyVlmDfnCall(ApiConsts.MODIFIED)
                .flags("-" + VolumeDefinition.Flags.GROSS_SIZE.name())
        );
        assertThat(testVlmDfn.getFlags().isSet(VolumeDefinition.Flags.GROSS_SIZE)).isFalse();
    }

    private class ModifyVlmDfnCall extends AbsApiCallTester
    {
        private java.util.UUID vlmDfnUuid;
        private String rscName;
        private int vlmNr;
        private Long size;
        private final Map<String, String> overrideProps;
        private final Set<String> deletePropKeys;
        private final List<String> vlmDfnFlags;

        ModifyVlmDfnCall(long... expectedRcs)
        {
            super(
                ApiConsts.MASK_VLM_DFN,
                ApiConsts.MASK_MOD,
                expectedRcs
            );

            vlmDfnUuid = null; // default: do not check against uuid
            rscName = TEST_RSC_NAME;
            vlmNr = TEST_VLM_NR;
            size = null; // default: do not change size
            overrideProps = new TreeMap<>();
            deletePropKeys = new TreeSet<>();
            vlmDfnFlags = new ArrayList<>();
        }

        @Override
        public ApiCallRc executeApiCall()
        {
            ApiCallRcImpl apiCallRc = new ApiCallRcImpl();
            vlmDfnModifyApiCallHandlerProvider.get().modifyVlmDfn(
                vlmDfnUuid,
                rscName,
                vlmNr,
                size,
                overrideProps,
                deletePropKeys,
                vlmDfnFlags
            )
                .contextWrite(contextWrite())
                .toStream()
                .forEach(apiCallRc::addEntries);
            return apiCallRc;
        }

        public ModifyVlmDfnCall vlmDfnUuid(java.util.UUID uuid)
        {
            vlmDfnUuid = uuid;
            return this;
        }

        public ModifyVlmDfnCall rscName(String rscNameRef)
        {
            rscName = rscNameRef;
            return this;
        }

        public ModifyVlmDfnCall vlmNr(int vlmNrRef)
        {
            vlmNr = vlmNrRef;
            return this;
        }

        public ModifyVlmDfnCall size(long sizeRef)
        {
            size = sizeRef;
            return this;
        }

        public ModifyVlmDfnCall overrideProps(String key, String value)
        {
            overrideProps.put(key, value);
            return this;
        }

        public ModifyVlmDfnCall deleteProp(String key)
        {
            deletePropKeys.add(key);
            return this;
        }

        public ModifyVlmDfnCall flags(String... flags)
        {
            vlmDfnFlags.clear();
            vlmDfnFlags.addAll(Arrays.asList(flags));
            return this;
        }
    }
}
