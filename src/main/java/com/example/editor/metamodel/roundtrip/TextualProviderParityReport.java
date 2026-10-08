package com.example.editor.metamodel.roundtrip;

/** Evidencia de que Xtext y Langium proyectan y reconstruyen el mismo significado neutral. */
public record TextualProviderParityReport(TextualMetamodelRoundTripResult xtext, TextualMetamodelRoundTripResult langium,
        SemanticEquivalenceReport crossProviderEquivalence, TextualSemanticCapabilityReport nativeCapability) {
    /** Descripción: Comprueba conjuntamente que Xtext, Langium y la comparación cruzada reconstruyen el mismo significado neutral. Entradas: no requiere parámetros de entrada. Salida: devuelve true solo cuando ambos round-trips son equivalentes y también lo son entre sí. */
    public boolean semanticallyAligned() {
        return xtext != null && langium != null && xtext.equivalence().semanticallyEquivalent()
                && langium.equivalence().semanticallyEquivalent() && crossProviderEquivalence != null
                && crossProviderEquivalence.semanticallyEquivalent();
    }
}
