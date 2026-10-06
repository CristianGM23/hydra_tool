package com.example.editor.artifact.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

/** Evalúa compatibilidad de dependencias sin confundir disponibilidad física con compatibilidad semántica. */
@Service
public class HydraArtifactCompatibilityService {
    /** Descripción: Evalúa un artefacto contra el snapshot actual del registro. Entradas: snapshot: registro; artifactId: artefacto objetivo. Salida: estado de compatibilidad y diagnósticos. */
    public HydraArtifactCompatibilityReport evaluate(HydraArtifactRegistrySnapshot snapshot, String artifactId) {
        Map<String, HydraArtifactDescriptor> byId = new LinkedHashMap<>();
        if (snapshot != null) snapshot.artifacts().forEach(item -> byId.put(item.id(), item));
        HydraArtifactDescriptor artifact = byId.get(artifactId);
        if (artifact == null) throw new IllegalArgumentException("Artifact was not found in registry: " + artifactId);
        HydraArtifactCompatibilityStatus status = HydraArtifactCompatibilityStatus.COMPATIBLE;
        List<String> diagnostics = new ArrayList<>();
        for (HydraArtifactDependency dependency : artifact.dependencies()) {
            HydraArtifactDescriptor target = byId.get(dependency.artifactId());
            if (target == null) {
                if (dependency.required()) {
                    status = HydraArtifactCompatibilityStatus.INCOMPATIBLE;
                    diagnostics.add("Missing required dependency: " + dependency.artifactId());
                }
                continue;
            }
            if (!HydraArtifactVersionPolicy.supports(target.version(), dependency.minimumVersion())) {
                if (status != HydraArtifactCompatibilityStatus.INCOMPATIBLE) status = HydraArtifactCompatibilityStatus.MIGRATION_REQUIRED;
                diagnostics.add("Dependency " + dependency.artifactId() + " is version " + target.version()
                        + " but requires at least " + dependency.minimumVersion());
            }
            if (target.status() == HydraArtifactStatus.STALE && status == HydraArtifactCompatibilityStatus.COMPATIBLE) {
                status = HydraArtifactCompatibilityStatus.REVALIDATION_REQUIRED;
                diagnostics.add("Dependency requires revalidation because it is stale: " + target.id());
            }
            if (target.status() == HydraArtifactStatus.INVALID || target.status() == HydraArtifactStatus.CONFLICTED
                    || target.status() == HydraArtifactStatus.MISSING_DEPENDENCY || target.status() == HydraArtifactStatus.INCOMPATIBLE_DEPENDENCY) {
                status = HydraArtifactCompatibilityStatus.INCOMPATIBLE;
                diagnostics.add("Dependency is not usable: " + target.id() + " (" + target.status() + ")");
            }
        }
        return new HydraArtifactCompatibilityReport(artifactId, status, diagnostics);
    }
}
