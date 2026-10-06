package com.example.editor.metamodel.importing.importers;
import org.springframework.stereotype.Component;
import com.example.editor.metamodel.importing.*;
import tools.jackson.databind.ObjectMapper;
@Component
public class BlocklyJavaScriptMetamodelImporter implements MetamodelImporter {
    private final BlocklyMetamodelImportSupport support;
    /** Descripción: Inicializa una instancia de BlocklyJavaScriptMetamodelImporter con sus dependencias y datos de entrada. Entradas: objectMapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public BlocklyJavaScriptMetamodelImporter(ObjectMapper objectMapper){ this.support=new BlocklyMetamodelImportSupport(objectMapper); }
    /** Descripción: Ejecuta la operación «format» correspondiente a la responsabilidad de BlocklyJavaScriptMetamodelImporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public MetamodelImportFormat format(){ return MetamodelImportFormat.BLOCKLY_JAVASCRIPT; }
    /** Descripción: Ejecuta la operación «importMetamodel» correspondiente a la responsabilidad de BlocklyJavaScriptMetamodelImporter. Entradas: source: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ @Override public MetamodelImportOutput importMetamodel(MetamodelImportSource source, MetamodelImportOptions options){ return support.importSource(source, options, true); }
}
