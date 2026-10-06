package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

import com.example.editor.definition.EditorDefinitionCheckReport;
import com.example.editor.definition.EditorDefinitionModel;

/** Preview canónico de paletas, representaciones y mappings recuperados. */
public record UpdateSiteLayerMaterializationPreview(String analysisId, EditorDefinitionModel editorDefinition,
        EditorDefinitionCheckReport checkReport, int selectedPaletteGroups, int selectedTools,
        int selectedRepresentations, int selectedMappings, int selectedSemanticElements,
        boolean applicable, List<String> diagnostics) {
    public UpdateSiteLayerMaterializationPreview {
        analysisId = analysisId == null ? "" : analysisId;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
