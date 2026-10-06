package com.example.editor.definition;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import com.example.editor.codegen.ModelCodeGenerationFeature;

/** Registro neutral y único de capacidades opcionales de scaffolding por destino, preparado para perfiles parciales. */
public final class CodeGenerationFeatureCatalog {
    private static final Set<ModelCodeGenerationFeature> ALL_SOURCE_FEATURES = Set.copyOf(EnumSet.allOf(ModelCodeGenerationFeature.class));
    private static final Map<String,Set<ModelCodeGenerationFeature>> FEATURES_BY_TARGET = build();

    /** Descripción: Impide instanciar el catálogo estático. Entradas: no requiere parámetros. Salida: deja el tipo como utilidad no instanciable. */
    /** Descripción: Inicializa una instancia de CodeGenerationFeatureCatalog con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private CodeGenerationFeatureCatalog() { }

    /** Descripción: Indica si el destino expone al menos una capacidad opcional. Entradas: identificador de destino. Salida: verdadero cuando admite scaffolding configurable. */
    /** Descripción: Evalúa la condición «supportsOptionalSourceFeatures» sin modificar el estado externo. Entradas: targetId: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ public static boolean supportsOptionalSourceFeatures(String targetId) { return !supportedFeatures(targetId).isEmpty(); }

    /** Descripción: Devuelve exactamente las capacidades soportadas por un destino y permite perfiles parciales futuros. Entradas: identificador de destino. Salida: conjunto inmutable de capacidades. */
    /** Descripción: Ejecuta la operación «supportedFeatures» correspondiente a la responsabilidad de CodeGenerationFeatureCatalog. Entradas: targetId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public static Set<ModelCodeGenerationFeature> supportedFeatures(String targetId) { return FEATURES_BY_TARGET.getOrDefault(targetId, Set.of()); }

    /** Descripción: Comprueba una capacidad concreta sin asumir que todos los lenguajes soportan el mismo perfil. Entradas: destino y capacidad. Salida: verdadero cuando está soportada. */
    /** Descripción: Evalúa la condición «supports» sin modificar el estado externo. Entradas: targetId: identificador que delimita el recurso objetivo; feature: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public static boolean supports(String targetId, ModelCodeGenerationFeature feature) { return supportedFeatures(targetId).contains(feature); }

    /** Descripción: Expone los destinos con alguna capacidad opcional para validadores y compatibilidad. Entradas: ninguna. Salida: conjunto inmutable de identificadores. */
    /** Descripción: Ejecuta la operación «richSourceTargetIds» correspondiente a la responsabilidad de CodeGenerationFeatureCatalog. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static Set<String> richSourceTargetIds() { return FEATURES_BY_TARGET.keySet(); }

    /** Descripción: Construye el registro incorporado de capacidades por lenguaje en un único punto extensible. Entradas: ninguna. Salida: mapa inmutable. */
    /** Descripción: Crea o prepara el resultado de «build» dentro de CodeGenerationFeatureCatalog. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String,Set<ModelCodeGenerationFeature>> build() {
        Map<String,Set<ModelCodeGenerationFeature>> values = new LinkedHashMap<>();
        for(String targetId : Set.of("java", "typescript", "javascript", "go", "python")) values.put(targetId, ALL_SOURCE_FEATURES);
        return Map.copyOf(values);
    }
}
