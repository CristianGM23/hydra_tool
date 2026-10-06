package com.example.editor.metamodel.export;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
@Component
public class BlocklyJavaScriptMetamodelExporter implements MetamodelTextExporter {
    private final BlocklyMetamodelExportSupport support;
    /** Descripción: Inicializa una instancia de BlocklyJavaScriptMetamodelExporter con sus dependencias y datos de entrada. Entradas: objectMapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public BlocklyJavaScriptMetamodelExporter(ObjectMapper objectMapper){ this.support=new BlocklyMetamodelExportSupport(objectMapper); }
    /** Descripción: Ejecuta la operación «format» correspondiente a la responsabilidad de BlocklyJavaScriptMetamodelExporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public MetamodelExportFormat format(){ return MetamodelExportFormat.BLOCKLY_JAVASCRIPT; }
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de BlocklyJavaScriptMetamodelExporter. Entradas: context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public String export(NeutralMetamodelExportContext context){ return support.javascript(context); }
}
