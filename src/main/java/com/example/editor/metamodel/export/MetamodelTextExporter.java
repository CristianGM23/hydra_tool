package com.example.editor.metamodel.export;

/**
 * Define un exportador textual neutral.
 */
public interface MetamodelTextExporter {

    /** Descripción: Ejecuta la operación «format» correspondiente a la responsabilidad de MetamodelTextExporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ MetamodelExportFormat format();

    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de MetamodelTextExporter. Entradas: context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ String export(NeutralMetamodelExportContext context);
}
