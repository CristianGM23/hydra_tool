package com.example.editor.project.recovery;

import java.util.List;

/** Diagnóstico neutral y no ejecutable de una gramática encontrada en un proyecto externo. */
public record ExternalProjectGrammarInspection(String sourcePath, String technology, String inferredEntryRule,
        String inferenceMethod, double inferenceConfidence, List<String> parserRules, List<String> recursiveRules,
        List<String> leftRecursiveRules, List<String> automaticResolutionRules, List<String> explanations) {
    public ExternalProjectGrammarInspection {
        sourcePath = text(sourcePath); technology = text(technology); inferredEntryRule = text(inferredEntryRule);
        inferenceMethod = text(inferenceMethod); inferenceConfidence = Math.max(0d, Math.min(1d, inferenceConfidence));
        parserRules = copy(parserRules); recursiveRules = copy(recursiveRules); leftRecursiveRules = copy(leftRecursiveRules);
        automaticResolutionRules = copy(automaticResolutionRules); explanations = copy(explanations);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de ExternalProjectGrammarInspection. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){ return value == null ? "" : value.trim(); }
    /** Descripción: Ejecuta la operación «copy» correspondiente a la responsabilidad de ExternalProjectGrammarInspection. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static List<String> copy(List<String> values){ return values == null ? List.of() : List.copyOf(values); }
}
