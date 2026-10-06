package com.example.editor.integration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;
/** Enlace entre un elemento canónico y un elemento de la tecnología anfitriona. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IntegrationElementMapping(String id, IntegrationMappingKind kind, String canonicalDocumentId,
        String canonicalElementId, HostElementReference hostReference, boolean required, Map<String, String> annotations) {
    public IntegrationElementMapping {
        id = StableIdentifierPolicy.requireElementId(id, "mapping.id");
        if(kind == null) throw new IllegalArgumentException("mapping.kind must not be null");
        canonicalDocumentId = StableIdentifierPolicy.requireDocumentId(canonicalDocumentId, "mapping.canonicalDocumentId");
        canonicalElementId = normalizeElementId(canonicalElementId);
        if(hostReference == null) throw new IllegalArgumentException("mapping.hostReference is required");
        annotations = annotations == null || annotations.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
    }
    /** Descripción: Ejecuta la operación «normalizeElementId» correspondiente a la responsabilidad de IntegrationElementMapping. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalizeElementId(String value) {
        if(value == null || value.isBlank()) return "";
        return StableIdentifierPolicy.requireElementId(value, "mapping.canonicalElementId");
    }
}
