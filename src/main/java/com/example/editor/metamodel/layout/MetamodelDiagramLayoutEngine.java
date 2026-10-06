package com.example.editor.metamodel.layout;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;

/**
 * Define un algoritmo intercambiable de layout.
 */
public interface MetamodelDiagramLayoutEngine {

    /**
     * Descripción:
     * Devuelve el identificador estable del algoritmo.
     *
     * @return el identificador.
     */
    /** Descripción: Ejecuta la operación «id» correspondiente a la responsabilidad de MetamodelDiagramLayoutEngine. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ String id();

    /**
     * Descripción:
     * Organiza el metamodelo.
     *
     * @param metamodel contiene la semántica neutral.
     * @param currentDiagram contiene el diagrama actual opcional.
     * @param options contiene las opciones.
     * @return el resultado del layout.
     */
    /** Descripción: Ejecuta la operación «layout» correspondiente a la responsabilidad de MetamodelDiagramLayoutEngine. Entradas: metamodel: dato de entrada necesario para la operación; currentDiagram: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ MetamodelLayoutResult layout(MetaModel metamodel, MetamodelDiagramDocument currentDiagram, MetamodelLayoutOptions options);
}
