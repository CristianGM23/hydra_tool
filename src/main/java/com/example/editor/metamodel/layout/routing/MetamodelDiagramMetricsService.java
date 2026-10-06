package com.example.editor.metamodel.layout.routing;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.editor.metamodel.design.MetamodelDiagramDocument;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.EdgeDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.NodeDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.RoutePointDesign;

/**
 * Mide cruces, intersecciones, giros, longitud y congestión.
 */
@Service
public class MetamodelDiagramMetricsService {

    /** Descripción: Ejecuta la operación «measure» correspondiente a la responsabilidad de MetamodelDiagramMetricsService. Entradas: diagram: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelDiagramMetrics measure(MetamodelDiagramDocument diagram) {
        int crossings = countCrossings(diagram.edges());
        int intersections = countEdgeNodeIntersections(diagram);
        int bends = 0;
        double totalLength = 0;
        Map<String, Integer> ports = new HashMap<>();

        for(EdgeDesign edge : diagram.edges()) {
            List<RoutePointDesign> route = edge.routePoints();
            bends += Math.max(0, route.size() - 2);

            for(int index = 0; index < route.size() - 1; index += 1) {
                totalLength += MetamodelRoutingGeometry.segmentLength(route.get(index), route.get(index + 1));
            }

            ports.merge(edge.sourceClassId() + "|" + edge.sourceHandle(), 1, Integer::sum);
            ports.merge(edge.targetClassId() + "|" + edge.targetHandle(), 1, Integer::sum);
        }

        int maximumPortCongestion = ports.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return new MetamodelDiagramMetrics(crossings, intersections, bends, Math.round(totalLength * 10.0) / 10.0, maximumPortCongestion);
    }

    /** Descripción: Ejecuta la operación «countCrossings» correspondiente a la responsabilidad de MetamodelDiagramMetricsService. Entradas: edges: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int countCrossings(List<EdgeDesign> edges) {
        int result = 0;

        for(int firstEdgeIndex = 0; firstEdgeIndex < edges.size(); firstEdgeIndex += 1) {
            EdgeDesign firstEdge = edges.get(firstEdgeIndex);

            for(int secondEdgeIndex = firstEdgeIndex + 1; secondEdgeIndex < edges.size(); secondEdgeIndex += 1) {
                EdgeDesign secondEdge = edges.get(secondEdgeIndex);

                if(sharesEndpoint(firstEdge, secondEdge)) {
                    continue;
                }

                for(int firstSegment = 0; firstSegment < firstEdge.routePoints().size() - 1; firstSegment += 1) {
                    for(int secondSegment = 0; secondSegment < secondEdge.routePoints().size() - 1; secondSegment += 1) {
                        if(MetamodelRoutingGeometry.segmentsCross(firstEdge.routePoints().get(firstSegment), firstEdge.routePoints().get(firstSegment + 1), secondEdge.routePoints().get(secondSegment), secondEdge.routePoints().get(secondSegment + 1))) {
                            result += 1;
                        }
                    }
                }
            }
        }

        return result;
    }

    /** Descripción: Ejecuta la operación «countEdgeNodeIntersections» correspondiente a la responsabilidad de MetamodelDiagramMetricsService. Entradas: diagram: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int countEdgeNodeIntersections(MetamodelDiagramDocument diagram) {
        int result = 0;

        for(EdgeDesign edge : diagram.edges()) {
            for(NodeDesign node : diagram.nodes()) {
                if(node.elementId().equals(edge.sourceClassId()) || node.elementId().equals(edge.targetClassId())) {
                    continue;
                }

                boolean intersects = false;

                for(int index = 0; index < edge.routePoints().size() - 1; index += 1) {
                    if(MetamodelRoutingGeometry.segmentIntersectsRectangle(edge.routePoints().get(index), edge.routePoints().get(index + 1), node, 0)) {
                        intersects = true;
                        break;
                    }
                }

                if(intersects) {
                    result += 1;
                }
            }
        }

        return result;
    }

    /** Descripción: Ejecuta la operación «sharesEndpoint» correspondiente a la responsabilidad de MetamodelDiagramMetricsService. Entradas: first: dato de entrada necesario para la operación; second: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean sharesEndpoint(EdgeDesign first, EdgeDesign second) {
        return first.sourceClassId().equals(second.sourceClassId()) || first.sourceClassId().equals(second.targetClassId()) || first.targetClassId().equals(second.sourceClassId()) || first.targetClassId().equals(second.targetClassId());
    }
}
