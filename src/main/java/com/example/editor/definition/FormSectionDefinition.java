package com.example.editor.definition;
import java.util.List;
import com.example.editor.validation.contract.StableIdentifierPolicy;
/** Sección ordenada de un formulario. */
public record FormSectionDefinition(String id, String title, int order, int columns, List<FormFieldDefinition> fields) {
    public FormSectionDefinition {
        id = StableIdentifierPolicy.requireElementId(id, "formSection.id");
        title = title == null || title.isBlank() ? id : title.trim();
        if(columns < 1 || columns > 4) throw new IllegalArgumentException("columns must be between 1 and 4");
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
