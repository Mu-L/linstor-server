package com.linbit.linstor.dbdrivers.interfaces;

import com.linbit.linstor.dbdrivers.ControllerDatabaseDriver;
import com.linbit.linstor.dbdrivers.interfaces.SecObjProtCtrlDatabaseDriver.SecObjProtInitObj;
import com.linbit.linstor.dbdrivers.interfaces.SecObjProtCtrlDatabaseDriver.SecObjProtParent;

import java.util.Map;

public interface SecObjProtCtrlDatabaseDriver extends SecObjProtDatabaseDriver,
    ControllerDatabaseDriver<ObjectProtection, SecObjProtInitObj, SecObjProtParent>
{
    class SecObjProtInitObj
    {
        private final String objPath;
        private final Map<RoleName, AccessControlEntry> aclBackingMap;

        public SecObjProtInitObj(String objPathRef, Map<RoleName, AccessControlEntry> aclBackingMapRef)
        {
            objPath = objPathRef;
            aclBackingMap = aclBackingMapRef;
        }

        public String getObjPath()
        {
            return objPath;
        }

        public Map<RoleName, AccessControlEntry> getAclBackingMap()
        {
            return aclBackingMap;
        }
    }

    class SecObjProtParent
    {
        public final Map<RoleName, Role> roles;
        public final Map<IdentityName, Identity> ids;
        public final Map<SecTypeName, SecurityType> secTypes;

        public SecObjProtParent(
            Map<RoleName, Role> rolesRef,
            Map<IdentityName, Identity> idsRef,
            Map<SecTypeName, SecurityType> secTypesRef
        )
        {
            roles = rolesRef;
            ids = idsRef;
            secTypes = secTypesRef;
        }
    }
}
