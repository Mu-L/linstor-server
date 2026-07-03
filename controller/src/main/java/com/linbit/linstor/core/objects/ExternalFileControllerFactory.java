package com.linbit.linstor.core.objects;

import com.linbit.linstor.LinStorDataAlreadyExistsException;
import com.linbit.linstor.annotation.Nullable;
import com.linbit.linstor.core.identifier.ExternalFileName;
import com.linbit.linstor.core.repository.ExternalFileRepository;
import com.linbit.linstor.dbdrivers.DatabaseException;
import com.linbit.linstor.dbdrivers.interfaces.ExternalFileDatabaseDriver;
import com.linbit.linstor.transaction.TransactionObjectFactory;
import com.linbit.linstor.transaction.manager.TransactionMgr;
import com.linbit.linstor.utils.ByteUtils;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Singleton
public class ExternalFileControllerFactory
{
    private final ExternalFileDatabaseDriver dbDriver;
    private final ObjectProtectionFactory objProtFactory;
    private final TransactionObjectFactory transObjFactory;
    private final Provider<TransactionMgr> transMgrProvider;
    private final ExternalFileRepository extFileRepo;

    @Inject
    public ExternalFileControllerFactory(
        ExternalFileDatabaseDriver dbDriverRef,
        ObjectProtectionFactory objProtFactoryRef,
        TransactionObjectFactory transObjFactoryRef,
        Provider<TransactionMgr> transMgrProviderRef,
        ExternalFileRepository extFileRepoRef
    )
    {
        dbDriver = dbDriverRef;
        objProtFactory = objProtFactoryRef;
        transObjFactory = transObjFactoryRef;
        transMgrProvider = transMgrProviderRef;
        extFileRepo = extFileRepoRef;
    }

    public ExternalFile create(
        ExternalFileName nameRef,
        byte[] contentRef,
        @Nullable List<String> altSuffixesRef
    )
        throws LinStorDataAlreadyExistsException, DatabaseException
    {
        ExternalFile extFile = extFileRepo.get(nameRef);
        if (extFile != null)
        {
            throw new LinStorDataAlreadyExistsException("This external file name is already registered");
        }

        extFile = new ExternalFile(
            UUID.randomUUID(),
            objProtFactory.getInstance(
                ObjectProtection.buildPath(nameRef),
                true
            ),
            nameRef,
            0,
            contentRef,
            ByteUtils.checksumSha256(contentRef),
            altSuffixesRef != null ? altSuffixesRef : Collections.emptyList(),
            dbDriver,
            transObjFactory,
            transMgrProvider
        );

        dbDriver.create(extFile);

        return extFile;
    }
}
