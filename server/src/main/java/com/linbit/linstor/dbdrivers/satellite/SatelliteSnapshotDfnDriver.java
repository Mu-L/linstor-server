package com.linbit.linstor.dbdrivers.satellite;

import com.linbit.SingleColumnDatabaseDriver;
import com.linbit.linstor.annotation.SystemContext;
import com.linbit.linstor.core.objects.SnapshotDefinitionData;
import com.linbit.linstor.dbdrivers.interfaces.SnapshotDefinitionDataDatabaseDriver;
import com.linbit.linstor.security.AccessContext;
import com.linbit.linstor.stateflags.StateFlagsPersistence;

import javax.inject.Inject;

public class SatelliteSnapshotDfnDriver implements SnapshotDefinitionDataDatabaseDriver
{
    private final StateFlagsPersistence<?> stateFlagsDriver = new SatelliteFlagDriver();
    private final SingleColumnDatabaseDriver<?, ?> singleColDriver = new SatelliteSingleColDriver<>();
    private final AccessContext dbCtx;

    @Inject
    public SatelliteSnapshotDfnDriver(@SystemContext AccessContext dbCtxRef)
    {
        dbCtx = dbCtxRef;
    }

    @SuppressWarnings("unchecked")
    @Override
    public StateFlagsPersistence<SnapshotDefinitionData> getStateFlagsPersistence()
    {
        return (StateFlagsPersistence<SnapshotDefinitionData>) stateFlagsDriver;
    }

    @Override
    public void create(SnapshotDefinitionData snapshotDefinition)
    {
        // no-op
    }

    @Override
    public void delete(SnapshotDefinitionData snapshotDefinition)
    {
        // no-op
    }
}
