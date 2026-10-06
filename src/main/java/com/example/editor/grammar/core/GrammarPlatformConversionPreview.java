package com.example.editor.grammar.core;

import java.util.List;

/** Resultado inmutable de una conversión preparada; no persiste ni modifica la gramática de origen. */
public record GrammarPlatformConversionPreview(
        String previewId,
        GrammarTechnology sourceTechnology,
        GrammarTechnology targetTechnology,
        GrammarDefinitionModel convertedGrammar,
        boolean semanticallyEquivalent,
        boolean exactSourceRestored,
        String sourceFingerprint,
        String targetFingerprint,
        String sourceHash,
        String targetHash,
        String sourceEntryRule,
        String targetEntryRule,
        String sourceEntryMethod,
        String targetEntryMethod,
        double sourceEntryConfidence,
        double targetEntryConfidence,
        List<String> recursiveRules,
        List<String> leftRecursiveRules,
        List<String> preservedConcepts,
        List<String> warnings,
        List<String> validationEvidence,
        String traceMarkdown) {
    public GrammarPlatformConversionPreview {
        previewId=text(previewId);sourceFingerprint=text(sourceFingerprint);targetFingerprint=text(targetFingerprint);sourceHash=text(sourceHash);targetHash=text(targetHash);
        sourceEntryRule=text(sourceEntryRule);targetEntryRule=text(targetEntryRule);sourceEntryMethod=text(sourceEntryMethod);targetEntryMethod=text(targetEntryMethod);traceMarkdown=textRaw(traceMarkdown);
        recursiveRules=copy(recursiveRules);leftRecursiveRules=copy(leftRecursiveRules);preservedConcepts=copy(preservedConcepts);warnings=copy(warnings);validationEvidence=copy(validationEvidence);
        sourceEntryConfidence=clamp(sourceEntryConfidence);targetEntryConfidence=clamp(targetEntryConfidence);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de GrammarPlatformConversionPreview. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value==null?"":value.trim();}
    /** Descripción: Ejecuta la operación «textRaw» correspondiente a la responsabilidad de GrammarPlatformConversionPreview. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String textRaw(String value){return value==null?"":value;}
    /** Descripción: Ejecuta la operación «copy» correspondiente a la responsabilidad de GrammarPlatformConversionPreview. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static List<String> copy(List<String> values){return values==null?List.of():List.copyOf(values);}
    /** Descripción: Ejecuta la operación «clamp» correspondiente a la responsabilidad de GrammarPlatformConversionPreview. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static double clamp(double value){return Math.max(0d,Math.min(1d,value));}
}
