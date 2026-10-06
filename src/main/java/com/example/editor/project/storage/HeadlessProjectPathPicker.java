package com.example.editor.project.storage;


import com.example.editor.project.ProjectPathPicker;
import com.example.editor.project.ProjectPathPickerRequest;
import com.example.editor.project.ProjectPathPickerResult;

/** Headless-safe fallback. The core never needs to initialize Swing when the desktop picker is disabled. */
public class HeadlessProjectPathPicker implements ProjectPathPicker {
    @Override
    /** Descripción: Ejecuta la operación «pick» correspondiente a la responsabilidad de HeadlessProjectPathPicker. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ProjectPathPickerResult pick(ProjectPathPickerRequest request) {
        return ProjectPathPickerResult.unavailable(request.mode(),
                "The native path picker is unavailable in this runtime profile. Enter or upload the path explicitly.");
    }
}
