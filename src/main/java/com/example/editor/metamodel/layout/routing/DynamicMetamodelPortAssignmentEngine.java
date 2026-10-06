package com.example.editor.metamodel.layout.routing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.EdgeDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.NodeDesign;
import com.example.editor.metamodel.layout.MetamodelLayoutOptions;
import com.example.editor.metamodel.layout.MetamodelLayoutProperties;

/**
 * Asigna puertos dinámicos ordenados según la posición de los destinos.
 */
@Component
public class DynamicMetamodelPortAssignmentEngine implements MetamodelPortAssignmentEngine {

    private final MetamodelLayoutProperties properties;

    /** Descripción: Inicializa una instancia de DynamicMetamodelPortAssignmentEngine con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public DynamicMetamodelPortAssignmentEngine(MetamodelLayoutProperties properties) {
        this.properties = properties;
    }

    @Override
    /** Descripción: Ejecuta la operación «id» correspondiente a la responsabilidad de PortRequest. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String id() {
        return "dynamic";
    }

    @Override
    /** Descripción: Ejecuta la operación «assign» correspondiente a la responsabilidad de PortRequest. Entradas: metamodel: dato de entrada necesario para la operación; diagram: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelDiagramDocument assign(MetaModel metamodel, MetamodelDiagramDocument diagram, MetamodelLayoutOptions options) {
        Map<String, NodeDesign> nodes = indexNodes(diagram);
        List<PortRequest> sourceRequests = new ArrayList<>();
        List<PortRequest> targetRequests = new ArrayList<>();
        Map<String, EdgeDesign> edgeById = new LinkedHashMap<>();

        for(EdgeDesign edge : diagram.edges()) {
            NodeDesign source = nodes.get(edge.sourceClassId());
            NodeDesign target = nodes.get(edge.targetClassId());

            if(source == null || target == null) {
                continue;
            }

            EdgeSides sides = chooseSides(source, target, edge);
            sourceRequests.add(new PortRequest(edge.relationId(), source.elementId(), sides.sourceSide(), true, otherCoordinate(target, sides.sourceSide()), source, target));
            targetRequests.add(new PortRequest(edge.relationId(), target.elementId(), sides.targetSide(), false, otherCoordinate(source, sides.targetSide()), target, source));
            edgeById.put(edge.relationId(), edge);
        }

        rebalance(sourceRequests);
        rebalance(targetRequests);
        Map<String, String> sourceHandles = assignHandles(sourceRequests);
        Map<String, String> targetHandles = assignHandles(targetRequests);
        List<EdgeDesign> resultEdges = new ArrayList<>();

        for(EdgeDesign edge : diagram.edges()) {
            String sourceHandle = sourceHandles.getOrDefault(edge.relationId(), edge.sourceHandle());
            String targetHandle = targetHandles.getOrDefault(edge.relationId(), edge.targetHandle());
            resultEdges.add(new EdgeDesign(edge.relationId(), sourceHandle, targetHandle, edge.kind(), edge.sourceClassId(), edge.targetClassId(), edge.label(), edge.style(), List.of(), null));
        }

        return new MetamodelDiagramDocument(diagram.metamodelId(), diagram.nodes(), resultEdges);
    }

    /** Descripción: Ejecuta la operación «chooseSides» correspondiente a la responsabilidad de PortRequest. Entradas: source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; edge: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EdgeSides chooseSides(NodeDesign source, NodeDesign target, EdgeDesign edge) {
        if(source.elementId().equals(target.elementId())) {
            return new EdgeSides("right", "bottom");
        }

        double sourceCenterX = source.x() + source.width() / 2;
        double sourceCenterY = source.y() + source.height() / 2;
        double targetCenterX = target.x() + target.width() / 2;
        double targetCenterY = target.y() + target.height() / 2;
        double dx = targetCenterX - sourceCenterX;
        double dy = targetCenterY - sourceCenterY;
        double horizontalScore = Math.abs(dx) / Math.max(1, source.width() + target.width());
        double verticalScore = Math.abs(dy) / Math.max(1, source.height() + target.height());

        if(horizontalScore >= verticalScore * 0.75) {
            return dx >= 0 ? new EdgeSides("right", "left") : new EdgeSides("left", "right");
        }

        return dy >= 0 ? new EdgeSides("bottom", "top") : new EdgeSides("top", "bottom");
    }

    /** Descripción: Ejecuta la operación «rebalance» correspondiente a la responsabilidad de PortRequest. Entradas: requests: solicitud con los datos necesarios para ejecutar la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void rebalance(List<PortRequest> requests) {
        Map<String, List<PortRequest>> groups = group(requests);

        for(List<PortRequest> group : groups.values()) {
            if(group.isEmpty()) {
                continue;
            }

            PortRequest sample = group.get(0);
            int capacity = capacity(sample.node(), sample.side());

            if(group.size() <= capacity) {
                continue;
            }

            List<PortRequest> overflow = new ArrayList<>(group.subList(capacity, group.size()));

            for(int index = 0; index < overflow.size(); index += 1) {
                PortRequest request = overflow.get(index);
                String alternative = alternativeSide(request, index);
                request.side = alternative;
                request.sortCoordinate = otherCoordinate(request.otherNode(), alternative);
            }
        }
    }

    /** Descripción: Ejecuta la operación «alternativeSide» correspondiente a la responsabilidad de PortRequest. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; index: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String alternativeSide(PortRequest request, int index) {
        if("left".equals(request.side) || "right".equals(request.side)) {
            return index % 2 == 0 ? "top" : "bottom";
        }

        return index % 2 == 0 ? "left" : "right";
    }

    /** Descripción: Ejecuta la operación «assignHandles» correspondiente a la responsabilidad de PortRequest. Entradas: requests: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, String> assignHandles(List<PortRequest> requests) {
        Map<String, String> result = new HashMap<>();
        Map<String, List<PortRequest>> groups = group(requests);

        for(List<PortRequest> group : groups.values()) {
            group.sort(Comparator.comparingDouble(PortRequest::sortCoordinate).thenComparing(PortRequest::relationId));
            PortRequest sample = group.get(0);
            double dimension = sideDimension(sample.node(), sample.side());
            double marginPercent = Math.min(32, properties.getPortMargin() / Math.max(1, dimension) * 100);
            double available = Math.max(10, 100 - marginPercent * 2);
            double step = available / (group.size() + 1);

            for(int index = 0; index < group.size(); index += 1) {
                double percent = marginPercent + step * (index + 1);
                result.put(group.get(index).relationId(), group.get(index).side() + "-" + formatPercent(percent));
            }
        }

        return result;
    }

    /** Descripción: Ejecuta la operación «group» correspondiente a la responsabilidad de PortRequest. Entradas: requests: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, List<PortRequest>> group(List<PortRequest> requests) {
        Map<String, List<PortRequest>> result = new LinkedHashMap<>();

        for(PortRequest request : requests) {
            String key = request.nodeId() + "|" + request.side() + "|" + request.source();
            result.computeIfAbsent(key, ignored -> new ArrayList<>()).add(request);
        }

        return result;
    }

    /** Descripción: Ejecuta la operación «capacity» correspondiente a la responsabilidad de PortRequest. Entradas: node: dato de entrada necesario para la operación; side: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int capacity(NodeDesign node, String side) {
        double dimension = sideDimension(node, side);
        double available = Math.max(1, dimension - properties.getPortMargin() * 2);
        int geometricCapacity = (int) Math.floor(available / Math.max(4, properties.getMinimumPortGap())) + 1;
        return Math.max(2, Math.min(Math.max(2, properties.getMaximumPortsPerSide()), geometricCapacity));
    }

    /** Descripción: Ejecuta la operación «sideDimension» correspondiente a la responsabilidad de PortRequest. Entradas: node: dato de entrada necesario para la operación; side: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private double sideDimension(NodeDesign node, String side) {
        return "left".equals(side) || "right".equals(side) ? node.height() : node.width();
    }

    /** Descripción: Ejecuta la operación «otherCoordinate» correspondiente a la responsabilidad de PortRequest. Entradas: node: dato de entrada necesario para la operación; side: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private double otherCoordinate(NodeDesign node, String side) {
        if("left".equals(side) || "right".equals(side)) {
            return node.y() + node.height() / 2;
        }

        return node.x() + node.width() / 2;
    }

    /** Descripción: Ejecuta la operación «formatPercent» correspondiente a la responsabilidad de PortRequest. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String formatPercent(double value) {
        double rounded = Math.round(value * 10.0) / 10.0;

        if(Math.abs(rounded - Math.rint(rounded)) < 0.0001) {
            return Integer.toString((int) Math.rint(rounded));
        }

        return Double.toString(rounded);
    }

    /** Descripción: Ejecuta la operación «indexNodes» correspondiente a la responsabilidad de PortRequest. Entradas: diagram: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, NodeDesign> indexNodes(MetamodelDiagramDocument diagram) {
        Map<String, NodeDesign> result = new LinkedHashMap<>();
        diagram.nodes().forEach(item -> result.put(item.elementId(), item));
        return result;
    }

    private record EdgeSides(String sourceSide, String targetSide) {
    }

    private static final class PortRequest {

        private final String relationId;
        private final String nodeId;
        private final boolean source;
        private final NodeDesign node;
        private final NodeDesign otherNode;
        private String side;
        private double sortCoordinate;

        /** Descripción: Inicializa una instancia de PortRequest con sus dependencias y datos de entrada. Entradas: relationId: identificador que delimita el recurso objetivo; nodeId: identificador que delimita el recurso objetivo; side: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; sortCoordinate: dato de entrada necesario para la operación; node: dato de entrada necesario para la operación; otherNode: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private PortRequest(String relationId, String nodeId, String side, boolean source, double sortCoordinate, NodeDesign node, NodeDesign otherNode) {
            this.relationId = relationId;
            this.nodeId = nodeId;
            this.side = side;
            this.source = source;
            this.sortCoordinate = sortCoordinate;
            this.node = node;
            this.otherNode = otherNode;
        }

        /** Descripción: Ejecuta la operación «relationId» correspondiente a la responsabilidad de PortRequest. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private String relationId() {
            return relationId;
        }

        /** Descripción: Ejecuta la operación «nodeId» correspondiente a la responsabilidad de PortRequest. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private String nodeId() {
            return nodeId;
        }

        /** Descripción: Ejecuta la operación «side» correspondiente a la responsabilidad de PortRequest. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private String side() {
            return side;
        }

        /** Descripción: Ejecuta la operación «source» correspondiente a la responsabilidad de PortRequest. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean source() {
            return source;
        }

        /** Descripción: Ejecuta la operación «sortCoordinate» correspondiente a la responsabilidad de PortRequest. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private double sortCoordinate() {
            return sortCoordinate;
        }

        /** Descripción: Ejecuta la operación «node» correspondiente a la responsabilidad de PortRequest. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private NodeDesign node() {
            return node;
        }

        /** Descripción: Ejecuta la operación «otherNode» correspondiente a la responsabilidad de PortRequest. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private NodeDesign otherNode() {
            return otherNode;
        }
    }
}
