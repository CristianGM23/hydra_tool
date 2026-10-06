package com.example.editor.project;

/** Solicitud neutral para abrir un selector de rutas del sistema anfitrión. */
public record ProjectPathPickerRequest(ProjectPathPickerMode mode,String title,String initialDirectory) {
    public ProjectPathPickerRequest {
        mode = mode == null ? ProjectPathPickerMode.DIRECTORY : mode;
        title = title == null ? "" : title.trim();
        initialDirectory = initialDirectory == null ? "" : initialDirectory.trim();
    }
}
