package com.example.editor.metamodel.layout.routing;

/**
 * Contiene métricas objetivas del diagrama.
 *
 * @param crossings contiene cruces entre relaciones.
 * @param edgeNodeIntersections contiene relaciones que atraviesan nodos.
 * @param bends contiene giros.
 * @param totalLength contiene longitud ortogonal total.
 * @param maximumPortCongestion contiene el máximo de relaciones por puerto.
 */
public record MetamodelDiagramMetrics(int crossings, int edgeNodeIntersections, int bends, double totalLength, int maximumPortCongestion) {
}
