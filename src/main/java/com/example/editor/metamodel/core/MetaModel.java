package com.example.editor.metamodel.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Representa el significado neutral y canónico de un metamodelo.
 *
 * Los nombres de archivo, espacios de nombres Ecore y demás decisiones de un
 * destino se guardan fuera de este record, en TargetConfigurationDocument.
 */
public record MetaModel(String schemaVersion, String id, String name, List<MetaClass> classes, List<MetaEnum> enums, List<MetaDataType> dataTypes, List<MetaRelation> relations, Map<String, String> annotations) {

    public static final String CURRENT_SCHEMA_VERSION = "2.1";

    public MetaModel {
        if(schemaVersion == null || schemaVersion.isBlank()) {
            schemaVersion = CURRENT_SCHEMA_VERSION;
        }

        id = id == null ? "" : id;
        name = name == null ? "" : name;
        classes = classes == null ? List.of() : List.copyOf(classes);
        enums = enums == null ? List.of() : List.copyOf(enums);
        dataTypes = dataTypes == null ? List.of() : List.copyOf(dataTypes);
        relations = relations == null ? List.of() : List.copyOf(relations);
        annotations = immutableMap(annotations);
    }

    /** Descripción: Ejecuta la operación «immutableMap» correspondiente a la responsabilidad de MetaModel. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String, String> immutableMap(Map<String, String> values) {
        if(values == null || values.isEmpty()) {
            return Map.of();
        }

        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
