package com.example.editor.metamodel.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Describe una enumeración neutral.
 *
 * @param id contiene el identificador estable.
 * @param name contiene el nombre.
 * @param literals contiene los literales.
 * @param annotations contiene extensiones configurables.
 */
public record MetaEnum(String id, String name, List<MetaEnumLiteral> literals, Map<String, String> annotations) {

    /**
     * Descripción:
     * Normaliza las colecciones de la enumeración.
     *
     * Entrada:
     * Usa los componentes del record.
     *
     * Salida:
     * Crea una enumeración inmutable.
     */
    public MetaEnum {
        if (literals == null) {
            literals = List.of();
        } else {
            literals = List.copyOf(literals);
        }

        if (annotations == null || annotations.isEmpty()) {
            annotations = Map.of();
        } else {
            annotations = Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
        }
    }
}
