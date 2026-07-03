package com.linbit.linstor.dbdrivers.interfaces;

import com.linbit.linstor.dbdrivers.interfaces.updater.SingleColumnDatabaseDriver;

/**
 * Database driver for {@link ObjectProtection}.
 *
 * @author Gabor Hernadi &lt;gabor.hernadi@linbit.com&gt;
 */
public interface SecObjProtDatabaseDriver extends GenericDatabaseDriver<ObjectProtection>
{
    SingleColumnDatabaseDriver<ObjectProtection, Role> getOwnerRoleDriver();

    SingleColumnDatabaseDriver<ObjectProtection, Identity> getCreatorIdentityDriver();

    SingleColumnDatabaseDriver<ObjectProtection, SecurityType> getSecurityTypeDriver();
}
