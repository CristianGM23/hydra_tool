package com.example.editor.definition.runtime.session;

/**
 * Contexto mínimo e inmutable necesario para publicar un evento de una sesión
 * de editor. Evita retener el modelo completo mientras una sesión sin autosave
 * mantiene eventos pendientes de commit.
 */
public record EditorSessionEventContext(String sessionId,String metamodelId,String modelId,String editorId,long revision) {
    public EditorSessionEventContext {
        sessionId=normalize(sessionId);
        metamodelId=normalize(metamodelId);
        modelId=normalize(modelId);
        editorId=normalize(editorId);
        if(sessionId.isBlank()) throw new IllegalArgumentException("Session id is required");
        if(metamodelId.isBlank()) throw new IllegalArgumentException("Metamodel id is required");
        if(modelId.isBlank()) throw new IllegalArgumentException("Model id is required");
        if(editorId.isBlank()) throw new IllegalArgumentException("Editor id is required");
        if(revision<0) throw new IllegalArgumentException("Session revision cannot be negative");
    }

    /** Descripción: Ejecuta la operación «from» correspondiente a la responsabilidad de EditorSessionEventContext. Entradas: session: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static EditorSessionEventContext from(EditorModelSession session) {
        if(session==null) throw new IllegalArgumentException("Editor session is required");
        return new EditorSessionEventContext(session.id(),session.metamodelId(),session.model().id(),session.editorId(),session.revision());
    }

    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de EditorSessionEventContext. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalize(String value) { return value==null?"":value.trim(); }
}
