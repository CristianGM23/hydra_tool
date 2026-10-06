package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Vista segura del repositorio p2 analizado. No expone el contenido binario. */
public record UpdateSiteRepositorySummary(String name, String sourceFingerprint, boolean contentMetadataFound,
        boolean artifactMetadataFound, int repositoryFileCount, long repositorySizeBytes,
        List<UpdateSiteFeatureDescriptor> features, List<UpdateSiteBundleDescriptor> bundles,
        List<UpdateSiteArtifactDescriptor> unownedArtifacts, List<UpdateSiteDependency> missingDependencies,
        List<List<String>> dependencyCycles, List<String> diagnostics) {
    public UpdateSiteRepositorySummary {
        name = name == null || name.isBlank() ? "Eclipse update site" : name;
        sourceFingerprint = sourceFingerprint == null ? "" : sourceFingerprint;
        features = features == null ? List.of() : List.copyOf(features);
        bundles = bundles == null ? List.of() : List.copyOf(bundles);
        unownedArtifacts = unownedArtifacts == null ? List.of() : List.copyOf(unownedArtifacts);
        missingDependencies = missingDependencies == null ? List.of() : List.copyOf(missingDependencies);
        dependencyCycles = dependencyCycles == null ? List.of() : dependencyCycles.stream().map(List::copyOf).toList();
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
