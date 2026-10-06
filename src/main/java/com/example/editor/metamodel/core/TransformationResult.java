package com.example.editor.metamodel.core;

import java.util.List;

/**
 * Contiene un resultado y las advertencias producidas.
 *
 * @param value contiene el resultado.
 * @param warnings contiene adaptaciones o pérdidas.
 * @param <T> define el tipo generado.
 */
public record TransformationResult<T>(T value, List<TransformationWarning> warnings) {

    /**
     * Descripción:
     * Normaliza las advertencias.
     *
     * Entrada:
     * Usa los componentes del record.
     *
     * Salida:
     * Crea un resultado inmutable.
     */
    public TransformationResult {
        if (warnings == null) {
            warnings = List.of();
        } else {
            warnings = List.copyOf(warnings);
        }
    }

    /**
     * Descripción:
     * Indica si existen advertencias.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * @return true cuando existen advertencias.
     */
    /** Descripción: Evalúa la condición «hasWarnings» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
}
