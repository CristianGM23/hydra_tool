package com.example.editor.standalone.distribution;

import java.nio.file.Path;
import java.util.List;

/** Deterministic ZIP package metadata. */
public record StandaloneDistributionPackageResult(Path archive, List<String> entries, String sha256) {
    public StandaloneDistributionPackageResult {
        if(archive == null){throw new IllegalArgumentException("archive is required");}
        archive = archive.toAbsolutePath().normalize();
        entries = entries == null ? List.of() : List.copyOf(entries);
        if(sha256 == null || sha256.isBlank()){throw new IllegalArgumentException("sha256 is required");}
    }
}
