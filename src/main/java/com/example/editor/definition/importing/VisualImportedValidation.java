package com.example.editor.definition.importing;

/** Validation recovered from an external visual technology while preserving its original expression. */
public record VisualImportedValidation(String source, String technology, String name, String representationId,
        String contextClassId, String expression, String message, String severity, String originalFragment) {
    public VisualImportedValidation {
        source = source == null ? "" : source;
        technology = technology == null || technology.isBlank() ? "EXTERNAL" : technology;
        name = name == null || name.isBlank() ? "Imported visual validation" : name;
        representationId = representationId == null ? "" : representationId;
        contextClassId = contextClassId == null ? "" : contextClassId;
        expression = expression == null ? "" : expression;
        message = message == null || message.isBlank() ? name : message;
        severity = severity == null || severity.isBlank() ? "WARNING" : severity;
        originalFragment = originalFragment == null ? "" : originalFragment;
    }

    /** Constructor compatible con validaciones visuales previas. */
    /** Descripción: Inicializa una instancia de VisualImportedValidation con sus dependencias y datos de entrada. Entradas: source: dato de entrada necesario para la operación; name: dato de entrada necesario para la operación; representationId: identificador que delimita el recurso objetivo; expression: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; severity: dato de entrada necesario para la operación; originalFragment: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public VisualImportedValidation(String source,String name,String representationId,String expression,String message,
            String severity,String originalFragment) {
        this(source,"EXTERNAL",name,representationId,"",expression,message,severity,originalFragment);
    }
}
