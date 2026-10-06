package com.example.editor.metamodel.export;

/**
 * Define un exportador binario neutral.
 */
public interface MetamodelBinaryExporter {

    /** Descripción: Ejecuta la operación «format» correspondiente a la responsabilidad de MetamodelBinaryExporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ MetamodelExportFormat format();

    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de MetamodelBinaryExporter. Entradas: context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ byte[] export(NeutralMetamodelExportContext context);
}
