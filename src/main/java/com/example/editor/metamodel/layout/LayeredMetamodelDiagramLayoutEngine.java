package com.example.editor.metamodel.layout;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaDataType;
import com.example.editor.metamodel.core.MetaEnum;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaTypeReferenceKind;
import com.example.editor.metamodel.core.TransformationWarning;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.EdgeDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.NodeDesign;
import com.example.editor.metamodel.layout.routing.DynamicMetamodelPortAssignmentEngine;
import com.example.editor.metamodel.layout.routing.LongestSegmentMetamodelEdgeLabelPlacementEngine;
import com.example.editor.metamodel.layout.routing.MetamodelDiagramMetrics;
import com.example.editor.metamodel.layout.routing.MetamodelDiagramMetricsService;
import com.example.editor.metamodel.layout.routing.MetamodelEdgeLabelPlacementEngineRegistry;
import com.example.editor.metamodel.layout.routing.MetamodelEdgeRoutingEngineRegistry;
import com.example.editor.metamodel.layout.routing.MetamodelPortAssignmentEngineRegistry;
import com.example.editor.metamodel.layout.routing.MetamodelRoutingPipeline;
import com.example.editor.metamodel.layout.routing.OrthogonalMetamodelEdgeRoutingEngine;

/**
 * Organiza el metamodelo por capas y reduce cruces mediante ordenación baricéntrica.
 */
@Component
public class LayeredMetamodelDiagramLayoutEngine implements MetamodelDiagramLayoutEngine {

    private static final double DEFAULT_CLASS_WIDTH = 240;
    private static final double DEFAULT_ENUM_WIDTH = 220;
    private static final double DEFAULT_DATA_TYPE_WIDTH = 220;
    private static final double DEFAULT_ROW_HEIGHT = 24;
    private static final double DEFAULT_CLASS_HEADER = 78;
    private static final double DEFAULT_ENUM_HEADER = 58;
    private static final double DEFAULT_DATA_TYPE_HEIGHT = 105;

    private final MetamodelLayoutProperties properties;
    private final MetamodelRoutingPipeline routingPipeline;
    private final MetamodelDiagramMetricsService metricsService;

    @Autowired
    /** Descripción: Inicializa una instancia de LayeredMetamodelDiagramLayoutEngine con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; routingPipeline: dato de entrada necesario para la operación; metricsService: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public LayeredMetamodelDiagramLayoutEngine(MetamodelLayoutProperties properties, MetamodelRoutingPipeline routingPipeline, MetamodelDiagramMetricsService metricsService) {
        this.properties = properties;
        this.routingPipeline = routingPipeline;
        this.metricsService = metricsService;
    }

    @Override
    /** Descripción: Ejecuta la operación «id» correspondiente a la responsabilidad de Handles. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String id() {
        return "layered";
    }

    @Override
    /** Descripción: Ejecuta la operación «layout» correspondiente a la responsabilidad de Handles. Entradas: metamodel: dato de entrada necesario para la operación; currentDiagram: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelLayoutResult layout(MetaModel metamodel, MetamodelDiagramDocument currentDiagram, MetamodelLayoutOptions options) {
        if(options.routeOnly() && currentDiagram != null && !currentDiagram.nodes().isEmpty()) {
            return routeOnly(metamodel, currentDiagram, options);
        }

        MetamodelLayoutDirection direction = MetamodelLayoutDirection.fromValue(options.direction());
        Map<String, NodeDesign> currentNodes = indexNodes(currentDiagram);
        Map<String, EdgeDesign> currentEdges = indexEdges(currentDiagram);
        Map<String, LayoutNode> nodes = createNodes(metamodel, currentNodes, options);
        List<PlacementEdge> placementEdges = createPlacementEdges(metamodel, options);
        List<MetaRelation> semanticEdges = metamodel.relations();
        List<Set<String>> components = connectedComponents(nodes.keySet(), placementEdges);
        List<Set<String>> graphComponents = components.stream().filter(component -> hasPlacementEdge(component, placementEdges)).toList();
        List<String> isolated = components.stream().filter(component -> !hasPlacementEdge(component, placementEdges)).flatMap(Set::stream).sorted().toList();
        List<TransformationWarning> warnings = new ArrayList<>();
        int maxLayers = 0;
        int cycleCount = 0;
        double componentOffset = properties.getMargin();

        for(Set<String> component : graphComponents) {
            ComponentLayout componentLayout = layoutComponent(component, placementEdges, nodes, direction);
            maxLayers = Math.max(maxLayers, componentLayout.layerCount());
            cycleCount += componentLayout.cycles();
            placeComponent(componentLayout, nodes, direction, componentOffset);
            componentOffset += componentLayout.secondarySize() + Math.max(40, properties.getComponentGap());
        }

        if(!isolated.isEmpty()) {
            placeIsolatedNodes(isolated, nodes, direction, componentOffset);
            maxLayers = Math.max(maxLayers, 1);
        }

        applyFixedPositions(nodes, currentNodes, options.fixedElementIds());
        List<NodeDesign> outputNodes = buildNodeDesigns(nodes, currentNodes, options);
        Map<String, NodeDesign> outputNodeIndex = outputNodes.stream().collect(java.util.stream.Collectors.toMap(NodeDesign::elementId, item -> item));
        List<EdgeDesign> outputEdges = buildEdgeDesigns(semanticEdges, currentEdges, outputNodeIndex, options);

        if(cycleCount > 0) {
            warnings.add(new TransformationWarning("LAYOUT_CYCLES_GROUPED", "relations", "Cyclic relationships were grouped in the same layout layer"));
        }

        MetamodelDiagramDocument positionedDiagram = new MetamodelDiagramDocument(metamodel.id(), outputNodes, outputEdges);
        MetamodelDiagramDocument diagram = routingPipeline.route(metamodel, positionedDiagram, options);
        MetamodelDiagramMetrics metrics = metricsService.measure(diagram);
        MetamodelLayoutStatistics statistics = new MetamodelLayoutStatistics(outputNodes.size(), outputEdges.size(), components.size(), maxLayers, cycleCount, metrics.crossings(), metrics.edgeNodeIntersections(), metrics.bends(), metrics.totalLength(), metrics.maximumPortCongestion());
        return new MetamodelLayoutResult(diagram, warnings, statistics);
    }

    /**
     * Descripción:
     * Conserva los nodos y recalcula únicamente puertos, rutas y etiquetas.
     *
     * @param metamodel contiene el metamodelo.
     * @param currentDiagram contiene las posiciones actuales.
     * @param options contiene la configuración.
     * @return el resultado encaminado.
     */
    /** Descripción: Ejecuta la operación «routeOnly» correspondiente a la responsabilidad de Handles. Entradas: metamodel: dato de entrada necesario para la operación; currentDiagram: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ private MetamodelLayoutResult routeOnly(MetaModel metamodel, MetamodelDiagramDocument currentDiagram, MetamodelLayoutOptions options) {
        Map<String, EdgeDesign> currentEdges = indexEdges(currentDiagram);
        Map<String, NodeDesign> nodes = indexNodes(currentDiagram);
        List<EdgeDesign> edgeShells = buildEdgeDesigns(metamodel.relations(), currentEdges, nodes, options);
        MetamodelDiagramDocument input = new MetamodelDiagramDocument(metamodel.id(), currentDiagram.nodes(), edgeShells);
        MetamodelDiagramDocument diagram = routingPipeline.route(metamodel, input, options);
        MetamodelDiagramMetrics metrics = metricsService.measure(diagram);
        MetamodelLayoutStatistics statistics = new MetamodelLayoutStatistics(diagram.nodes().size(), diagram.edges().size(), connectedComponents(nodes.keySet(), createPlacementEdges(metamodel, options)).size(), 0, 0, metrics.crossings(), metrics.edgeNodeIntersections(), metrics.bends(), metrics.totalLength(), metrics.maximumPortCongestion());
        return new MetamodelLayoutResult(diagram, List.of(), statistics);
    }

    /** Descripción: Crea o prepara el resultado de «createNodes» dentro de Handles. Entradas: metamodel: dato de entrada necesario para la operación; currentNodes: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, LayoutNode> createNodes(MetaModel metamodel, Map<String, NodeDesign> currentNodes, MetamodelLayoutOptions options) {
        Map<String, LayoutNode> result = new LinkedHashMap<>();

        for(MetaClass metaClass : metamodel.classes()) {
            double estimatedHeight = Math.max(135, DEFAULT_CLASS_HEADER + metaClass.attributes().size() * DEFAULT_ROW_HEIGHT + outgoingCount(metamodel, metaClass.id()) * DEFAULT_ROW_HEIGHT + metaClass.operations().size() * DEFAULT_ROW_HEIGHT);
            result.put(metaClass.id(), createNode(metaClass.id(), DEFAULT_CLASS_WIDTH, estimatedHeight, currentNodes, options));
        }

        for(MetaEnum metaEnum : metamodel.enums()) {
            double estimatedHeight = Math.max(115, DEFAULT_ENUM_HEADER + Math.max(1, metaEnum.literals().size()) * DEFAULT_ROW_HEIGHT);
            result.put(metaEnum.id(), createNode(metaEnum.id(), DEFAULT_ENUM_WIDTH, estimatedHeight, currentNodes, options));
        }

        for(MetaDataType dataType : metamodel.dataTypes()) {
            result.put(dataType.id(), createNode(dataType.id(), DEFAULT_DATA_TYPE_WIDTH, DEFAULT_DATA_TYPE_HEIGHT, currentNodes, options));
        }

        return result;
    }

    /** Descripción: Crea o prepara el resultado de «createNode» dentro de Handles. Entradas: id: identificador que delimita el recurso objetivo; defaultWidth: dato de entrada necesario para la operación; defaultHeight: dato de entrada necesario para la operación; currentNodes: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ private LayoutNode createNode(String id, double defaultWidth, double defaultHeight, Map<String, NodeDesign> currentNodes, MetamodelLayoutOptions options) {
        NodeDesign current = currentNodes.get(id);
        double width = defaultWidth;
        double height = defaultHeight;

        if(options.preserveNodeSizes() && current != null) {
            width = Math.max(defaultWidth, current.width());
            height = Math.max(defaultHeight, current.height());
        }

        return new LayoutNode(id, width, height);
    }

    /** Descripción: Ejecuta la operación «outgoingCount» correspondiente a la responsabilidad de Handles. Entradas: metamodel: dato de entrada necesario para la operación; sourceId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private int outgoingCount(MetaModel metamodel, String sourceId) {
        return (int) metamodel.relations().stream().filter(item -> item.sourceTypeId().equals(sourceId)).count();
    }

    /** Descripción: Crea o prepara el resultado de «createPlacementEdges» dentro de Handles. Entradas: metamodel: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ private List<PlacementEdge> createPlacementEdges(MetaModel metamodel, MetamodelLayoutOptions options) {
        List<PlacementEdge> result = new ArrayList<>();

        for(MetaRelation relation : metamodel.relations()) {
            result.add(new PlacementEdge(relation.sourceTypeId(), relation.targetTypeId(), true));
        }

        if(options.useAttributeTypesForPlacement()) {
            Set<String> classifierIds = new HashSet<>();
            metamodel.enums().forEach(item -> classifierIds.add(item.id()));
            metamodel.dataTypes().forEach(item -> classifierIds.add(item.id()));

            for(MetaClass metaClass : metamodel.classes()) {
                for(MetaAttribute attribute : metaClass.attributes()) {
                    if((attribute.type().kind() == MetaTypeReferenceKind.ENUM || attribute.type().kind() == MetaTypeReferenceKind.DATATYPE) && classifierIds.contains(attribute.type().targetId())) {
                        result.add(new PlacementEdge(metaClass.id(), attribute.type().targetId(), false));
                    }
                }
            }
        }

        return result.stream().filter(item -> !item.sourceId().equals(item.targetId())).distinct().toList();
    }

    /** Descripción: Ejecuta la operación «connectedComponents» correspondiente a la responsabilidad de Handles. Entradas: nodeIds: dato de entrada necesario para la operación; edges: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<Set<String>> connectedComponents(Set<String> nodeIds, List<PlacementEdge> edges) {
        Map<String, Set<String>> undirected = new LinkedHashMap<>();
        nodeIds.forEach(id -> undirected.put(id, new LinkedHashSet<>()));

        for(PlacementEdge edge : edges) {
            if(undirected.containsKey(edge.sourceId()) && undirected.containsKey(edge.targetId())) {
                undirected.get(edge.sourceId()).add(edge.targetId());
                undirected.get(edge.targetId()).add(edge.sourceId());
            }
        }

        List<Set<String>> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        for(String id : nodeIds) {
            if(!visited.add(id)) {
                continue;
            }

            Set<String> component = new LinkedHashSet<>();
            Deque<String> queue = new ArrayDeque<>();
            queue.add(id);

            while(!queue.isEmpty()) {
                String current = queue.removeFirst();
                component.add(current);

                for(String neighbour : undirected.getOrDefault(current, Set.of())) {
                    if(visited.add(neighbour)) {
                        queue.addLast(neighbour);
                    }
                }
            }

            result.add(component);
        }

        result.sort(Comparator.comparingInt(Set<String>::size).reversed());
        return result;
    }

    /** Descripción: Evalúa la condición «hasPlacementEdge» sin modificar el estado externo. Entradas: component: dato de entrada necesario para la operación; edges: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean hasPlacementEdge(Set<String> component, List<PlacementEdge> edges) {
        return edges.stream().anyMatch(item -> component.contains(item.sourceId()) && component.contains(item.targetId()));
    }

    /** Descripción: Ejecuta la operación «layoutComponent» correspondiente a la responsabilidad de Handles. Entradas: component: dato de entrada necesario para la operación; allEdges: dato de entrada necesario para la operación; nodes: dato de entrada necesario para la operación; direction: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ComponentLayout layoutComponent(Set<String> component, List<PlacementEdge> allEdges, Map<String, LayoutNode> nodes, MetamodelLayoutDirection direction) {
        List<PlacementEdge> edges = allEdges.stream().filter(item -> component.contains(item.sourceId()) && component.contains(item.targetId())).toList();
        StrongComponents strong = stronglyConnectedComponents(component, edges);
        Map<Integer, Set<Integer>> dag = new LinkedHashMap<>();
        Map<Integer, Integer> indegree = new LinkedHashMap<>();

        for(int index = 0; index < strong.groups().size(); index += 1) {
            dag.put(index, new LinkedHashSet<>());
            indegree.put(index, 0);
        }

        for(PlacementEdge edge : edges) {
            int sourceGroup = strong.nodeToGroup().get(edge.sourceId());
            int targetGroup = strong.nodeToGroup().get(edge.targetId());

            if(sourceGroup != targetGroup && dag.get(sourceGroup).add(targetGroup)) {
                indegree.put(targetGroup, indegree.get(targetGroup) + 1);
            }
        }

        Map<Integer, Integer> ranks = new LinkedHashMap<>();
        Deque<Integer> queue = new ArrayDeque<>();

        for(Map.Entry<Integer, Integer> entry : indegree.entrySet()) {
            ranks.put(entry.getKey(), 0);

            if(entry.getValue() == 0) {
                queue.addLast(entry.getKey());
            }
        }

        while(!queue.isEmpty()) {
            int source = queue.removeFirst();

            for(int target : dag.get(source)) {
                ranks.put(target, Math.max(ranks.get(target), ranks.get(source) + 1));
                indegree.put(target, indegree.get(target) - 1);

                if(indegree.get(target) == 0) {
                    queue.addLast(target);
                }
            }
        }

        Map<Integer, List<String>> layers = new LinkedHashMap<>();

        for(String nodeId : component) {
            int rank = ranks.get(strong.nodeToGroup().get(nodeId));
            layers.computeIfAbsent(rank, key -> new ArrayList<>()).add(nodeId);
        }

        layers.values().forEach(layer -> layer.sort(String::compareToIgnoreCase));
        reduceCrossings(layers, edges);
        double primarySize = calculatePrimarySize(layers, nodes, direction);
        double secondarySize = calculateSecondarySize(layers, nodes, direction);
        int cycles = (int) strong.groups().stream().filter(group -> group.size() > 1).count();
        return new ComponentLayout(layers, primarySize, secondarySize, layers.size(), cycles);
    }

    /** Descripción: Ejecuta la operación «stronglyConnectedComponents» correspondiente a la responsabilidad de Handles. Entradas: component: dato de entrada necesario para la operación; edges: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private StrongComponents stronglyConnectedComponents(Set<String> component, List<PlacementEdge> edges) {
        Map<String, List<String>> adjacency = new LinkedHashMap<>();
        component.forEach(id -> adjacency.put(id, new ArrayList<>()));
        edges.forEach(edge -> adjacency.get(edge.sourceId()).add(edge.targetId()));
        Map<String, Integer> index = new HashMap<>();
        Map<String, Integer> lowLink = new HashMap<>();
        Deque<String> stack = new ArrayDeque<>();
        Set<String> onStack = new HashSet<>();
        List<List<String>> groups = new ArrayList<>();
        int[] nextIndex = { 0 };

        for(String id : component) {
            if(!index.containsKey(id)) {
                connect(id, adjacency, index, lowLink, stack, onStack, groups, nextIndex);
            }
        }

        Map<String, Integer> nodeToGroup = new HashMap<>();

        for(int groupIndex = 0; groupIndex < groups.size(); groupIndex += 1) {
            for(String id : groups.get(groupIndex)) {
                nodeToGroup.put(id, groupIndex);
            }
        }

        return new StrongComponents(groups, nodeToGroup);
    }

    /** Descripción: Ejecuta la operación «connect» correspondiente a la responsabilidad de Handles. Entradas: id: identificador que delimita el recurso objetivo; adjacency: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación; lowLink: dato de entrada necesario para la operación; stack: dato de entrada necesario para la operación; onStack: dato de entrada necesario para la operación; groups: dato de entrada necesario para la operación; nextIndex: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void connect(String id, Map<String, List<String>> adjacency, Map<String, Integer> index, Map<String, Integer> lowLink, Deque<String> stack, Set<String> onStack, List<List<String>> groups, int[] nextIndex) {
        index.put(id, nextIndex[0]);
        lowLink.put(id, nextIndex[0]);
        nextIndex[0] += 1;
        stack.push(id);
        onStack.add(id);

        for(String target : adjacency.getOrDefault(id, List.of())) {
            if(!index.containsKey(target)) {
                connect(target, adjacency, index, lowLink, stack, onStack, groups, nextIndex);
                lowLink.put(id, Math.min(lowLink.get(id), lowLink.get(target)));
            } else if(onStack.contains(target)) {
                lowLink.put(id, Math.min(lowLink.get(id), index.get(target)));
            }
        }

        if(lowLink.get(id).equals(index.get(id))) {
            List<String> group = new ArrayList<>();
            String current;

            do {
                current = stack.pop();
                onStack.remove(current);
                group.add(current);
            } while(!current.equals(id));

            groups.add(group);
        }
    }

    /** Descripción: Ejecuta la operación «reduceCrossings» correspondiente a la responsabilidad de Handles. Entradas: layers: dato de entrada necesario para la operación; edges: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void reduceCrossings(Map<Integer, List<String>> layers, List<PlacementEdge> edges) {
        int passes = Math.max(0, properties.getCrossingReductionPasses());
        int maxRank = layers.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);

        for(int pass = 0; pass < passes; pass += 1) {
            boolean forward = pass % 2 == 0;

            if(forward) {
                for(int rank = 1; rank <= maxRank; rank += 1) {
                    sortByBarycenter(layers.get(rank), layers.get(rank - 1), edges, true);
                }
            } else {
                for(int rank = maxRank - 1; rank >= 0; rank -= 1) {
                    sortByBarycenter(layers.get(rank), layers.get(rank + 1), edges, false);
                }
            }
        }
    }

    /** Descripción: Ejecuta la operación «sortByBarycenter» correspondiente a la responsabilidad de Handles. Entradas: layer: dato de entrada necesario para la operación; adjacent: dato de entrada necesario para la operación; edges: dato de entrada necesario para la operación; useIncoming: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void sortByBarycenter(List<String> layer, List<String> adjacent, List<PlacementEdge> edges, boolean useIncoming) {
        if(layer == null || adjacent == null || layer.size() < 2) {
            return;
        }

        Map<String, Integer> adjacentPositions = new HashMap<>();

        for(int index = 0; index < adjacent.size(); index += 1) {
            adjacentPositions.put(adjacent.get(index), index);
        }

        Map<String, Double> barycenters = new HashMap<>();

        for(String id : layer) {
            List<Integer> positions = new ArrayList<>();

            for(PlacementEdge edge : edges) {
                String neighbour = null;

                if(useIncoming && edge.targetId().equals(id)) {
                    neighbour = edge.sourceId();
                } else if(!useIncoming && edge.sourceId().equals(id)) {
                    neighbour = edge.targetId();
                }

                if(neighbour != null && adjacentPositions.containsKey(neighbour)) {
                    positions.add(adjacentPositions.get(neighbour));
                }
            }

            double value = positions.isEmpty() ? Double.MAX_VALUE : positions.stream().mapToInt(Integer::intValue).average().orElse(Double.MAX_VALUE);
            barycenters.put(id, value);
        }

        layer.sort(Comparator.comparingDouble((String id) -> barycenters.getOrDefault(id, Double.MAX_VALUE)).thenComparing(String::compareToIgnoreCase));
    }

    /** Descripción: Ejecuta la operación «calculatePrimarySize» correspondiente a la responsabilidad de Handles. Entradas: layers: dato de entrada necesario para la operación; nodes: dato de entrada necesario para la operación; direction: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private double calculatePrimarySize(Map<Integer, List<String>> layers, Map<String, LayoutNode> nodes, MetamodelLayoutDirection direction) {
        double result = 0;
        int layerIndex = 0;

        for(List<String> layer : layers.values()) {
            double size = layer.stream().map(nodes::get).mapToDouble(item -> direction == MetamodelLayoutDirection.LEFT_TO_RIGHT ? item.width : item.height).max().orElse(0);

            if(layerIndex > 0) {
                result += Math.max(30, properties.getLayerGap());
            }

            result += size;
            layerIndex += 1;
        }

        return result;
    }

    /** Descripción: Ejecuta la operación «calculateSecondarySize» correspondiente a la responsabilidad de Handles. Entradas: layers: dato de entrada necesario para la operación; nodes: dato de entrada necesario para la operación; direction: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private double calculateSecondarySize(Map<Integer, List<String>> layers, Map<String, LayoutNode> nodes, MetamodelLayoutDirection direction) {
        double result = 0;

        for(List<String> layer : layers.values()) {
            double size = 0;

            for(int index = 0; index < layer.size(); index += 1) {
                if(index > 0) {
                    size += Math.max(20, properties.getNodeGap());
                }

                LayoutNode node = nodes.get(layer.get(index));
                size += direction == MetamodelLayoutDirection.LEFT_TO_RIGHT ? node.height : node.width;
            }

            result = Math.max(result, size);
        }

        return result;
    }

    /** Descripción: Ejecuta la operación «placeComponent» correspondiente a la responsabilidad de Handles. Entradas: layout: dato de entrada necesario para la operación; nodes: dato de entrada necesario para la operación; direction: dato de entrada necesario para la operación; componentOffset: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void placeComponent(ComponentLayout layout, Map<String, LayoutNode> nodes, MetamodelLayoutDirection direction, double componentOffset) {
        double primary = properties.getMargin();

        for(List<String> layer : layout.layers().values()) {
            double layerPrimarySize = layer.stream().map(nodes::get).mapToDouble(item -> direction == MetamodelLayoutDirection.LEFT_TO_RIGHT ? item.width : item.height).max().orElse(0);
            double layerSecondarySize = 0;

            for(int index = 0; index < layer.size(); index += 1) {
                if(index > 0) {
                    layerSecondarySize += Math.max(20, properties.getNodeGap());
                }

                LayoutNode node = nodes.get(layer.get(index));
                layerSecondarySize += direction == MetamodelLayoutDirection.LEFT_TO_RIGHT ? node.height : node.width;
            }

            double secondary = componentOffset + Math.max(0, (layout.secondarySize() - layerSecondarySize) / 2);

            for(String nodeId : layer) {
                LayoutNode node = nodes.get(nodeId);

                if(direction == MetamodelLayoutDirection.LEFT_TO_RIGHT) {
                    node.x = primary;
                    node.y = secondary;
                    secondary += node.height + Math.max(20, properties.getNodeGap());
                } else {
                    node.x = secondary;
                    node.y = primary;
                    secondary += node.width + Math.max(20, properties.getNodeGap());
                }
            }

            primary += layerPrimarySize + Math.max(30, properties.getLayerGap());
        }
    }

    /** Descripción: Ejecuta la operación «placeIsolatedNodes» correspondiente a la responsabilidad de Handles. Entradas: isolated: dato de entrada necesario para la operación; nodes: dato de entrada necesario para la operación; direction: dato de entrada necesario para la operación; componentOffset: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void placeIsolatedNodes(List<String> isolated, Map<String, LayoutNode> nodes, MetamodelLayoutDirection direction, double componentOffset) {
        int columns = Math.max(1, properties.getIsolatedNodeColumns());
        double maxWidth = isolated.stream().map(nodes::get).mapToDouble(item -> item.width).max().orElse(DEFAULT_CLASS_WIDTH);
        double maxHeight = isolated.stream().map(nodes::get).mapToDouble(item -> item.height).max().orElse(135);

        for(int index = 0; index < isolated.size(); index += 1) {
            LayoutNode node = nodes.get(isolated.get(index));
            int column = index % columns;
            int row = index / columns;

            if(direction == MetamodelLayoutDirection.LEFT_TO_RIGHT) {
                node.x = properties.getMargin() + column * (maxWidth + Math.max(20, properties.getNodeGap()));
                node.y = componentOffset + row * (maxHeight + Math.max(20, properties.getNodeGap()));
            } else {
                node.x = componentOffset + row * (maxWidth + Math.max(20, properties.getNodeGap()));
                node.y = properties.getMargin() + column * (maxHeight + Math.max(20, properties.getNodeGap()));
            }
        }
    }

    /** Descripción: Actualiza el estado gestionado por Handles mediante la operación «applyFixedPositions». Entradas: nodes: dato de entrada necesario para la operación; currentNodes: dato de entrada necesario para la operación; fixedIds: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void applyFixedPositions(Map<String, LayoutNode> nodes, Map<String, NodeDesign> currentNodes, List<String> fixedIds) {
        for(String id : fixedIds) {
            LayoutNode node = nodes.get(id);
            NodeDesign current = currentNodes.get(id);

            if(node != null && current != null) {
                node.x = current.x();
                node.y = current.y();
            }
        }
    }

    /** Descripción: Crea o prepara el resultado de «buildNodeDesigns» dentro de Handles. Entradas: nodes: dato de entrada necesario para la operación; currentNodes: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ private List<NodeDesign> buildNodeDesigns(Map<String, LayoutNode> nodes, Map<String, NodeDesign> currentNodes, MetamodelLayoutOptions options) {
        List<NodeDesign> result = new ArrayList<>();

        for(LayoutNode node : nodes.values()) {
            NodeDesign current = currentNodes.get(node.id);

            if(options.preserveStyles() && current != null && current.style() != null) {
                result.add(new NodeDesign(node.id, round(node.x), round(node.y), round(node.width), round(node.height), current.style()));
            } else {
                result.add(new NodeDesign(node.id, round(node.x), round(node.y), round(node.width), round(node.height)));
            }
        }

        return result;
    }

    /** Descripción: Crea o prepara el resultado de «buildEdgeDesigns» dentro de Handles. Entradas: relations: dato de entrada necesario para la operación; currentEdges: dato de entrada necesario para la operación; nodes: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ private List<EdgeDesign> buildEdgeDesigns(List<MetaRelation> relations, Map<String, EdgeDesign> currentEdges, Map<String, NodeDesign> nodes, MetamodelLayoutOptions options) {
        List<EdgeDesign> result = new ArrayList<>();

        for(MetaRelation relation : relations) {
            NodeDesign source = nodes.get(relation.sourceTypeId());
            NodeDesign target = nodes.get(relation.targetTypeId());

            if(source == null || target == null) {
                continue;
            }

            Handles handles = chooseHandles(source, target);
            EdgeDesign current = currentEdges.get(relation.id());

            if(options.preserveStyles() && current != null && current.style() != null) {
                result.add(new EdgeDesign(relation.id(), handles.sourceHandle(), handles.targetHandle(), relation.kind().name(), relation.sourceTypeId(), relation.targetTypeId(), relation.name(), current.style()));
            } else {
                result.add(new EdgeDesign(relation.id(), handles.sourceHandle(), handles.targetHandle(), relation.kind().name(), relation.sourceTypeId(), relation.targetTypeId(), relation.name()));
            }
        }

        return result;
    }

    /** Descripción: Ejecuta la operación «chooseHandles» correspondiente a la responsabilidad de Handles. Entradas: source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Handles chooseHandles(NodeDesign source, NodeDesign target) {
        double sourceCenterX = source.x() + source.width() / 2;
        double sourceCenterY = source.y() + source.height() / 2;
        double targetCenterX = target.x() + target.width() / 2;
        double targetCenterY = target.y() + target.height() / 2;
        double dx = targetCenterX - sourceCenterX;
        double dy = targetCenterY - sourceCenterY;

        if(Math.abs(dx) >= Math.abs(dy)) {
            String sourceSide = dx >= 0 ? "right" : "left";
            String targetSide = dx >= 0 ? "left" : "right";
            return new Handles(sourceSide + "-" + verticalHandlePercent(source, targetCenterY), targetSide + "-" + verticalHandlePercent(target, sourceCenterY));
        }

        String sourceSide = dy >= 0 ? "bottom" : "top";
        String targetSide = dy >= 0 ? "top" : "bottom";
        return new Handles(sourceSide + "-" + horizontalHandlePercent(source, targetCenterX), targetSide + "-" + horizontalHandlePercent(target, sourceCenterX));
    }

    /** Descripción: Ejecuta la operación «verticalHandlePercent» correspondiente a la responsabilidad de Handles. Entradas: node: dato de entrada necesario para la operación; otherCenterY: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int verticalHandlePercent(NodeDesign node, double otherCenterY) {
        double ratio = (otherCenterY - node.y()) / Math.max(1, node.height());
        return snapHandlePercent(ratio);
    }

    /** Descripción: Ejecuta la operación «horizontalHandlePercent» correspondiente a la responsabilidad de Handles. Entradas: node: dato de entrada necesario para la operación; otherCenterX: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int horizontalHandlePercent(NodeDesign node, double otherCenterX) {
        double ratio = (otherCenterX - node.x()) / Math.max(1, node.width());
        return snapHandlePercent(ratio);
    }

    /** Descripción: Ejecuta la operación «snapHandlePercent» correspondiente a la responsabilidad de Handles. Entradas: ratio: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int snapHandlePercent(double ratio) {
        if(ratio < 0.375) {
            return 25;
        }

        if(ratio > 0.625) {
            return 75;
        }

        return 50;
    }

    /** Descripción: Ejecuta la operación «indexNodes» correspondiente a la responsabilidad de Handles. Entradas: diagram: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, NodeDesign> indexNodes(MetamodelDiagramDocument diagram) {
        Map<String, NodeDesign> result = new HashMap<>();

        if(diagram != null) {
            diagram.nodes().forEach(item -> result.put(item.elementId(), item));
        }

        return result;
    }

    /** Descripción: Ejecuta la operación «indexEdges» correspondiente a la responsabilidad de Handles. Entradas: diagram: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, EdgeDesign> indexEdges(MetamodelDiagramDocument diagram) {
        Map<String, EdgeDesign> result = new HashMap<>();

        if(diagram != null) {
            diagram.edges().forEach(item -> result.put(item.relationId(), item));
        }

        return result;
    }

    /** Descripción: Ejecuta la operación «round» correspondiente a la responsabilidad de Handles. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static final class LayoutNode {
        private final String id;
        private final double width;
        private final double height;
        private double x;
        private double y;

        /** Descripción: Inicializa una instancia de LayoutNode con sus dependencias y datos de entrada. Entradas: id: identificador que delimita el recurso objetivo; width: dato de entrada necesario para la operación; height: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private LayoutNode(String id, double width, double height) {
            this.id = id;
            this.width = width;
            this.height = height;
        }
    }

    private record PlacementEdge(String sourceId, String targetId, boolean semantic) {
    }

    private record StrongComponents(List<List<String>> groups, Map<String, Integer> nodeToGroup) {
    }

    private record ComponentLayout(Map<Integer, List<String>> layers, double primarySize, double secondarySize, int layerCount, int cycles) {
    }

    private record Handles(String sourceHandle, String targetHandle) {
    }
}
