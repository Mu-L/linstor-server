package com.linbit.linstor.core.repository;

import com.linbit.linstor.annotation.Nullable;
import com.linbit.linstor.core.LinStor;
import com.linbit.linstor.dbdrivers.DatabaseException;
import com.linbit.linstor.propscon.InvalidKeyException;
import com.linbit.linstor.propscon.InvalidValueException;
import com.linbit.linstor.propscon.Props;
import com.linbit.linstor.propscon.ReadOnlyProps;
import com.linbit.linstor.propscon.ReadOnlyPropsImpl;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Holds the singleton system props protection instance, allowing it to be initialized from the database
 * after dependency injection has been performed.
 */
@Singleton
public class SystemConfProtectionRepository implements SystemConfRepository
{
    private final Props ctrlConf;
    private final Props stltConf;

    // can't initialize objProt in constructor because of chicken-egg-problem
    @SuppressFBWarnings("NP_NONNULL_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR")
    @Inject
    public SystemConfProtectionRepository(
        @Named(LinStor.CONTROLLER_PROPS) Props ctrlConfRef,
        @Named(LinStor.SATELLITE_PROPS) Props stltConfRef
    )
    {
        ctrlConf = ctrlConfRef;
        stltConf = stltConfRef;
    }

    public void setObjectProtection()
    {
        if (objectProtection != null)
        {
            throw new IllegalStateException("Object protection already set");
        }
    }

    @Override
    public void requireAccess(AccessType requested)
    {
        checkProtSet();
    }

    @Override
    public @Nullable String setCtrlProp(String key, String value, String namespace)
        throws InvalidValueException, DatabaseException, InvalidKeyException
    {
        checkProtSet();
        return ctrlConf.setProp(key, value, namespace);
    }

    @Override
    public @Nullable String setStltProp(String key, String value)
        throws InvalidValueException, InvalidKeyException, DatabaseException
    {
        checkProtSet();
        return stltConf.setProp(key, value);
    }

    @Override
    public @Nullable String removeCtrlProp(String key, String namespace)
        throws InvalidKeyException, DatabaseException
    {
        checkProtSet();
        return ctrlConf.removeProp(key, namespace);
    }

    @Override
    public @Nullable String removeStltProp(String key, String namespace)
        throws InvalidKeyException, DatabaseException
    {
        checkProtSet();
        return stltConf.removeProp(key, namespace);
    }

    @Override
    public ReadOnlyProps getCtrlConfForView()
    {
        checkProtSet();
        return new ReadOnlyPropsImpl(ctrlConf);
    }

    @Override
    public Props getCtrlConfForChange()
    {
        checkProtSet();
        return ctrlConf;
    }

    @Override
    public ReadOnlyProps getStltConfForView()
    {
        checkProtSet();
        return new ReadOnlyPropsImpl(stltConf);
    }

    private void checkProtSet()
    {
        if (objectProtection == null)
        {
            throw new IllegalStateException("Object protection not yet set");
        }
    }
}
