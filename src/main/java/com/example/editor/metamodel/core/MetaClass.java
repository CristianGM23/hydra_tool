package com.example.editor.metamodel.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Describe una clase neutral. Las interfaces pueden declarar atributos como
 * propiedades estructurales requeridas y operaciones como contratos.
 */
public record MetaClass(String id, String name, MetaClassKind kind, List<MetaAttribute> attributes, List<MetaOperation> operations, Map<String, String> annotations) {

    public MetaClass {
        id = id == null ? "" : id;
        name = name == null ? "" : name;
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
        operations = operations == null ? List.of() : List.copyOf(operations);
        if(annotations == null || annotations.isEmpty()) {
            annotations = Map.of();
        } else {
            annotations = Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
        }
    }
}
