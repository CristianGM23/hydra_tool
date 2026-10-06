package com.example.editor.metamodel.importing;

/**
 * Define un importador desacoplado del controlador y la persistencia.
 */
public interface MetamodelImporter {

    /** Descripción: Ejecuta la operación «format» correspondiente a la responsabilidad de MetamodelImporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ MetamodelImportFormat format();

    /** Descripción: Ejecuta la operación «importMetamodel» correspondiente a la responsabilidad de MetamodelImporter. Entradas: source: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ MetamodelImportOutput importMetamodel(MetamodelImportSource source, MetamodelImportOptions options);
}
