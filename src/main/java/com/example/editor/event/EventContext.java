package com.example.editor.event;
/** Contexto portable de una ejecución o sesión. */
public record EventContext(EventContextKind kind, String workspaceId, String projectId, String metamodelId, String modelId, String editorId, String sessionId) {
    public EventContext {
        if(kind == null) kind = EventContextKind.SYSTEM;
        workspaceId = normalize(workspaceId);
        projectId = normalize(projectId);
        metamodelId = normalize(metamodelId);
        modelId = normalize(modelId);
        editorId = normalize(editorId);
        sessionId = normalize(sessionId);
    }
    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de EventContext. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalize(String value) { return value == null ? "" : value.trim(); }
}
