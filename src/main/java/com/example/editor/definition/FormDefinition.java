package com.example.editor.definition;
import java.util.List;
import com.example.editor.validation.contract.StableIdentifierPolicy;
/** Formulario de creación o edición de una clase. */
public record FormDefinition(String id, String name, String targetClassId, List<FormSectionDefinition> sections) {
    public FormDefinition {
        id = StableIdentifierPolicy.requireElementId(id, "form.id");
        name = name == null || name.isBlank() ? id : name.trim();
        targetClassId = StableIdentifierPolicy.requireElementId(targetClassId, "targetClassId");
        sections = sections == null ? List.of() : List.copyOf(sections);
    }
}
