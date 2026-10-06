package com.example.editor.portability;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import tools.jackson.databind.JsonNode;

/** Contenedor neutral que puede transportar una capa o un editor completo. */
public record HydraCapabilityPackage(HydraCapabilityPackageManifest manifest, Map<String, JsonNode> canonicalDocuments,
        Map<String, String> resources) {

    public HydraCapabilityPackage {
        canonicalDocuments = immutableNodes(canonicalDocuments);
        resources = immutable(resources);
    }

    /** Descripción: Ejecuta la operación «immutableNodes» correspondiente a la responsabilidad de HydraCapabilityPackage. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String, JsonNode> immutableNodes(Map<String, JsonNode> values) {
        if(values == null || values.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /** Descripción: Ejecuta la operación «immutable» correspondiente a la responsabilidad de HydraCapabilityPackage. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String, String> immutable(Map<String, String> values) {
        if(values == null || values.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
