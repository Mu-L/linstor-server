package com.linbit.linstor.core.objects;

import com.linbit.ImplementationError;
import com.linbit.linstor.core.identifier.SnapshotName;
import com.linbit.linstor.dbdrivers.interfaces.SnapshotDefinitionDatabaseDriver;
import com.linbit.linstor.propscon.PropsContainerFactory;
import com.linbit.linstor.stateflags.StateFlagsBits;
import com.linbit.linstor.transaction.TransactionObjectFactory;
import com.linbit.linstor.transaction.manager.TransactionMgr;

import javax.inject.Inject;
import javax.inject.Provider;

import java.util.TreeMap;
import java.util.UUID;

public class SnapshotDefinitionSatelliteFactory
{
    private final SnapshotDefinitionDatabaseDriver driver;
    private final PropsContainerFactory propsContainerFactory;
    private final TransactionObjectFactory transObjFactory;
    private final Provider<TransactionMgr> transMgrProvider;
    private final ObjectProtectionFactory objectProtectionFactory;

    @Inject
    public SnapshotDefinitionSatelliteFactory(
        SnapshotDefinitionDatabaseDriver driverRef,
        ObjectProtectionFactory objectProtectionFactoryRef,
        PropsContainerFactory propsContainerFactoryRef,
        TransactionObjectFactory transObjFactoryRef,
        Provider<TransactionMgr> transMgrProviderRef
    )
    {
        driver = driverRef;
        objectProtectionFactory = objectProtectionFactoryRef;
        propsContainerFactory = propsContainerFactoryRef;
        transObjFactory = transObjFactoryRef;
        transMgrProvider = transMgrProviderRef;
    }

    public SnapshotDefinition getInstanceSatellite(
        UUID snapshotDfnUuid,
        ResourceDefinition rscDfn,
        SnapshotName snapshotName,
        SnapshotDefinition.Flags[] flags
    )
        throws ImplementationError
    {
        SnapshotDefinition snapshotDfnData;
        try
        {
            snapshotDfnData = rscDfn.getSnapshotDfn(snapshotName);
            if (snapshotDfnData == null)
            {
                snapshotDfnData = new SnapshotDefinition(
                    snapshotDfnUuid,
                    objectProtectionFactory.getInstance("", false),
                    rscDfn,
                    snapshotName,
                    StateFlagsBits.getMask(flags),
                    driver,
                    transObjFactory,
                    propsContainerFactory,
                    transMgrProvider,
                    new TreeMap<>(),
                    new TreeMap<>(),
                    new TreeMap<>()
                );
                rscDfn.addSnapshotDfn(snapshotDfnData);
            }
        }
        catch (Exception exc)
        {
            throw new ImplementationError(
                "This method should only be called with a satellite db in background!",
                exc
            );
        }
        return snapshotDfnData;
    }
}
