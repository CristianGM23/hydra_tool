package com.example.editor.recovery;

import java.time.Instant;
import java.util.List;

/** Reproducible local backup manifest. */
public record HydraRecoveryBackupManifest(String backupId,Instant createdAt,String archivePath,long totalBytes,
        List<HydraRecoveryFile> files,String manifestSha256,boolean verified) {
    public HydraRecoveryBackupManifest {createdAt=createdAt==null?Instant.now():createdAt;files=files==null?List.of():List.copyOf(files);}
}
