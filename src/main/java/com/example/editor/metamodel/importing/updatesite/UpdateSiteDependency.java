package com.example.editor.metamodel.importing.updatesite;

/** Dependencia declarada por un feature, una unidad p2 o un bundle OSGi. */
public record UpdateSiteDependency(String sourceId, String targetId, String versionRange, boolean optional,
        boolean resolved, String origin) {
    public UpdateSiteDependency {
        sourceId = sourceId == null ? "" : sourceId;
        targetId = targetId == null ? "" : targetId;
        versionRange = versionRange == null ? "" : versionRange;
        origin = origin == null ? "" : origin;
    }
}
