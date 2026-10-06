package com.example.editor.integration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;
/** Proyecto o formato al que se dirige una integración. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IntegrationTarget(IntegrationTechnology technology, IntegrationTargetKind kind, String technologyVersion,
        String projectId, String moduleName, IntegrationBuildSystem buildSystem, List<String> sourceRoots,
        List<String> configurationFiles, String endpoint, Map<String, String> settings) {
    public IntegrationTarget {
        if(technology == null || kind == null) throw new IllegalArgumentException("target technology and kind are required");
        technologyVersion = normalize(technologyVersion);
        projectId = projectId == null || projectId.isBlank() ? "external-project" : StableIdentifierPolicy.requireDocumentId(projectId, "target.projectId");
        moduleName = normalize(moduleName);
        if(buildSystem == null) buildSystem = IntegrationBuildSystem.NONE;
        sourceRoots = normalizePaths(sourceRoots);
        configurationFiles = normalizePaths(configurationFiles);
        endpoint = normalize(endpoint);
        settings = settings == null || settings.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(settings));
    }
    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de IntegrationTarget. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalize(String value) { return value == null ? "" : value.trim(); }
    /** Descripción: Ejecuta la operación «normalizePaths» correspondiente a la responsabilidad de IntegrationTarget. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static List<String> normalizePaths(List<String> values) { return values == null ? List.of() : values.stream().map(IntegrationTarget::normalize).filter(value -> !value.isBlank()).distinct().toList(); }
}
