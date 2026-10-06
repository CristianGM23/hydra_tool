package com.example.editor.persistence;

import com.example.editor.scope.ResourceScope;

/** Evento neutral de persistencia para observadores transversales como auditoría. */
public record CanonicalDocumentMutation(String namespace, ResourceScope scope, String documentId,
        CanonicalDocumentMutationKind kind, Object before, Object after) { }
