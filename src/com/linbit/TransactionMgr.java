package com.linbit;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;

import com.linbit.drbdmanage.dbcp.DbConnectionPool;

public class TransactionMgr
{
    public final Connection dbCon;
    private Set<TransactionObject> transObjects;

    public TransactionMgr(DbConnectionPool dbConnPool) throws SQLException
    {
        this(dbConnPool.getConnection());
    }

    public TransactionMgr(Connection con) throws SQLException
    {
        con.setAutoCommit(false);
        dbCon = con;
        transObjects = new LinkedHashSet<>(); // preserves the order but removes duplicates
    }

    public void register(TransactionObject transObj)
    {
        if (transObj.isDbCacheDirty())
        {
            throw new ImplementationError(
                "Connection set after TransactionObject modified",
                null
            );
        }
        transObjects.add(transObj);
    }

    public void commit() throws SQLException
    {
        commit(false);
    }

    public void commit(boolean clearTransObjects) throws SQLException
    {
        dbCon.commit();
        for (TransactionObject transObj : transObjects)
        {
            // checking if isDirty to prevent endless indirect recursion
            if (transObj.isDirty())
            {
                transObj.commit();
            }
        }
        if (clearTransObjects)
        {
            clearTransactionObjects();
        }

        // if no SQLException happened so far
        for (TransactionObject transObj : transObjects)
        {
            // remove the active connection to force the next transaction to be explicit
            transObj.setConnection(null);
        }
    }


    public void rollback() throws SQLException
    {
        for (TransactionObject transObj : transObjects)
        {
            // checking if isDirty to prevent endless indirect recursion
            if (transObj.isDirty())
            {
                transObj.rollback();
            }
        }
        dbCon.rollback();
    }

    public void clearTransactionObjects()
    {
        transObjects.clear();
    }

    public boolean isDirty()
    {
        boolean dirty = false;
        for (TransactionObject transObj : transObjects)
        {
            if (transObj.isDirty())
            {
                dirty = true;
                break;
            }
        }
        return dirty;
    }
}
