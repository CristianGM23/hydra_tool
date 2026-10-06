package com.example.editor.metamodel.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Describe un literal neutral.
 *
 * @param id contiene el identificador estable.
 * @param name contiene el nombre.
 * @param value contiene el valor numérico.
 * @param annotations contiene extensiones configurables.
 */
public record MetaEnumLiteral(String id, String name, int value, Map<String, String> annotations) {

    /**
     * Descripción:
     * Normaliza las anotaciones.
     *
     * Entrada:
     * Usa los componentes del record.
     *
     * Salida:
     * Crea un literal inmutable.
     */
    public MetaEnumLiteral {
        if (annotations == null || annotations.isEmpty()) {
            annotations = Map.of();
        } else {
            annotations = Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
        }
    }
}
