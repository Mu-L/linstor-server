package com.linbit.linstor.core.apicallhandler.controller.backup.l2l.rest;

import com.linbit.linstor.api.pojo.backups.BackupMetaDataPojo;

import javax.annotation.Nullable;

import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.sentry.util.Objects;

/**
 * Initial request sent from the source Linstor cluster to the target Linstor cluster.
 * The target Linstor cluster needs to verify if the shipping can be accepted. That means:
 * * Does the target node exist
 * * Does the target node have the requested storage pool
 * * Does the target sp have
 * * * enough space
 * * * compatible provider type (lvm/zfs/...)
 *
 * This request is expected to be answered with {@link BackupShippingResponse}
 */
public class BackupShippingRequest
{
    public final int[] srcVersion;
    public final String dstRscName;
    public final Map<String, String> storPoolRenameMap;
    public final BackupMetaDataPojo metaData;
    public final String srcBackupName;

    public final @Nullable String dstNodeName;
    public final @Nullable String dstNodeNetIfName;
    public final @Nullable String dstStorPool;

    @JsonCreator
    public BackupShippingRequest(
        @JsonProperty("srcVersion") int[] srcVersionRef,
        @JsonProperty("metaData") BackupMetaDataPojo metaDataRef,
        @JsonProperty("srcBackupName") String srcBackupNameRef,
        @JsonProperty("dstRscName") String dstRscNameRef,
        @JsonProperty("dstNodeName") @Nullable String dstNodeNameRef,
        @JsonProperty("dstNodeNetIfName") @Nullable String dstNodeNetIfNameRef,
        @JsonProperty("dstStorPool") @Nullable String dstStorPoolRef,
        @JsonProperty("storPoolRenameMap") @Nullable Map<String, String> storPoolRenameMapRef
    )
    {
        srcVersion = Objects.requireNonNull(srcVersionRef, "Version must not be null!");
        dstRscName = Objects.requireNonNull(dstRscNameRef, "Target resource name must not be null!");
        srcBackupName = Objects.requireNonNull(srcBackupNameRef, "BackupName must not be null!");
        storPoolRenameMap = storPoolRenameMapRef == null ?
            Collections.emptyMap() :
            Collections.unmodifiableMap(storPoolRenameMapRef);
        metaData = Objects.requireNonNull(metaDataRef, "Metadata must not be null!");

        dstNodeName = dstNodeNameRef;
        dstNodeNetIfName = dstNodeNetIfNameRef;
        dstStorPool = dstStorPoolRef;
    }
}
