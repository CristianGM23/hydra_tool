package com.example.editor.diagram;

import java.util.List;

/**
 * Representa la información visual asociada a un modelo XMI.
 *
 * @param formatVersion versión del formato visual.
 * @param modelFileName nombre del archivo XMI.
 * @param notationId identificador de la notación.
 * @param nodes posiciones, tamaños y representaciones de los nodos.
 * @param edges estilos seleccionados para las conexiones.
 */
public record DiagramDocument(int formatVersion, String modelFileName, String notationId, List<DiagramNodeView> nodes, List<DiagramEdgeView> edges) {

    /**
     * Representa el estado visual de un EObject.
     *
     * @param elementId identificador del elemento semántico.
     * @param nodeStyleId representación visual seleccionada.
     * @param x posición horizontal.
     * @param y posición vertical.
     * @param width anchura actual.
     * @param height altura actual.
     */
    public record DiagramNodeView(String elementId, String nodeStyleId, double x, double y, double width, double height) {
    }

    /**
     * Representa el estilo visual elegido para una referencia.
     *
     * @param sourceId identificador del elemento de origen.
     * @param referenceName nombre de la referencia semántica.
     * @param targetId identificador del elemento de destino.
     * @param edgeStyleId estilo visual seleccionado.
     */
    public record DiagramEdgeView(String sourceId, String referenceName, String targetId, String edgeStyleId) {
    }
}