package com.example.editor.textual.roundtrip;

/** Resultado de comparar reconstrucciones de una misma instancia mediante Xtext y Langium. */
public record TextualInstanceProviderParityReport(
        TextualInstanceRoundTripResult xtext,
        TextualInstanceRoundTripResult langium,
        CanonicalModelSemanticReport crossProviderEquivalence) {
    /** Descripción: Determina si ambos proveedores preservan la fuente y reconstruyen el mismo significado canónico. Entradas: no requiere parámetros. Salida: devuelve true cuando Xtext, Langium y la comparación cruzada son lossless. */ public boolean semanticallyAligned(){return xtext!=null&&langium!=null&&xtext.lossless()&&langium.lossless()&&crossProviderEquivalence!=null&&crossProviderEquivalence.equivalent();}
}
