package com.example.editor.portability;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.example.editor.integration.IntegrationTechnology;

/** Proveniencia y vínculo reversible del metamodelo de un paquete portable. */
public record MetamodelOrigin(MetamodelOriginKind kind, IntegrationTechnology technology, String sourceUri, String sourceRevision,
        String sourceChecksum, String importedAt, String synchronizedAt, Map<String, String> elementMappings,
        Map<String, String> annotations) {

    public MetamodelOrigin {
        kind = kind == null ? MetamodelOriginKind.OWNED : kind;
        sourceUri = safe(sourceUri);
        sourceRevision = safe(sourceRevision);
        sourceChecksum = safe(sourceChecksum);
        importedAt = safe(importedAt);
        synchronizedAt = safe(synchronizedAt);
        elementMappings = immutable(elementMappings);
        annotations = immutable(annotations);
    }

    /** Descripción: Ejecuta la operación «safe» correspondiente a la responsabilidad de MetamodelOrigin. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String safe(String value) {
        return value == null ? "" : value;
    }

    /** Descripción: Ejecuta la operación «immutable» correspondiente a la responsabilidad de MetamodelOrigin. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String, String> immutable(Map<String, String> values) {
        if(values == null || values.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
