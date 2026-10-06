package com.example.editor.codegen;

import com.example.editor.definition.CodeGenerationFeatureOptions;

/** Opciones neutrales de una generación de código desde un modelo. */
public record ModelCodeGenerationOptions(String namespace, String artifactName, boolean includeTraceability,
        CodeGenerationFeatureOptions features) {
    public ModelCodeGenerationOptions {
        namespace = namespace == null || namespace.isBlank() ? "generated.model" : namespace.trim();
        artifactName = artifactName == null || artifactName.isBlank() ? "GeneratedModel" : artifactName.trim();
        features = features == null ? CodeGenerationFeatureOptions.none() : features;
    }

    /** Descripción: Devuelve opciones seguras por defecto. Entradas: ninguna. Salida: configuración predeterminada. */
    /** Descripción: Ejecuta la operación «defaults» correspondiente a la responsabilidad de ModelCodeGenerationOptions. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static ModelCodeGenerationOptions defaults() { return new ModelCodeGenerationOptions("generated.model", "GeneratedModel", true, CodeGenerationFeatureOptions.none()); }
}
