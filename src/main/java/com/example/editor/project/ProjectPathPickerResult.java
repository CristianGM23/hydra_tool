package com.example.editor.project;

/** Resultado portable del selector nativo de archivos o directorios. */
public record ProjectPathPickerResult(boolean available,boolean cancelled,String selectedPath,String selectedDirectory,String fileName,ProjectPathPickerMode mode,String message) {
    public ProjectPathPickerResult {
        selectedPath = selectedPath == null ? "" : selectedPath;
        selectedDirectory = selectedDirectory == null ? "" : selectedDirectory;
        fileName = fileName == null ? "" : fileName;
        mode = mode == null ? ProjectPathPickerMode.DIRECTORY : mode;
        message = message == null ? "" : message;
    }

    /** Descripción: Ejecuta la operación «unavailable» correspondiente a la responsabilidad de ProjectPathPickerResult. Entradas: mode: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static ProjectPathPickerResult unavailable(ProjectPathPickerMode mode,String message) {
        return new ProjectPathPickerResult(false,false,"","","",mode,message);
    }

    /** Descripción: Evalúa la condición «cancelled» sin modificar el estado externo. Entradas: mode: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public static ProjectPathPickerResult cancelled(ProjectPathPickerMode mode) {
        return new ProjectPathPickerResult(true,true,"","","",mode,"Selection cancelled");
    }
}
