package com.linbit.linstor.core.apicallhandler.controller.db;

import com.linbit.linstor.annotation.Nullable;
import com.linbit.linstor.dbdrivers.DatabaseTable.Column;
import com.linbit.linstor.dbdrivers.GeneratedDatabaseTables;
import com.linbit.linstor.dbdrivers.k8s.crd.GenCrdCurrent.ResourceDefinitionsSpec;
import com.linbit.linstor.dbdrivers.k8s.crd.LinstorSpec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the reordering of self-referencing tables (currently only RESOURCE_DEFINITIONS, where a
 * snapshot-definition references its resource-definition via PARENT_UUID -> UUID) so that referenced entries
 * are always imported before the entries referencing them.
 */
public class DbExportImportHelperTest
{
    private static final String RD_A_UUID = "aaaaaaaa-0000-0000-0000-000000000000";
    private static final String RD_B_UUID = "bbbbbbbb-0000-0000-0000-000000000000";
    private static final String SNAP_1_UUID = "11111111-0000-0000-0000-000000000000";
    private static final String SNAP_2_UUID = "22222222-0000-0000-0000-000000000000";

    @Test
    public void snapDfnBeforeitsRscDfnGetsReordered()
    {
        DbExportPojoData.Table tbl = rscDfnTable(
            snapDfn(SNAP_1_UUID, "rsc-a", "snap1", RD_A_UUID),
            rscDfn(RD_A_UUID, "rsc-a")
        );

        DbExportImportHelper.reorderSelfReferencingEntries(tbl, GeneratedDatabaseTables.SELF_REFERENCING_FOREIGN_KEYS);

        assertThat(uuids(tbl)).containsExactly(RD_A_UUID, SNAP_1_UUID);
    }

    @Test
    public void alreadyValidOrderIsKept()
    {
        DbExportPojoData.Table tbl = rscDfnTable(
            rscDfn(RD_A_UUID, "rsc-a"),
            snapDfn(SNAP_1_UUID, "rsc-a", "snap1", RD_A_UUID),
            rscDfn(RD_B_UUID, "rsc-b"),
            snapDfn(SNAP_2_UUID, "rsc-b", "snap2", RD_B_UUID)
        );

        DbExportImportHelper.reorderSelfReferencingEntries(tbl, GeneratedDatabaseTables.SELF_REFERENCING_FOREIGN_KEYS);

        assertThat(uuids(tbl)).containsExactly(RD_A_UUID, SNAP_1_UUID, RD_B_UUID, SNAP_2_UUID);
    }

    @Test
    public void interleavedSnapDfnsEndUpAfterTheirRscDfns()
    {
        DbExportPojoData.Table tbl = rscDfnTable(
            snapDfn(SNAP_1_UUID, "rsc-a", "snap1", RD_A_UUID),
            rscDfn(RD_B_UUID, "rsc-b"),
            snapDfn(SNAP_2_UUID, "rsc-b", "snap2", RD_B_UUID),
            rscDfn(RD_A_UUID, "rsc-a")
        );

        DbExportImportHelper.reorderSelfReferencingEntries(tbl, GeneratedDatabaseTables.SELF_REFERENCING_FOREIGN_KEYS);

        List<String> uuids = uuids(tbl);
        assertThat(uuids).containsExactlyInAnyOrder(RD_A_UUID, RD_B_UUID, SNAP_1_UUID, SNAP_2_UUID);
        assertThat(uuids.indexOf(SNAP_1_UUID)).isGreaterThan(uuids.indexOf(RD_A_UUID));
        assertThat(uuids.indexOf(SNAP_2_UUID)).isGreaterThan(uuids.indexOf(RD_B_UUID));
    }

    @Test
    public void danglingParentReferenceIsNotDropped()
    {
        DbExportPojoData.Table tbl = rscDfnTable(
            snapDfn(SNAP_1_UUID, "rsc-a", "snap1", "deadbeef-0000-0000-0000-000000000000"),
            rscDfn(RD_A_UUID, "rsc-a")
        );

        DbExportImportHelper.reorderSelfReferencingEntries(tbl, GeneratedDatabaseTables.SELF_REFERENCING_FOREIGN_KEYS);

        assertThat(uuids(tbl)).containsExactlyInAnyOrder(SNAP_1_UUID, RD_A_UUID);
    }

    @Test
    public void exportWithoutParentUuidColumnStaysUntouched()
    {
        List<DbExportPojoData.Column> clmsWithoutParentUuid = new ArrayList<>();
        for (Column clm : GeneratedDatabaseTables.RESOURCE_DEFINITIONS.values())
        {
            if (!clm.getName().equals(GeneratedDatabaseTables.ResourceDefinitions.PARENT_UUID.getName()))
            {
                clmsWithoutParentUuid.add(
                    new DbExportPojoData.Column(clm.getName(), clm.getSqlType(), clm.isPk(), clm.isNullable())
                );
            }
        }
        DbExportPojoData.Table tbl = new DbExportPojoData.Table(
            GeneratedDatabaseTables.RESOURCE_DEFINITIONS.getName(),
            clmsWithoutParentUuid,
            new ArrayList<>(
                Arrays.asList(
                    snapDfn(SNAP_1_UUID, "rsc-a", "snap1", RD_A_UUID),
                    rscDfn(RD_A_UUID, "rsc-a")
                )
            ),
            null
        );

        DbExportImportHelper.reorderSelfReferencingEntries(tbl, GeneratedDatabaseTables.SELF_REFERENCING_FOREIGN_KEYS);

        assertThat(uuids(tbl)).containsExactly(SNAP_1_UUID, RD_A_UUID);
    }

    @Test
    public void otherTablesStayUntouched()
    {
        DbExportPojoData.Table tbl = new DbExportPojoData.Table(
            GeneratedDatabaseTables.NODES.getName(),
            new ArrayList<>(),
            new ArrayList<>(
                Arrays.asList(
                    snapDfn(SNAP_1_UUID, "rsc-a", "snap1", RD_A_UUID),
                    rscDfn(RD_A_UUID, "rsc-a")
                )
            ),
            null
        );

        DbExportImportHelper.reorderSelfReferencingEntries(tbl, GeneratedDatabaseTables.SELF_REFERENCING_FOREIGN_KEYS);

        assertThat(uuids(tbl)).containsExactly(SNAP_1_UUID, RD_A_UUID);
    }

    @Test
    public void cyclicReferencesAreKeptInsteadOfDropped()
    {
        // cannot occur with real data, but a malformed export must not lose entries silently
        DbExportPojoData.Table tbl = rscDfnTable(
            snapDfn(SNAP_1_UUID, "rsc-a", "snap1", SNAP_2_UUID),
            snapDfn(SNAP_2_UUID, "rsc-a", "snap2", SNAP_1_UUID),
            rscDfn(RD_A_UUID, "rsc-a")
        );

        DbExportImportHelper.reorderSelfReferencingEntries(tbl, GeneratedDatabaseTables.SELF_REFERENCING_FOREIGN_KEYS);

        assertThat(uuids(tbl)).containsExactlyInAnyOrder(RD_A_UUID, SNAP_1_UUID, SNAP_2_UUID);
    }

    private DbExportPojoData.Table rscDfnTable(LinstorSpec<?, ?>... specs)
    {
        List<DbExportPojoData.Column> clmDescrList = new ArrayList<>();
        for (Column clm : GeneratedDatabaseTables.RESOURCE_DEFINITIONS.values())
        {
            clmDescrList.add(
                new DbExportPojoData.Column(clm.getName(), clm.getSqlType(), clm.isPk(), clm.isNullable())
            );
        }
        return new DbExportPojoData.Table(
            GeneratedDatabaseTables.RESOURCE_DEFINITIONS.getName(),
            clmDescrList,
            new ArrayList<>(Arrays.asList(specs)),
            null
        );
    }

    private LinstorSpec<?, ?> rscDfn(String uuid, String rscName)
    {
        return rscDfnSpec(uuid, rscName, "", null);
    }

    private LinstorSpec<?, ?> snapDfn(String uuid, String rscName, String snapName, String parentUuid)
    {
        return rscDfnSpec(uuid, rscName, snapName, parentUuid);
    }

    private LinstorSpec<?, ?> rscDfnSpec(
        String uuid,
        String rscName,
        String snapName,
        @Nullable String parentUuid
    )
    {
        return new ResourceDefinitionsSpec(
            uuid,
            rscName.toUpperCase(),
            snapName.toUpperCase(),
            rscName,
            snapName,
            0L,
            "",
            null,
            "DfltRscGrp",
            parentUuid
        );
    }

    private List<String> uuids(DbExportPojoData.Table tblRef)
    {
        List<String> ret = new ArrayList<>();
        for (LinstorSpec<?, ?> spec : tblRef.data)
        {
            ret.add((String) spec.getByColumn(GeneratedDatabaseTables.ResourceDefinitions.UUID.getName()));
        }
        return ret;
    }
}
