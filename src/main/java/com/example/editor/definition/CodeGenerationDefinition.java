package com.example.editor.definition;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.editor.metamodel.importing.MetamodelImportFormat;

/** Configura los generadores disponibles dentro de un editor de modelos exportable. */
public record CodeGenerationDefinition(boolean enabled, boolean exposeInRuntime, List<String> targetIds,
        String defaultTargetId, String outputNamespace, boolean includeTraceability,
        CodeGenerationFeatureOptions defaultFeatures,
        Map<String, CodeGenerationFeatureOptions> targetFeatureOverrides,
        Map<String,String> annotations) {
    private static final List<String> DEFAULT_TARGET_IDS = Arrays.stream(MetamodelImportFormat.values()).map(MetamodelImportFormat::id).toList();
    public CodeGenerationDefinition {
        targetIds = targetIds == null || targetIds.isEmpty() ? defaultTargetIds() : List.copyOf(targetIds);
        defaultTargetId = defaultTargetId == null || defaultTargetId.isBlank() ? "java" : defaultTargetId.trim();
        outputNamespace = outputNamespace == null || outputNamespace.isBlank() ? "generated.model" : outputNamespace.trim();
        defaultFeatures = defaultFeatures == null ? CodeGenerationFeatureOptions.none() : defaultFeatures;
        targetFeatureOverrides = immutableFeatures(targetFeatureOverrides);
        annotations = annotations == null || annotations.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
    }

    /** Descripción: Mantiene el catálogo de destinos alineado con los identificadores públicos de importación de metamodelos. Entradas: no requiere parámetros. Salida: lista estable de identificadores. */
    /** Descripción: Ejecuta la operación «defaultTargetIds» correspondiente a la responsabilidad de CodeGenerationDefinition. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static List<String> defaultTargetIds() { return DEFAULT_TARGET_IDS; }

    /** Descripción: Resuelve el perfil efectivo para un destino permitiendo overrides por generador. Entradas: identificador de destino. Salida: perfil efectivo. */
    /** Descripción: Ejecuta la operación «featuresFor» correspondiente a la responsabilidad de CodeGenerationDefinition. Entradas: targetId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public CodeGenerationFeatureOptions featuresFor(String targetId) {
        if(!CodeGenerationFeatureCatalog.supportsOptionalSourceFeatures(targetId)) return CodeGenerationFeatureOptions.none();
        return targetFeatureOverrides.getOrDefault(targetId, defaultFeatures);
    }

    /** Descripción: Devuelve la configuración predeterminada que habilita todos los destinos y el runtime sin generar scaffolding adicional. Entradas: ninguna. Salida: definición segura. */
    /** Descripción: Ejecuta la operación «defaults» correspondiente a la responsabilidad de CodeGenerationDefinition. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static CodeGenerationDefinition defaults() {
        return new CodeGenerationDefinition(true, true, List.of(), "java", "generated.model", true,
                CodeGenerationFeatureOptions.none(), Map.of(), Map.of());
    }

    /** Descripción: Devuelve una capa deshabilitada para documentos históricos que no declaraban generación. Entradas: ninguna. Salida: definición compatible. */
    /** Descripción: Ejecuta la operación «disabled» correspondiente a la responsabilidad de CodeGenerationDefinition. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static CodeGenerationDefinition disabled() {
        return new CodeGenerationDefinition(false, false, defaultTargetIds(), "java", "generated.model", true,
                CodeGenerationFeatureOptions.none(), Map.of(), Map.of());
    }

    /** Descripción: Inmoviliza los overrides por destino preservando orden estable. Entradas: mapa opcional. Salida: mapa inmutable. */
    /** Descripción: Ejecuta la operación «immutableFeatures» correspondiente a la responsabilidad de CodeGenerationDefinition. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String, CodeGenerationFeatureOptions> immutableFeatures(Map<String, CodeGenerationFeatureOptions> values) {
        if(values == null || values.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
