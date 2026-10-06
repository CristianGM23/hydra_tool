package com.example.editor.transformation.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record TransformationDefinitionDocument(
        String schemaVersion,
        String id,
        String name,
        TransformationKind kind,
        TransformationDirectionality directionality,
        TransformationSynchronizationPolicy synchronizationPolicy,
        List<TransformationEndpoint> sources,
        List<TransformationEndpoint> targets,
        List<TransformationRule> rules,
        List<TransformationGenerationRule> generationRules,
        TransformationTextToModelConfiguration textToModel,
        TransformationExecutionPolicy executionPolicy,
        TransformationTracePolicy tracePolicy,
        Map<String, String> annotations) {

    public static final String CURRENT_SCHEMA_VERSION = "1.1";
    public static final Set<String> SUPPORTED_SCHEMA_VERSIONS = Set.of("1.0", CURRENT_SCHEMA_VERSION);

    public TransformationDefinitionDocument {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? CURRENT_SCHEMA_VERSION : schemaVersion.trim();
        id = text(id);
        name = text(name);
        kind = kind == null ? TransformationKind.M2M : kind;
        directionality = directionality == null ? TransformationDirectionality.UNIDIRECTIONAL : directionality;
        synchronizationPolicy = synchronizationPolicy == null ? TransformationSynchronizationPolicy.defaults() : synchronizationPolicy;
        sources = sources == null ? List.of() : List.copyOf(sources);
        targets = targets == null ? List.of() : List.copyOf(targets);
        rules = rules == null ? List.of() : List.copyOf(rules);
        generationRules = generationRules == null ? List.of() : List.copyOf(generationRules);
        executionPolicy = executionPolicy == null ? TransformationExecutionPolicy.defaults() : executionPolicy;
        tracePolicy = tracePolicy == null ? TransformationTracePolicy.defaults() : tracePolicy;
        annotations = immutable(annotations);
    }

    /** Backward-compatible source constructor for schema 1.0 documents created before directionality became first-class. */
    /** Descripción: Inicializa una instancia de TransformationDefinitionDocument con sus dependencias y datos de entrada. Entradas: schemaVersion: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación; kind: dato de entrada necesario para la operación; sources: dato de entrada necesario para la operación; targets: dato de entrada necesario para la operación; rules: dato de entrada necesario para la operación; generationRules: dato de entrada necesario para la operación; textToModel: dato de entrada necesario para la operación; executionPolicy: dato de entrada necesario para la operación; tracePolicy: dato de entrada necesario para la operación; annotations: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationDefinitionDocument(
            String schemaVersion, String id, String name, TransformationKind kind,
            List<TransformationEndpoint> sources, List<TransformationEndpoint> targets,
            List<TransformationRule> rules, List<TransformationGenerationRule> generationRules,
            TransformationTextToModelConfiguration textToModel, TransformationExecutionPolicy executionPolicy,
            TransformationTracePolicy tracePolicy, Map<String, String> annotations) {
        this(schemaVersion, id, name, kind, TransformationDirectionality.UNIDIRECTIONAL,
                TransformationSynchronizationPolicy.defaults(), sources, targets, rules, generationRules, textToModel,
                executionPolicy, tracePolicy, annotations);
    }

    /** Descripción: Normaliza un texto del documento canónico. Entradas: value: texto opcional. Salida: texto no nulo y sin espacios extremos. */
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de TransformationDefinitionDocument. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value) { return value == null ? "" : value.trim(); }

    /** Descripción: Copia un mapa de anotaciones para impedir mutaciones externas. Entradas: values: mapa opcional. Salida: mapa inmutable. */
    /** Descripción: Ejecuta la operación «immutable» correspondiente a la responsabilidad de TransformationDefinitionDocument. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String, String> immutable(Map<String, String> values) {
        if(values == null || values.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
