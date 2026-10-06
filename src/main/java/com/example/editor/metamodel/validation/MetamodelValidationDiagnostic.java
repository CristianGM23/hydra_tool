package com.example.editor.metamodel.validation;

import java.util.List;

/** Diagnóstico orientado al usuario construido a partir de una regla neutral. */
public record MetamodelValidationDiagnostic(ValidationSeverity severity, String code, String category, String title,
        String whatHappened, String location, String situation, List<String> involvedElements,
        List<String> howToFix, String exportImpact, String path) {
    public MetamodelValidationDiagnostic {
        category = text(category, "Structure"); title = text(title, code); whatHappened = text(whatHappened, "Validation failed.");
        location = text(location, "Metamodel"); situation = text(situation, "Review the affected element.");
        involvedElements = involvedElements == null ? List.of() : List.copyOf(involvedElements);
        howToFix = howToFix == null ? List.of() : List.copyOf(howToFix);
        exportImpact = text(exportImpact, severity == ValidationSeverity.ERROR ? "Blocks derived project generation." : "Does not block generation by default.");
        path = path == null ? "$" : path;
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de MetamodelValidationDiagnostic. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value, String fallback){return value == null || value.isBlank() ? fallback : value.trim();}
}
