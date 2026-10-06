package com.example.editor.metamodel.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Documento canónico que mantiene las implementaciones separadas de las firmas. */
public record OperationBehaviorModel(String schemaVersion, String metamodelId, List<OperationBinding> bindings, Map<String, String> annotations) {
    public static final String CURRENT_SCHEMA_VERSION = "1.0";

    public OperationBehaviorModel {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? CURRENT_SCHEMA_VERSION : schemaVersion;
        if(!CURRENT_SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("Unsupported operation behavior schema version: " + schemaVersion);
        }
        metamodelId = metamodelId == null ? "" : metamodelId;
        bindings = bindings == null ? List.of() : List.copyOf(bindings);
        if(annotations == null || annotations.isEmpty()) {
            annotations = Map.of();
        } else {
            annotations = Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
        }
    }
}
