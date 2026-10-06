package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Resumen comprensible de una gramática Xtext o Langium encontrada dentro del update site. */
public record UpdateSiteGrammarInspection(String sourcePath, String language, String grammarName,
        int parserRules, int terminalRules, int imports, int assignments, int crossReferences,
        int actions, List<String> duplicateRules, List<String> possibleLeftRecursions,
        String inferredEntryRule, String entryInferenceMethod, double entryInferenceConfidence,
        List<String> recursiveRules, List<String> automaticResolutionRules, List<String> diagnostics) {
    public UpdateSiteGrammarInspection {
        sourcePath = text(sourcePath);
        language = text(language);
        grammarName = text(grammarName);
        parserRules = Math.max(0, parserRules);
        terminalRules = Math.max(0, terminalRules);
        imports = Math.max(0, imports);
        assignments = Math.max(0, assignments);
        crossReferences = Math.max(0, crossReferences);
        actions = Math.max(0, actions);
        duplicateRules = copy(duplicateRules);
        possibleLeftRecursions = copy(possibleLeftRecursions);
        inferredEntryRule = text(inferredEntryRule);
        entryInferenceMethod = text(entryInferenceMethod);
        entryInferenceConfidence = Math.max(0d, Math.min(1d, entryInferenceConfidence));
        recursiveRules = copy(recursiveRules);
        automaticResolutionRules = copy(automaticResolutionRules);
        diagnostics = copy(diagnostics);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de UpdateSiteGrammarInspection. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value) { return value == null ? "" : value.trim(); }
    /** Descripción: Ejecuta la operación «copy» correspondiente a la responsabilidad de UpdateSiteGrammarInspection. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static List<String> copy(List<String> values) { return values == null ? List.of() : List.copyOf(values); }
}
