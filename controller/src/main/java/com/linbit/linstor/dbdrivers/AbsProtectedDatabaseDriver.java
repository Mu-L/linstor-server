package com.linbit.linstor.dbdrivers;

import com.linbit.linstor.annotation.Nullable;
import com.linbit.linstor.logging.ErrorReporter;

public abstract class AbsProtectedDatabaseDriver<DATA extends Comparable<? super DATA>, INIT_MAPS, LOAD_ALL>
    extends AbsDatabaseDriver<DATA, INIT_MAPS, LOAD_ALL>
{
    private final ObjectProtectionFactory objProtFactory;

    protected AbsProtectedDatabaseDriver(
        ErrorReporter errorReporterRef,
        @Nullable DatabaseTable tableRef,
        DbEngine dbEngineRef,
        ObjectProtectionFactory objProtFactoryRef
    )
    {
        super(errorReporterRef, tableRef, dbEngineRef);

        objProtFactory = objProtFactoryRef;
    }

    protected ObjectProtection getObjectProtection(String objProtPath) throws DatabaseException
    {
        return objProtFactory.getInstance(objProtPath, false);
    }

}
