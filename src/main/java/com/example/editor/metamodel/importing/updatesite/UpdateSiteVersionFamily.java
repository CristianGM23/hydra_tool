package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Versiones detectadas para una feature o bundle. */
public record UpdateSiteVersionFamily(String id, String artifactType, List<String> versions,
        String oldestVersion, String latestVersion, boolean multipleVersions, String recommendation) {
    public UpdateSiteVersionFamily {
        id = id == null ? "" : id;
        artifactType = artifactType == null ? "" : artifactType;
        versions = versions == null ? List.of() : List.copyOf(versions);
        oldestVersion = oldestVersion == null ? "" : oldestVersion;
        latestVersion = latestVersion == null ? "" : latestVersion;
        recommendation = recommendation == null ? "" : recommendation;
    }
}
