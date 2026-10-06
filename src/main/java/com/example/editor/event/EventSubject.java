package com.example.editor.event;
import com.example.editor.validation.contract.StableIdentifierPolicy;
/** Elemento principal afectado por un evento. */
public record EventSubject(EventSubjectKind kind, String documentId, String elementId, String typeId, String featureId, String path) {
    public EventSubject {
        if(kind == null) throw new IllegalArgumentException("subject.kind must not be null");
        documentId = StableIdentifierPolicy.requireDocumentId(documentId, "subject.documentId");
        elementId = normalizeElementId(elementId, "subject.elementId");
        typeId = normalizeElementId(typeId, "subject.typeId");
        featureId = normalizeElementId(featureId, "subject.featureId");
        path = path == null ? "" : path.trim();
    }
    /** Descripción: Ejecuta la operación «normalizeElementId» correspondiente a la responsabilidad de EventSubject. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalizeElementId(String value, String field) {
        if(value == null || value.isBlank()) return "";
        return StableIdentifierPolicy.requireElementId(value, field);
    }
}
