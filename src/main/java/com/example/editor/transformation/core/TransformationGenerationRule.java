package com.example.editor.transformation.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import com.example.editor.semantic.expression.ExpressionDefinition;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Regla M2T neutral basada en contexto, condición, ruta y plantilla segura. La condición ausente no se serializa, igual que en las demás reglas de transformación. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransformationGenerationRule(
        String id,
        String name,
        boolean enabled,
        String sourceEndpointId,
        String contextTypeId,
        ExpressionDefinition guard,
        String outputPathTemplate,
        String contentTemplate,
        GenerationOverwritePolicy overwritePolicy,
        Map<String,String> annotations) {
    public TransformationGenerationRule {
        id=text(id); name=text(name); sourceEndpointId=text(sourceEndpointId); contextTypeId=text(contextTypeId);
        outputPathTemplate=text(outputPathTemplate); contentTemplate=contentTemplate==null?"":contentTemplate;
        overwritePolicy=overwritePolicy==null?GenerationOverwritePolicy.FAIL:overwritePolicy;
        annotations=annotations==null||annotations.isEmpty()?Map.of():Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de TransformationGenerationRule. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value==null?"":value.trim();}
}
