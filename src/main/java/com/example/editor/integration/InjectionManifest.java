package com.example.editor.integration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;
/** Manifiesto reversible que acompañará a cada paquete generado. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record InjectionManifest(String schemaVersion, String id, String integrationProfileId, IntegrationTechnology targetTechnology,
        String targetVersion, String generatorVersion, Instant createdAt, List<InjectionFileChange> files,
        List<IntegrationDependencyChange> dependencies, List<String> installationSteps, List<String> rollbackSteps,
        Map<String, String> annotations) {
    public InjectionManifest {
        if(schemaVersion == null || schemaVersion.isBlank()) schemaVersion = IntegrationContractVersions.INJECTION_MANIFEST;
        if(!IntegrationContractVersions.INJECTION_MANIFEST.equals(schemaVersion)) throw new IllegalArgumentException("Unsupported injection manifest schemaVersion: " + schemaVersion);
        id = StableIdentifierPolicy.requireDocumentId(id, "id");
        integrationProfileId = StableIdentifierPolicy.requireDocumentId(integrationProfileId, "integrationProfileId");
        if(targetTechnology == null) throw new IllegalArgumentException("targetTechnology is required");
        targetVersion = normalize(targetVersion); generatorVersion = normalize(generatorVersion);
        if(createdAt == null) createdAt = Instant.now();
        files = files == null ? List.of() : List.copyOf(files);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        installationSteps = installationSteps == null ? List.of() : List.copyOf(installationSteps);
        rollbackSteps = rollbackSteps == null ? List.of() : List.copyOf(rollbackSteps);
        annotations = annotations == null || annotations.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
    }
    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de InjectionManifest. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalize(String value) { return value == null ? "" : value.trim(); }
}
