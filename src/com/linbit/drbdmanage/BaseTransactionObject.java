package com.linbit.drbdmanage;

import java.sql.Connection;
import java.util.List;

import com.linbit.ImplementationError;
import com.linbit.TransactionMgr;
import com.linbit.TransactionObject;

public abstract class BaseTransactionObject implements TransactionObject
{
    private boolean initialized = false;
    protected List<TransactionObject> transObjs;
    protected Connection dbCon;

    private boolean inCommit = false;
    private boolean inRollback = false;

    @Override
    public void initialized()
    {
        if (!initialized)
        {
            initialized = true;
            for(TransactionObject transObj : transObjs)
            {
                transObj.initialized();
            }
        }
    }

    @Override
    public void setConnection(TransactionMgr transMgr) throws ImplementationError
    {
        if (transMgr != null)
        {
            transMgr.register(this);
            dbCon = transMgr.dbCon;
        }
        else
        {
            dbCon = null;
        }
        for (TransactionObject transObj : transObjs)
        {
            transObj.setConnection(transMgr);
        }
    }

    @Override
    public void commit()
    {
        if (!inCommit)
        {
            inCommit = true;
            for (TransactionObject transObj : transObjs)
            {
                if (transObj.isDirty())
                {
                    transObj.commit();
                }
            }
            inCommit = false;
        }
    }

    @Override
    public void rollback()
    {
        if (!inRollback)
        {
            inRollback = true;
            for (TransactionObject transObj : transObjs)
            {
                if (transObj.isDirty())
                {
                    transObj.rollback();
                }
            }
            inRollback = false;
        }
    }

    @Override
    public boolean isDirty()
    {
        boolean dirty = false;
        for (TransactionObject transObj : transObjs)
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
