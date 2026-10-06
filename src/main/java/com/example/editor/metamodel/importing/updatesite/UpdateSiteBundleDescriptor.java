package com.example.editor.metamodel.importing.updatesite;

import java.util.List;
import java.util.Map;

/** Bundle OSGi clasificado por su función dentro de la aplicación Eclipse. */
public record UpdateSiteBundleDescriptor(String id, String version, String name, UpdateSiteBundleKind kind,
        String sourcePath, List<String> classificationSignals, List<UpdateSiteDependency> dependencies,
        List<UpdateSiteArtifactDescriptor> artifacts, Map<String, String> manifestHeaders) {
    public UpdateSiteBundleDescriptor {
        id = id == null ? "" : id;
        version = version == null ? "" : version;
        name = name == null || name.isBlank() ? id : name;
        kind = kind == null ? UpdateSiteBundleKind.THIRD_PARTY_DEPENDENCY : kind;
        sourcePath = sourcePath == null ? "" : sourcePath;
        classificationSignals = classificationSignals == null ? List.of() : List.copyOf(classificationSignals);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        manifestHeaders = manifestHeaders == null ? Map.of() : Map.copyOf(manifestHeaders);
    }
}
