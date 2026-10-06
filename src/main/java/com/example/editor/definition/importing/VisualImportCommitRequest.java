package com.example.editor.definition.importing;

import java.util.List;
import com.example.editor.definition.EditorDefinitionModel;
import com.example.editor.metamodel.core.MetaModel;

/** Final confirmation of a visual import after the side-effect-free preview. */
public record VisualImportCommitRequest(EditorDefinitionModel editorDefinition, MetaModel associatedMetamodel,
        VisualMetamodelAssociation metamodelAssociation, List<VisualImportedValidation> validationDetails) {
    public VisualImportCommitRequest {
        validationDetails = validationDetails == null ? List.of() : List.copyOf(validationDetails);
    }
}
