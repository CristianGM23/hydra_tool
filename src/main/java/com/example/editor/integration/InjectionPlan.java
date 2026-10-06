package com.example.editor.integration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;
/** Previsualización no ejecutada de archivos, dependencias, instalación, rollback y verificación. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record InjectionPlan(String schemaVersion, String id, String integrationProfileId,
        IntegrationTechnology targetTechnology, InjectionPlanStatus status, Instant createdAt,
        List<InjectionFileChange> fileChanges, List<IntegrationDependencyChange> dependencyChanges,
        List<String> installationSteps, List<String> rollbackSteps,
        List<IntegrationVerificationStep> verificationSteps, List<IntegrationDiagnostic> diagnostics,
        Map<String, String> annotations) {
    public InjectionPlan {
        if(schemaVersion == null || schemaVersion.isBlank()) schemaVersion = IntegrationContractVersions.INJECTION_PLAN;
        if(!IntegrationContractVersions.INJECTION_PLAN.equals(schemaVersion)) throw new IllegalArgumentException("Unsupported injection plan schemaVersion: " + schemaVersion);
        id = StableIdentifierPolicy.requireDocumentId(id, "id");
        integrationProfileId = StableIdentifierPolicy.requireDocumentId(integrationProfileId, "integrationProfileId");
        if(targetTechnology == null || status == null) throw new IllegalArgumentException("targetTechnology and status are required");
        if(createdAt == null) createdAt = Instant.now();
        fileChanges = fileChanges == null ? List.of() : List.copyOf(fileChanges);
        dependencyChanges = dependencyChanges == null ? List.of() : List.copyOf(dependencyChanges);
        installationSteps = installationSteps == null ? List.of() : List.copyOf(installationSteps);
        rollbackSteps = rollbackSteps == null ? List.of() : List.copyOf(rollbackSteps);
        verificationSteps = verificationSteps == null ? List.of() : List.copyOf(verificationSteps);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        annotations = annotations == null || annotations.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
        if(status == InjectionPlanStatus.READY && diagnostics.stream().anyMatch(value -> value.severity() == IntegrationDiagnosticSeverity.ERROR)) throw new IllegalArgumentException("READY injection plans cannot contain blocking diagnostics");
        if(status != InjectionPlanStatus.BLOCKED && rollbackSteps.isEmpty()) throw new IllegalArgumentException("A non-blocked injection plan requires rollback steps");
    }
}
