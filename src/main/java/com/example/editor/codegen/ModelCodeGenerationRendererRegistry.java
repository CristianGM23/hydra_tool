package com.example.editor.codegen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Registro inmutable y validado de renderers de generación; permite extensiones por inyección de beans. */
@Component
public class ModelCodeGenerationRendererRegistry {
    private final Map<String, ModelCodeGenerationRenderer> byTarget;

    /** Descripción: Construye el registro desde todos los renderers descubiertos por Spring y valida cobertura, duplicados y capacidades. Entradas: renderers disponibles. Salida: registro listo o fallo temprano de configuración. */
    /** Descripción: Inicializa una instancia de ModelCodeGenerationRendererRegistry con sus dependencias y datos de entrada. Entradas: renderers: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ModelCodeGenerationRendererRegistry(List<ModelCodeGenerationRenderer> renderers) { this.byTarget = validate(renderers); }

    /** Descripción: Crea el registro de renderers incorporados para tests aislados o ejecución sin contenedor Spring. Entradas: mapper JSON. Salida: registro equivalente al de producción. */
    /** Descripción: Ejecuta la operación «builtIns» correspondiente a la responsabilidad de ModelCodeGenerationRendererRegistry. Entradas: mapper: componente de conversión utilizado durante el procesamiento. Salida: devuelve el resultado definido por el contrato del método. */ static ModelCodeGenerationRendererRegistry builtIns(JsonMapper mapper) {
        return new ModelCodeGenerationRendererRegistry(List.of(new ProjectionModelCodeGenerationRenderer(mapper), new SourceModelCodeGenerationRenderer(mapper), new SqlDataModelCodeGenerationRenderer()));
    }

    /** Descripción: Obtiene el renderer que atiende un destino. Entradas: identificador de destino. Salida: renderer registrado. */
    /** Descripción: Valida las condiciones necesarias para «require» y rechaza entradas no válidas. Entradas: targetId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ ModelCodeGenerationRenderer require(String targetId) {
        ModelCodeGenerationRenderer renderer = byTarget.get(targetId);
        if(renderer == null) throw new IllegalArgumentException("Unsupported model code generation target: " + targetId);
        return renderer;
    }

    /** Descripción: Valida y construye el índice de renderers asegurando cobertura exacta de los destinos públicos. Entradas: lista de renderers. Salida: mapa inmutable por destino. */
    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: renderers: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String, ModelCodeGenerationRenderer> validate(List<ModelCodeGenerationRenderer> renderers) {
        if(renderers == null || renderers.isEmpty()) throw new IllegalStateException("At least one model code generation renderer is required");
        Map<String,ModelCodeGenerationRenderer> indexed = new LinkedHashMap<>();
        for(ModelCodeGenerationRenderer renderer : renderers) {
            if(renderer == null || renderer.targetIds() == null || renderer.targetIds().isEmpty()) throw new IllegalStateException("A code generation renderer must expose at least one target id");
            for(String targetId : renderer.targetIds()) {
                if(targetId == null || targetId.isBlank()) throw new IllegalStateException("Code generation target ids must not be blank");
                if(indexed.putIfAbsent(targetId, renderer) != null) throw new IllegalStateException("Duplicate model code generation renderer for target: " + targetId);
            }
        }
        List<ModelCodeGenerationTarget> publicTargets = ModelCodeGenerationTarget.catalog();
        Set<String> expected = publicTargets.stream().map(ModelCodeGenerationTarget::id).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> actual = new LinkedHashSet<>(indexed.keySet());
        if(!expected.equals(actual)) {
            Set<String> missing = new LinkedHashSet<>(expected); missing.removeAll(actual);
            Set<String> extra = new LinkedHashSet<>(actual); extra.removeAll(expected);
            throw new IllegalStateException("Code generation renderer catalog is inconsistent. Missing=" + missing + ", extra=" + extra);
        }
        List<String> capabilityErrors = new ArrayList<>();
        for(ModelCodeGenerationTarget target : publicTargets) {
            Set<ModelCodeGenerationFeature> declared = indexed.get(target.id()).supportedFeatures(target.id());
            if(declared == null || !Set.copyOf(declared).equals(target.supportedFeatures())) capabilityErrors.add(target.id());
        }
        if(!capabilityErrors.isEmpty()) throw new IllegalStateException("Code generation renderer capabilities do not match the public catalog: " + capabilityErrors);
        return Map.copyOf(indexed);
    }
}
