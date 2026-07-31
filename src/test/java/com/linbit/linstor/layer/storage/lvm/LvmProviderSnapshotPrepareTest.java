package com.linbit.linstor.layer.storage.lvm;

import com.linbit.extproc.ExtCmdFactoryStlt;
import com.linbit.extproc.utils.TestExtCmd;
import com.linbit.extproc.utils.TestExtCmd.Command;
import com.linbit.extproc.utils.TestExtCmd.TestOutputData;
import com.linbit.fsevent.FileSystemWatch;
import com.linbit.linstor.api.ApiConsts;
import com.linbit.linstor.backupshipping.BackupShippingMgr;
import com.linbit.linstor.clone.CloneService;
import com.linbit.linstor.core.StltConfigAccessor;
import com.linbit.linstor.core.apicallhandler.StltExtToolsChecker;
import com.linbit.linstor.core.identifier.SnapshotName;
import com.linbit.linstor.core.identifier.VolumeNumber;
import com.linbit.linstor.core.objects.Resource;
import com.linbit.linstor.core.objects.Snapshot;
import com.linbit.linstor.core.objects.SnapshotDefinition;
import com.linbit.linstor.core.objects.SnapshotVolumeDefinition;
import com.linbit.linstor.core.objects.StorPool;
import com.linbit.linstor.core.objects.Volume;
import com.linbit.linstor.layer.DeviceLayer.NotificationListener;
import com.linbit.linstor.layer.drbd.DrbdInvalidateUtils;
import com.linbit.linstor.layer.storage.AbsStorageProvider.AbsStorageProviderInit;
import com.linbit.linstor.layer.storage.WipeHandler;
import com.linbit.linstor.layer.storage.lvm.utils.LvmUtils;
import com.linbit.linstor.propscon.ReadOnlyPropsImpl;
import com.linbit.linstor.security.GenericDbBase;
import com.linbit.linstor.storage.StorageConstants;
import com.linbit.linstor.storage.interfaces.categories.resource.VlmProviderObject;
import com.linbit.linstor.storage.kinds.DeviceLayerKind;
import com.linbit.linstor.storage.kinds.DeviceProviderKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the snapshot handling of {@link LvmProvider#prepare}, especially that a snapshot flagged for
 * deletion is NOT re-activated: activating a thick snapshot implicitly also activates its origin LV,
 * which the snapshot deleting "lvremove" would leave active - fatal for INACTIVE resources in shared
 * storage pools.
 */
public class LvmProviderSnapshotPrepareTest extends GenericDbBase
{
    private static final String LVS_COLUMNS =
        "lv_name,lv_path,lv_size,vg_name,pool_lv,data_percent,lv_attr,metadata_percent,chunk_size,stripes,origin";
    private static final String VGS_THICK_COLUMNS = "vg_name,vg_extent_size,vg_size,vg_free";
    private static final String IGNORE_DRBD_CONFIG = "devices { filter=[\"r|^/dev/drbd.*|\"] }";

    private static final String NODE_NAME_STR = "node";
    private static final String RSC_NAME_STR = "rsc";
    private static final String SNAP_NAME_STR = "snap";
    private static final String SP_NAME_STR = "lvmSp";
    private static final String RSC_LV_ID = "rsc_00000";
    private static final String SNAP_LV_ID = "rsc_00000_snap";
    private static final long VLM_SIZE_IN_KIB = 4096L;

    private TestExtCmd extCmd;
    private LvmProvider lvmProvider;

    private Snapshot snap;
    private VlmProviderObject<Snapshot> snapVlmData;
    /** Unique per test method since LvmUtils caches the lvm filter config statically per volume group set */
    private String vg;
    private String lvmConfig;

    @Before
    public void setUp() throws Exception
    {
        super.setUpAndEnterScope();

        extCmd = new TestExtCmd(errorReporter);
        LvmUtils.recacheNext();

        ExtCmdFactoryStlt extCmdFactoryStlt = Mockito.mock(ExtCmdFactoryStlt.class);
        Mockito.when(extCmdFactoryStlt.create()).thenReturn(extCmd);

        StltConfigAccessor stltCfgAccessor = Mockito.mock(StltConfigAccessor.class);
        Mockito.when(stltCfgAccessor.getReadonlyProps()).thenReturn(ReadOnlyPropsImpl.emptyRoProps());

        NotificationListener notificationListener = Mockito.mock(NotificationListener.class);
        lvmProvider = new LvmProvider(
            new AbsStorageProviderInit(
                errorReporter,
                extCmdFactoryStlt,
                stltCfgAccessor,
                Mockito.mock(WipeHandler.class),
                () -> notificationListener,
                transMgrProvider::get,
                Mockito.mock(StltExtToolsChecker.class),
                Mockito.mock(CloneService.class),
                Mockito.mock(BackupShippingMgr.class),
                Mockito.mock(FileSystemWatch.class),
                rscDfnMap,
                Mockito.mock(DrbdInvalidateUtils.class),
                remoteMap
            )
        );

        vg = "vg_" + testMethodName.getMethodName();

        Resource rsc = resourceTestFactory.builder(NODE_NAME_STR, RSC_NAME_STR)
            .setLayerStack(Collections.singletonList(DeviceLayerKind.STORAGE))
            .build();
        StorPool storPool = storPoolTestFactory.builder(NODE_NAME_STR, SP_NAME_STR)
            .setDriverKind(DeviceProviderKind.LVM)
            .build();
        storPool.getProps().setProp(
            StorageConstants.CONFIG_LVM_VOLUME_GROUP_KEY,
            vg,
            ApiConsts.NAMESPC_STORAGE_DRIVER
        );
        Volume vlm = volumeTestFactory.builder(NODE_NAME_STR, RSC_NAME_STR, 0)
            .setSize(VLM_SIZE_IN_KIB)
            .setStorPoolData(storPool)
            .build();
        rsc.getStateFlags().enableFlags(Resource.Flags.INACTIVE);

        SnapshotDefinition snapDfn = snapshotDefinitionFactory.create(
            rsc.getResourceDefinition(),
            new SnapshotName(SNAP_NAME_STR),
            new SnapshotDefinition.Flags[0]
        );
        SnapshotVolumeDefinition snapVlmDfn = snapshotVolumeDefinitionFactory.create(
            snapDfn,
            vlm.getVolumeDefinition(),
            VLM_SIZE_IN_KIB,
            new SnapshotVolumeDefinition.Flags[0]
        );
        snap = snapshotFactory.create(rsc, snapDfn, new Snapshot.Flags[0]);
        snapshotVolumeFactory.create(rsc, snap, snapVlmDfn);

        snapVlmData = snap.getLayerData().getVlmProviderObject(new VolumeNumber(0));
    }

    @Test
    public void prepareDoesNotActivateSnapshotFlaggedForDeletion() throws Exception
    {
        snap.getFlags().enableFlags(Snapshot.Flags.DELETE);
        expectPrepareCommands();

        // TestExtCmd fails on any unexpected command, i.e. also on an unexpected "lvchange -ay"
        lvmProvider.prepare(Collections.emptyList(), Collections.singletonList(snapVlmData));

        assertThat(extCmd.getUncalledCommands()).isEmpty();
        assertThat(snapVlmData.exists()).isTrue();
        assertThat(snapVlmData.getDevicePath()).isNull();
    }

    @Test
    public void prepareReactivatesKnownInactiveSnapshot() throws Exception
    {
        // without the DELETE flag a known but inactive snapshot has to be re-activated (e.g. after a
        // satellite reboot) since snapshot restore and backup shipping require an active snapshot LV
        expectPrepareCommands();
        expect(lvchangeActivateCommand(SNAP_LV_ID), "");

        lvmProvider.prepare(Collections.emptyList(), Collections.singletonList(snapVlmData));

        assertThat(extCmd.getUncalledCommands()).isEmpty();
        assertThat(snapVlmData.exists()).isTrue();
    }

    private void expectPrepareCommands()
    {
        expect(pvDisplayCommand(), "  /dev/vdz1\n");
        lvmConfig = LvmUtils.getLvmFilterByPhysicalVolumes("/dev/vdz1");
        expect(
            lvsCommand(Collections.singleton(vg)),
            "  " + RSC_LV_ID + ";/dev/" + vg + "/" + RSC_LV_ID + ";" + VLM_SIZE_IN_KIB + ".00;" + vg +
                ";;;-wi-------;;0;1;\n" +
                "  " + SNAP_LV_ID + ";/dev/" + vg + "/" + SNAP_LV_ID + ";" + VLM_SIZE_IN_KIB + ".00;" + vg +
                ";;;swi---s--k;;0;1;" + RSC_LV_ID + "\n"
        );
        expect(
            vgsCommand(Collections.singleton(vg)),
            "  " + vg + ";4096.00;104857600.00;104857600.00\n"
        );
    }

    private void expect(String[] argv, String stdOut)
    {
        extCmd.setExpectedBehavior(new Command(argv), new TestOutputData(argv, stdOut, "", 0));
    }

    private String[] pvDisplayCommand()
    {
        return new String[]
        {
            "pvdisplay",
            "--config", IGNORE_DRBD_CONFIG,
            "--columns",
            "-o", "pv_name",
            "-S", "vg_name=" + vg,
            "--noheadings",
            "--nosuffix"
        };
    }

    private String[] lvsCommand(Set<String> volumeGroups)
    {
        List<String> cmd = new ArrayList<>();
        cmd.add("lvs");
        cmd.add("--config");
        cmd.add(lvmConfig);
        Collections.addAll(
            cmd,
            "-o", LVS_COLUMNS,
            "--separator", ";",
            "--noheadings",
            "--units", "k",
            "--nosuffix"
        );
        cmd.addAll(volumeGroups);
        return cmd.toArray(new String[0]);
    }

    private String[] vgsCommand(Set<String> volumeGroups)
    {
        List<String> cmd = new ArrayList<>();
        cmd.add("vgs");
        cmd.add("--config");
        cmd.add(lvmConfig);
        Collections.addAll(
            cmd,
            "-o", VGS_THICK_COLUMNS,
            "--separator", ";",
            "--units", "k",
            "--noheadings",
            "--nosuffix"
        );
        cmd.addAll(volumeGroups);
        return cmd.toArray(new String[0]);
    }

    private String[] lvchangeActivateCommand(String lvId)
    {
        return new String[]
        {
            "lvchange",
            "--config", lvmConfig,
            "-ay",
            "-K",
            "-y",
            vg + "/" + lvId
        };
    }
}
