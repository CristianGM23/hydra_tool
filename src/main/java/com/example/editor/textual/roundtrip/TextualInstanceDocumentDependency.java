package com.example.editor.textual.roundtrip;

/** Dependencia de linking entre dos documentos textuales de una instancia generada. */
public record TextualInstanceDocumentDependency(
        String sourceDocumentId,
        String targetDocumentId,
        String sourceElementId,
        String targetElementId,
        String relationId,
        String resolutionMode) {
    public TextualInstanceDocumentDependency {
        sourceDocumentId=text(sourceDocumentId);
        targetDocumentId=text(targetDocumentId);
        sourceElementId=text(sourceElementId);
        targetElementId=text(targetElementId);
        relationId=text(relationId);
        resolutionMode=text(resolutionMode);
        if(resolutionMode.isBlank()) resolutionMode="WORKSPACE_SCOPE";
    }
    /** Descripción: Normaliza texto nullable para mantener estable el contrato de dependencias documentales. Entradas: value: valor opcional. Salida: devuelve texto recortado o cadena vacía. */
    private static String text(String value){return value==null?"":value.trim();}
}
