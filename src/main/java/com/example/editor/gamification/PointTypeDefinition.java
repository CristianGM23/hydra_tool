package com.example.editor.gamification;

import com.example.editor.validation.contract.StableIdentifierPolicy;

/** Tipo de puntos con límite inferior configurable para penalizaciones. */
public record PointTypeDefinition(String id, String name, String description, long minimumBalance) {
    public PointTypeDefinition {
        id = StableIdentifierPolicy.requireElementId(id, "pointType.id");
        name = name == null || name.isBlank() ? id : name.trim();
        description = description == null ? "" : description.trim();
    }
}
