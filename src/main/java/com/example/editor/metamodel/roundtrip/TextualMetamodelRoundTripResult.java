package com.example.editor.metamodel.roundtrip;

import java.util.List;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.TransformationWarning;
import com.example.editor.metamodel.export.MetamodelExportFormat;

/** Evidencia completa de un round-trip Xtext/Langium ejecutado enteramente sobre el modelo neutral. */
public record TextualMetamodelRoundTripResult(MetamodelExportFormat format, String generatedText, MetaModel reimportedMetamodel,
        List<TransformationWarning> importWarnings, SemanticEquivalenceReport equivalence) {
    public TextualMetamodelRoundTripResult {
        generatedText = generatedText == null ? "" : generatedText;
        importWarnings = importWarnings == null ? List.of() : List.copyOf(importWarnings);
    }
}
