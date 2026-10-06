package com.example.editor.metamodel.layout.routing;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;
import com.example.editor.metamodel.layout.MetamodelLayoutOptions;

/**
 * Calcula rutas para las relaciones.
 */
public interface MetamodelEdgeRoutingEngine {

    /** Descripción: Ejecuta la operación «id» correspondiente a la responsabilidad de MetamodelEdgeRoutingEngine. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ String id();

    /** Descripción: Ejecuta la operación «route» correspondiente a la responsabilidad de MetamodelEdgeRoutingEngine. Entradas: metamodel: dato de entrada necesario para la operación; diagram: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ MetamodelDiagramDocument route(MetaModel metamodel, MetamodelDiagramDocument diagram, MetamodelLayoutOptions options);
}
