package com.example.editor.persistence.evolution;

import java.util.List;

/** Plan determinista para evolucionar un documento persistido entre dos versiones de esquema. */
public record HydraSchemaEvolutionPlan(String namespace, String fromVersion, String targetVersion,
        boolean reachable, List<String> steps, List<String> diagnostics) {
    public HydraSchemaEvolutionPlan {
        namespace = text(namespace);
        fromVersion = text(fromVersion);
        targetVersion = text(targetVersion);
        steps = steps == null ? List.of() : List.copyOf(steps);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    /** Descripción: Normaliza un texto opcional utilizado por el plan de migración. Entradas: value: valor de entrada. Salida: devuelve una cadena no nula y sin espacios extremos. */
    private static String text(String value) { return value == null ? "" : value.trim(); }
}
