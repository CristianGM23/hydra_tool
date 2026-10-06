package com.example.editor.portability;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Artefacto canónico seleccionado para un paquete parcial o completo. */
public record CapabilitySelection(String id, HydraCapabilityKind kind, String documentId, String schemaVersion, boolean required,
        boolean compiled, CapabilityExecutionMode executionMode, Map<String, String> annotations) {

    public CapabilitySelection {
        id = safe(id);
        documentId = safe(documentId);
        schemaVersion = safe(schemaVersion);
        executionMode = executionMode == null ? CapabilityExecutionMode.EMBEDDED : executionMode;
        annotations = annotations == null || annotations.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
    }

    /** Descripción: Mantiene compatibilidad con selecciones anteriores al modo de ejecución explícito. Entradas: contrato histórico completo. Salida: usa EMBEDDED como modo heredado. */
    /** Descripción: Inicializa una instancia de CapabilitySelection con sus dependencias y datos de entrada. Entradas: id: identificador que delimita el recurso objetivo; kind: dato de entrada necesario para la operación; documentId: identificador que delimita el recurso objetivo; schemaVersion: dato de entrada necesario para la operación; required: dato de entrada necesario para la operación; compiled: dato de entrada necesario para la operación; annotations: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CapabilitySelection(String id, HydraCapabilityKind kind, String documentId, String schemaVersion,
            boolean required, boolean compiled, Map<String, String> annotations) {
        this(id, kind, documentId, schemaVersion, required, compiled, CapabilityExecutionMode.EMBEDDED, annotations);
    }

    /** Descripción: Ejecuta la operación «safe» correspondiente a la responsabilidad de CapabilitySelection. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String safe(String value) {
        return value == null ? "" : value;
    }
}
