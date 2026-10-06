package com.example.editor.integration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonInclude;
/** Localizador neutral de un elemento en un proyecto externo. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record HostElementReference(IntegrationLocatorStrategy strategy, String documentUri, String namespace,
        String qualifiedName, String externalId, String featureName, String path, Map<String, String> attributes) {
    public HostElementReference {
        if(strategy == null) throw new IllegalArgumentException("host reference strategy is required");
        documentUri = normalize(documentUri); namespace = normalize(namespace); qualifiedName = normalize(qualifiedName);
        externalId = normalize(externalId); featureName = normalize(featureName); path = normalize(path);
        attributes = attributes == null || attributes.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        if(documentUri.isBlank() && namespace.isBlank() && qualifiedName.isBlank() && externalId.isBlank() && path.isBlank()) throw new IllegalArgumentException("host reference must define at least one locator value");
    }
    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de HostElementReference. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalize(String value) { return value == null ? "" : value.trim(); }
}
