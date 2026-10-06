package com.example.editor.metamodel.target;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.example.editor.metamodel.core.MetaModel;

/**
 * Guarda la configuración de las tecnologías a las que se proyecta un MetaModel.
 */
public record TargetConfigurationDocument(String schemaVersion, String metamodelId, EcoreProjectionConfiguration ecore, Map<String, Map<String, String>> additionalTargets) {

    public static final String CURRENT_SCHEMA_VERSION = "1.0";

    public TargetConfigurationDocument {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? CURRENT_SCHEMA_VERSION : schemaVersion;

        if (!CURRENT_SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("Unsupported target configuration schema version: " + schemaVersion);
        }

        metamodelId = metamodelId == null ? "" : metamodelId;
        additionalTargets = immutableNestedMap(additionalTargets);
    }

    /** Descripción: Ejecuta la operación «defaultsFor» correspondiente a la responsabilidad de TargetConfigurationDocument. Entradas: metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static TargetConfigurationDocument defaultsFor(MetaModel metamodel) {
        return new TargetConfigurationDocument(CURRENT_SCHEMA_VERSION, metamodel.id(), EcoreProjectionConfiguration.defaultsFor(metamodel), Map.of());
    }

    /** Descripción: Ejecuta la operación «normalizedFor» correspondiente a la responsabilidad de TargetConfigurationDocument. Entradas: metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TargetConfigurationDocument normalizedFor(MetaModel metamodel) {
        if (!metamodel.id().equals(metamodelId)) {
            throw new IllegalArgumentException("Target configuration metamodel id must match the MetaModel id");
        }

        EcoreProjectionConfiguration effectiveEcore = ecore == null ? EcoreProjectionConfiguration.defaultsFor(metamodel) : ecore.normalizedFor(metamodel);
        return new TargetConfigurationDocument(schemaVersion, metamodelId, effectiveEcore, additionalTargets);
    }

    /** Descripción: Ejecuta la operación «immutableNestedMap» correspondiente a la responsabilidad de TargetConfigurationDocument. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String, Map<String, String>> immutableNestedMap(Map<String, Map<String, String>> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }

        Map<String, Map<String, String>> copy = new LinkedHashMap<>();

        for (Map.Entry<String, Map<String, String>> entry : values.entrySet()) {
            Map<String, String> nested = entry.getValue() == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(entry.getValue()));
            copy.put(entry.getKey(), nested);
        }

        return Collections.unmodifiableMap(copy);
    }
}
