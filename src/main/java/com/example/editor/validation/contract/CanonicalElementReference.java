package com.example.editor.validation.contract;

/**
 * Referencia un elemento canónico sin copiarlo y sin depender de su nombre.
 *
 * @param documentId identifica el documento propietario.
 * @param elementId identifica el elemento estable dentro del documento.
 */
public record CanonicalElementReference(String documentId, String elementId) {

    public CanonicalElementReference {
        documentId = StableIdentifierPolicy.requireDocumentId(documentId, "documentId");
        elementId = StableIdentifierPolicy.requireElementId(elementId, "elementId");
    }
}
