package com.example.editor.metamodel.export;

import java.util.Map;

import com.example.editor.metamodel.design.MetamodelDiagramDocument.EdgeDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.LabelPositionDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.NodeDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.RoutePointDesign;

/**
 * Calcula el rectángulo completo que debe incluir una exportación visual.
 */
final class DiagramExportBounds {

    private static final double MARGIN = 40;
    private static final double LABEL_HORIZONTAL_MARGIN = 90;
    private static final double LABEL_VERTICAL_MARGIN = 18;

    /** Descripción: Inicializa una instancia de DiagramExportBounds con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private DiagramExportBounds() {
    }

    /** Descripción: Ejecuta la operación «calculate» correspondiente a la responsabilidad de Bounds. Entradas: nodes: dato de entrada necesario para la operación; edges: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static Bounds calculate(Map<String, NodeDesign> nodes, Map<String, EdgeDesign> edges) {
        if(nodes.isEmpty()) {
            return new Bounds(0, 0, 960, 600);
        }

        MutableBounds bounds = new MutableBounds();

        for(NodeDesign node : nodes.values()) {
            bounds.include(node.x(), node.y());
            bounds.include(node.x() + node.width(), node.y() + node.height());
        }

        for(EdgeDesign edge : edges.values()) {
            for(RoutePointDesign point : edge.routePoints()) {
                bounds.include(point.x(), point.y());
            }

            LabelPositionDesign label = edge.labelPosition();

            if(label != null) {
                bounds.include(label.x() - LABEL_HORIZONTAL_MARGIN, label.y() - LABEL_VERTICAL_MARGIN);
                bounds.include(label.x() + LABEL_HORIZONTAL_MARGIN, label.y() + LABEL_VERTICAL_MARGIN);
            }
        }

        double x = bounds.minX - MARGIN;
        double y = bounds.minY - MARGIN;
        double width = Math.max(300, bounds.maxX - bounds.minX + MARGIN * 2);
        double height = Math.max(200, bounds.maxY - bounds.minY + MARGIN * 2);
        return new Bounds(x, y, width, height);
    }

    record Bounds(double x, double y, double width, double height) {
    }

    private static final class MutableBounds {

        private double minX = Double.POSITIVE_INFINITY;
        private double minY = Double.POSITIVE_INFINITY;
        private double maxX = Double.NEGATIVE_INFINITY;
        private double maxY = Double.NEGATIVE_INFINITY;

        /** Descripción: Ejecuta la operación «include» correspondiente a la responsabilidad de Bounds. Entradas: x: dato de entrada necesario para la operación; y: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void include(double x, double y) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
    }
}
