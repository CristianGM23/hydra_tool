package com.example.editor.definition.runtime.session;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

import com.example.editor.validation.runtime.ModelValidationReport;

import com.example.editor.definition.runtime.EditorRuntimeContractVersions;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Sesión neutral y portable ejecutada por un editor configurable. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EditorModelSession(String schemaVersion, String id, String metamodelId, String editorId,
        String sourceEditorEtag, String activeRepresentationId, List<String> openRepresentationIds,
        CanonicalModelDocument model, ModelValidationReport latestValidationReport,
        long revision, Instant createdAt, Instant updatedAt, Map<String, String> annotations) {
    public EditorModelSession {
        if(schemaVersion == null || schemaVersion.isBlank()) schemaVersion = EditorRuntimeContractVersions.EDITOR_MODEL_SESSION;
        if(!EditorRuntimeContractVersions.EDITOR_MODEL_SESSION.equals(schemaVersion)) throw new IllegalArgumentException("Unsupported editor session schemaVersion: " + schemaVersion);
        id = StableIdentifierPolicy.requireDocumentId(id, "session id");
        metamodelId = StableIdentifierPolicy.requireDocumentId(metamodelId, "metamodel id");
        editorId = StableIdentifierPolicy.requireDocumentId(editorId, "editor id");
        if(model == null) throw new IllegalArgumentException("Canonical model is required");
        if(!metamodelId.equals(model.metamodelId())) throw new IllegalArgumentException("Session and model metamodel ids must match");
        sourceEditorEtag = sourceEditorEtag == null ? "" : sourceEditorEtag;
        activeRepresentationId = activeRepresentationId == null ? "" : activeRepresentationId;
        openRepresentationIds = openRepresentationIds == null || openRepresentationIds.isEmpty()
                ? (activeRepresentationId.isBlank() ? List.of() : List.of(activeRepresentationId))
                : openRepresentationIds.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
        if(!activeRepresentationId.isBlank() && !openRepresentationIds.contains(activeRepresentationId)) {
            java.util.ArrayList<String> opened = new java.util.ArrayList<>(openRepresentationIds);
            opened.add(0, activeRepresentationId);
            openRepresentationIds = List.copyOf(opened);
        }
        revision = Math.max(0, revision);
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
        annotations = annotations == null || annotations.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
    }


    /** Descripción: Inicializa una instancia de EditorModelSession con sus dependencias y datos de entrada. Entradas: schemaVersion: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sourceEditorEtag: dato de entrada necesario para la operación; activeRepresentationId: identificador que delimita el recurso objetivo; model: dato de entrada necesario para la operación; revision: dato de entrada necesario para la operación; createdAt: dato de entrada necesario para la operación; updatedAt: dato de entrada necesario para la operación; annotations: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EditorModelSession(String schemaVersion, String id, String metamodelId, String editorId,
            String sourceEditorEtag, String activeRepresentationId, CanonicalModelDocument model,
            long revision, Instant createdAt, Instant updatedAt, Map<String, String> annotations) {
        this(schemaVersion, id, metamodelId, editorId, sourceEditorEtag, activeRepresentationId,
                activeRepresentationId == null || activeRepresentationId.isBlank() ? List.of() : List.of(activeRepresentationId),
                model, null, revision, createdAt, updatedAt, annotations);
    }

    /** Descripción: Ejecuta la operación «withValidation» correspondiente a la responsabilidad de EditorModelSession. Entradas: report: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EditorModelSession withValidation(ModelValidationReport report) {
        return new EditorModelSession(schemaVersion, id, metamodelId, editorId, sourceEditorEtag,
                activeRepresentationId, openRepresentationIds, model, report, revision, createdAt, Instant.now(), annotations);
    }

    /** Descripción: Guarda una preferencia visual de runtime sin alterar el modelo semántico. Entradas: key: clave de sesión; value: valor de la preferencia. Salida: devuelve una nueva sesión con la anotación actualizada. */
    /** Descripción: Ejecuta la operación «withAnnotation» correspondiente a la responsabilidad de EditorModelSession. Entradas: key: identificador que delimita el recurso objetivo; value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public EditorModelSession withAnnotation(String key, String value) {
        if(key == null || key.isBlank()) throw new IllegalArgumentException("Session annotation key must not be blank");
        Map<String,String> next = new LinkedHashMap<>(annotations);
        if(value == null || value.isBlank()) next.remove(key); else next.put(key, value);
        return new EditorModelSession(schemaVersion, id, metamodelId, editorId, sourceEditorEtag,
                activeRepresentationId, openRepresentationIds, model, latestValidationReport, revision + 1, createdAt, Instant.now(), next);
    }

    /** Descripción: Crea o prepara el resultado de «openRepresentation» dentro de EditorModelSession. Entradas: representationId: identificador que delimita el recurso objetivo; maximumOpen: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EditorModelSession openRepresentation(String representationId, int maximumOpen) {
        java.util.ArrayList<String> opened = new java.util.ArrayList<>(openRepresentationIds);
        opened.remove(representationId);
        opened.add(representationId);
        while(opened.size() > Math.max(1, maximumOpen)) opened.remove(0);
        return new EditorModelSession(schemaVersion, id, metamodelId, editorId, sourceEditorEtag,
                representationId, opened, model, latestValidationReport, revision + 1, createdAt, Instant.now(), annotations);
    }

    /** Descripción: Elimina o libera el recurso gestionado por «closeRepresentation» de forma controlada. Entradas: representationId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public EditorModelSession closeRepresentation(String representationId) {
        if(openRepresentationIds.size() <= 1 || !openRepresentationIds.contains(representationId)) return this;
        java.util.ArrayList<String> opened = new java.util.ArrayList<>(openRepresentationIds);
        opened.remove(representationId);
        String active = activeRepresentationId.equals(representationId) ? opened.get(opened.size() - 1) : activeRepresentationId;
        return new EditorModelSession(schemaVersion, id, metamodelId, editorId, sourceEditorEtag,
                active, opened, model, latestValidationReport, revision + 1, createdAt, Instant.now(), annotations);
    }

    /** Descripción: Actualiza el estado gestionado por EditorModelSession mediante la operación «update». Entradas: nextModel: dato de entrada necesario para la operación; representationId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public EditorModelSession update(CanonicalModelDocument nextModel, String representationId) {
        String nextRepresentation = representationId == null ? activeRepresentationId : representationId;
        java.util.ArrayList<String> opened = new java.util.ArrayList<>(openRepresentationIds);
        if(!nextRepresentation.isBlank() && !opened.contains(nextRepresentation)) opened.add(nextRepresentation);
        return new EditorModelSession(schemaVersion, id, metamodelId, editorId, sourceEditorEtag,
                nextRepresentation, opened, nextModel, latestValidationReport,
                revision + 1, createdAt, Instant.now(), annotations);
    }
}
