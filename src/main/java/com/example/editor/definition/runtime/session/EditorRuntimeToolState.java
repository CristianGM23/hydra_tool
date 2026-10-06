package com.example.editor.definition.runtime.session;

/** Disponibilidad calculada de una herramienta para una sesión y selección concretas. */
public record EditorRuntimeToolState(String toolId, boolean visible, boolean enabled, String reason) {
    public EditorRuntimeToolState {
        toolId = toolId == null ? "" : toolId;
        reason = reason == null ? "" : reason;
    }
}
