package com.example.editor.metamodel.importing;

/**
 * Define el procesamiento analítico utilizado por los trabajos asíncronos.
 */
public interface MetamodelImportAnalysisProcessor {

    /**
     * Analiza un archivo y notifica sus fases.
     *
     * @param fileName contiene el nombre del archivo.
     * @param mediaType contiene el tipo MIME.
     * @param content contiene los bytes.
     * @param options contiene la configuración.
     * @param progressListener recibe los avances.
     * @return la previsualización editable.
     */
    /** Descripción: Ejecuta la operación «preview» correspondiente a la responsabilidad de MetamodelImportAnalysisProcessor. Entradas: fileName: archivo o contenido que debe procesarse; mediaType: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse; options: configuración que gobierna el comportamiento; progressListener: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ MetamodelImportPreview preview(String fileName, String mediaType, byte[] content, MetamodelImportOptions options, MetamodelImportProgressListener progressListener);
}
