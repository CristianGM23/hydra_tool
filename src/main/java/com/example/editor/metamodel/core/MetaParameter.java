package com.example.editor.metamodel.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Describe un parámetro neutral de una operación. */
public record MetaParameter(String id, String name, MetaTypeReference type, Multiplicity multiplicity, boolean ordered, boolean unique, Map<String, String> annotations) {
    public MetaParameter {
        id = id == null ? "" : id;
        name = name == null ? "" : name;
        multiplicity = multiplicity == null ? Multiplicity.one() : multiplicity;
        if(annotations == null || annotations.isEmpty()) {
            annotations = Map.of();
        } else {
            annotations = Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
        }
    }
}
