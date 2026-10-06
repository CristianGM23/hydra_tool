package com.example.editor.validation.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.example.editor.validation.contract.StableIdentifierPolicy;

/** Representa una instancia neutral de una relación del metamodelo. */
public record CanonicalModelRelation(String id, String relationId, String sourceElementId, String targetElementId, Map<String, String> annotations) {

    public CanonicalModelRelation {
        id = StableIdentifierPolicy.requireElementId(id, "id");
        relationId = StableIdentifierPolicy.requireElementId(relationId, "relationId");
        sourceElementId = StableIdentifierPolicy.requireElementId(sourceElementId, "sourceElementId");
        targetElementId = StableIdentifierPolicy.requireElementId(targetElementId, "targetElementId");
        if(annotations == null || annotations.isEmpty()) annotations = Map.of();
        else annotations = Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
    }
}
