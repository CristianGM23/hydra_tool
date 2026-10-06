package com.example.editor.definition.importing;

import java.util.List;
import com.example.editor.definition.EditorDefinitionModel;

public record VisualImportCommitResult(EditorDefinitionModel editorDefinition, String metamodelId,
        int validationsImported, List<String> validationRuleIds) {
    public VisualImportCommitResult { validationRuleIds = validationRuleIds == null ? List.of() : List.copyOf(validationRuleIds); }
}
