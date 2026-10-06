package com.example.editor.definition;

import java.util.Set;

/** Política neutral de ejecución, recuperación y automatización de un editor generado. */
public record EditorRuntimePolicy(Set<EditorValidationTrigger> validationTriggers, boolean autosave,
        boolean recoverLatestSession, boolean publishEvents, boolean eventFlowsEnabled,
        boolean gamificationEnabled, int maximumOpenRepresentations, long operationTimeoutMillis) {
    public EditorRuntimePolicy {
        validationTriggers = validationTriggers == null || validationTriggers.isEmpty()
                ? Set.of(EditorValidationTrigger.MANUAL) : Set.copyOf(validationTriggers);
        maximumOpenRepresentations = Math.max(1, maximumOpenRepresentations);
        operationTimeoutMillis = Math.max(100L, operationTimeoutMillis);
    }
    /** Descripción: Ejecuta la operación «defaults» correspondiente a la responsabilidad de EditorRuntimePolicy. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static EditorRuntimePolicy defaults() {
        return new EditorRuntimePolicy(Set.of(EditorValidationTrigger.MANUAL), true, true, true, false, false, 2, 30_000L);
    }
    /** Descripción: Valida las condiciones necesarias para «validates» y rechaza entradas no válidas. Entradas: trigger: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean validates(EditorValidationTrigger trigger) { return validationTriggers.contains(trigger); }
}
