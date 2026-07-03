package com.linbit.linstor.core.repository;

import com.linbit.linstor.annotation.Nullable;
import com.linbit.linstor.core.CoreModule;
import com.linbit.linstor.core.identifier.ExternalFileName;
import com.linbit.linstor.core.objects.ExternalFile;

public interface ExternalFileRepository extends ProtectedObject
{
    void requireAccess(AccessType requested);

    @Nullable
    ExternalFile get(ExternalFileName externalFileName);

    void put(ExternalFile externalFile);

    void remove(ExternalFileName externalFileName);

    CoreModule.ExternalFileMap getMapForView();
}
