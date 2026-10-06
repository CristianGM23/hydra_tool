package com.example.editor.project;

/** Puerto para seleccionar rutas sin acoplar la aplicación a Swing, Tauri o Electron. */
public interface ProjectPathPicker {
    /** Descripción: Ejecuta la operación «pick» correspondiente a la responsabilidad de ProjectPathPicker. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ ProjectPathPickerResult pick(ProjectPathPickerRequest request);
}
