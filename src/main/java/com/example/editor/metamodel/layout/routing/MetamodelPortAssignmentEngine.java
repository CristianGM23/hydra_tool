package com.example.editor.metamodel.layout.routing;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;
import com.example.editor.metamodel.layout.MetamodelLayoutOptions;

/**
 * Asigna puertos independientes a las relaciones.
 */
public interface MetamodelPortAssignmentEngine {

    /** Descripción: Ejecuta la operación «id» correspondiente a la responsabilidad de MetamodelPortAssignmentEngine. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ String id();

    /** Descripción: Ejecuta la operación «assign» correspondiente a la responsabilidad de MetamodelPortAssignmentEngine. Entradas: metamodel: dato de entrada necesario para la operación; diagram: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ MetamodelDiagramDocument assign(MetaModel metamodel, MetamodelDiagramDocument diagram, MetamodelLayoutOptions options);
}
