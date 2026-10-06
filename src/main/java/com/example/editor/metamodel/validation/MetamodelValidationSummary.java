package com.example.editor.metamodel.validation;

/** Resumen de severidades de una validación de metamodelo. */
public record MetamodelValidationSummary(int errors, int warnings, int total) {
    public MetamodelValidationSummary {
        errors = Math.max(0, errors); warnings = Math.max(0, warnings); total = Math.max(errors + warnings, total);
    }
}
