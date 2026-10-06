package com.example.editor.metamodel.layout;

/**
 * Resume la calidad y el tamaño del resultado.
 *
 * @param nodes contiene el número de nodos.
 * @param edges contiene el número de relaciones.
 * @param components contiene el número de componentes.
 * @param layers contiene el número máximo de capas.
 * @param cycles contiene los componentes cíclicos.
 * @param crossings contiene los cruces entre relaciones.
 * @param edgeNodeIntersections contiene relaciones que atraviesan nodos.
 * @param bends contiene el número total de giros.
 * @param totalLength contiene la longitud total.
 * @param maximumPortCongestion contiene la congestión máxima de un puerto.
 */
public record MetamodelLayoutStatistics(int nodes, int edges, int components, int layers, int cycles, int crossings, int edgeNodeIntersections, int bends, double totalLength, int maximumPortCongestion) {

    /** Descripción: Inicializa una instancia de MetamodelLayoutStatistics con sus dependencias y datos de entrada. Entradas: nodes: dato de entrada necesario para la operación; edges: dato de entrada necesario para la operación; components: dato de entrada necesario para la operación; layers: dato de entrada necesario para la operación; cycles: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelLayoutStatistics(int nodes, int edges, int components, int layers, int cycles) {
        this(nodes, edges, components, layers, cycles, 0, 0, 0, 0, 0);
    }
}
