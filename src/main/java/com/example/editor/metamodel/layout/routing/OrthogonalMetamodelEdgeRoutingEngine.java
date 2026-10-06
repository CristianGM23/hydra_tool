package com.example.editor.metamodel.layout.routing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.EdgeDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.NodeDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.RoutePointDesign;
import com.example.editor.metamodel.layout.MetamodelLayoutOptions;
import com.example.editor.metamodel.layout.MetamodelLayoutProperties;

/**
 * Encamina relaciones ortogonales evitando nodos y penalizando cruces.
 */
@Component
public class OrthogonalMetamodelEdgeRoutingEngine implements MetamodelEdgeRoutingEngine {

    private final MetamodelLayoutProperties properties;

    /** Descripción: Inicializa una instancia de OrthogonalMetamodelEdgeRoutingEngine con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public OrthogonalMetamodelEdgeRoutingEngine(MetamodelLayoutProperties properties) {
        this.properties = properties;
    }

    @Override
    /** Descripción: Ejecuta la operación «id» correspondiente a la responsabilidad de SearchNode. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String id() {
        return "orthogonal";
    }

    @Override
    /** Descripción: Ejecuta la operación «route» correspondiente a la responsabilidad de SearchNode. Entradas: metamodel: dato de entrada necesario para la operación; diagram: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelDiagramDocument route(MetaModel metamodel, MetamodelDiagramDocument diagram, MetamodelLayoutOptions options) {
        Map<String, NodeDesign> nodes = indexNodes(diagram);
        RoutingGrid grid = RoutingGrid.create(diagram.nodes(), properties);
        List<EdgeDesign> ordered = new ArrayList<>(diagram.edges());
        ordered.sort(Comparator.comparingInt(this::priority).thenComparingDouble(edge -> -estimatedLength(edge, nodes)).thenComparing(EdgeDesign::relationId));
        Map<String, EdgeDesign> routed = new LinkedHashMap<>();

        for(EdgeDesign edge : ordered) {
            NodeDesign source = nodes.get(edge.sourceClassId());
            NodeDesign target = nodes.get(edge.targetClassId());

            if(source == null || target == null) {
                routed.put(edge.relationId(), edge);
                continue;
            }

            List<RoutePointDesign> route;

            if(source.elementId().equals(target.elementId())) {
                route = selfLoop(source, edge);
            } else {
                route = routeEdge(source, target, edge, grid);
            }

            List<RoutePointDesign> compressed = MetamodelRoutingGeometry.compress(removeDuplicates(route));
            grid.reserve(compressed);
            routed.put(edge.relationId(), new EdgeDesign(edge.relationId(), edge.sourceHandle(), edge.targetHandle(), edge.kind(), edge.sourceClassId(), edge.targetClassId(), edge.label(), edge.style(), compressed, null));
        }

        List<EdgeDesign> output = diagram.edges().stream().map(edge -> routed.getOrDefault(edge.relationId(), edge)).toList();
        return new MetamodelDiagramDocument(diagram.metamodelId(), diagram.nodes(), output);
    }

    /** Descripción: Ejecuta la operación «routeEdge» correspondiente a la responsabilidad de SearchNode. Entradas: source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; edge: dato de entrada necesario para la operación; grid: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private List<RoutePointDesign> routeEdge(NodeDesign source, NodeDesign target, EdgeDesign edge, RoutingGrid grid) {
        RoutePointDesign sourcePoint = MetamodelRoutingGeometry.handlePoint(source, edge.sourceHandle());
        RoutePointDesign targetPoint = MetamodelRoutingGeometry.handlePoint(target, edge.targetHandle());
        double leadDistance = properties.getRoutingNodeClearance() + Math.max(5, properties.getRoutingGridSize()) * 1.5;
        RoutePointDesign sourceLead = MetamodelRoutingGeometry.leadPoint(sourcePoint, edge.sourceHandle(), leadDistance);
        RoutePointDesign targetLead = MetamodelRoutingGeometry.leadPoint(targetPoint, edge.targetHandle(), leadDistance);
        GridPoint start = grid.snap(sourceLead);
        GridPoint goal = grid.snap(targetLead);
        List<GridPoint> gridRoute = findPath(grid, start, goal);
        List<RoutePointDesign> result = new ArrayList<>();
        result.add(sourcePoint);
        result.add(sourceLead);

        if(gridRoute.isEmpty()) {
            result.addAll(fallbackRoute(sourceLead, targetLead, grid.nodes()));
        } else {
            List<RoutePointDesign> gridPoints = gridRoute.stream().map(grid::toPoint).toList();
            appendSourceBridge(result, sourceLead, gridPoints.get(0), edge.sourceHandle());
            result.addAll(gridPoints);
            appendTargetBridge(result, gridPoints.get(gridPoints.size() - 1), targetLead, edge.targetHandle());
        }

        result.add(targetLead);
        result.add(targetPoint);
        return result;
    }

    /** Descripción: Ejecuta la operación «appendSourceBridge» correspondiente a la responsabilidad de SearchNode. Entradas: result: dato de entrada necesario para la operación; lead: dato de entrada necesario para la operación; gridPoint: dato de entrada necesario para la operación; handle: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendSourceBridge(List<RoutePointDesign> result, RoutePointDesign lead, RoutePointDesign gridPoint, String handle) {
        if(MetamodelRoutingGeometry.approximately(lead.x(), gridPoint.x()) || MetamodelRoutingGeometry.approximately(lead.y(), gridPoint.y())) {
            return;
        }

        String side = MetamodelRoutingGeometry.side(handle);

        if("left".equals(side) || "right".equals(side)) {
            result.add(new RoutePointDesign(gridPoint.x(), lead.y()));
        } else {
            result.add(new RoutePointDesign(lead.x(), gridPoint.y()));
        }
    }

    /** Descripción: Ejecuta la operación «appendTargetBridge» correspondiente a la responsabilidad de SearchNode. Entradas: result: dato de entrada necesario para la operación; gridPoint: dato de entrada necesario para la operación; lead: dato de entrada necesario para la operación; handle: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendTargetBridge(List<RoutePointDesign> result, RoutePointDesign gridPoint, RoutePointDesign lead, String handle) {
        if(MetamodelRoutingGeometry.approximately(lead.x(), gridPoint.x()) || MetamodelRoutingGeometry.approximately(lead.y(), gridPoint.y())) {
            return;
        }

        String side = MetamodelRoutingGeometry.side(handle);

        if("left".equals(side) || "right".equals(side)) {
            result.add(new RoutePointDesign(gridPoint.x(), lead.y()));
        } else {
            result.add(new RoutePointDesign(lead.x(), gridPoint.y()));
        }
    }

    /** Descripción: Obtiene la información correspondiente a «findPath» dentro de SearchNode. Entradas: grid: identificador que delimita el recurso objetivo; start: dato de entrada necesario para la operación; goal: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<GridPoint> findPath(RoutingGrid grid, GridPoint start, GridPoint goal) {
        PriorityQueue<SearchNode> open = new PriorityQueue<>(Comparator.comparingDouble(SearchNode::estimatedTotal).thenComparingInt(item -> item.point().x()).thenComparingInt(item -> item.point().y()).thenComparingInt(SearchNode::direction));
        Map<SearchState, Double> bestCost = new HashMap<>();
        Map<SearchState, SearchState> previous = new HashMap<>();
        SearchState initial = new SearchState(start, Direction.NONE.code);
        bestCost.put(initial, 0.0);
        open.add(new SearchNode(start, Direction.NONE.code, 0, heuristic(start, goal)));
        int expanded = 0;

        while(!open.isEmpty() && expanded < Math.max(1000, properties.getRoutingMaximumExpandedStates())) {
            SearchNode currentNode = open.poll();
            SearchState current = new SearchState(currentNode.point(), currentNode.direction());
            double knownCost = bestCost.getOrDefault(current, Double.POSITIVE_INFINITY);

            if(currentNode.cost() > knownCost + 0.0001) {
                continue;
            }

            if(current.point().equals(goal)) {
                return reconstruct(current, previous);
            }

            expanded += 1;

            for(Step step : grid.neighbours(current.point())) {
                if(grid.isBlocked(step.point()) && !step.point().equals(goal) && !step.point().equals(start)) {
                    continue;
                }

                int nextDirection = step.horizontal() ? Direction.HORIZONTAL.code : Direction.VERTICAL.code;
                double transition = grid.gridSize();
                transition += turnCost(current.direction(), nextDirection);
                transition += grid.congestionCost(current.point(), step.point(), step.horizontal(), properties);
                SearchState next = new SearchState(step.point(), nextDirection);
                double nextCost = knownCost + transition;

                if(nextCost + 0.0001 < bestCost.getOrDefault(next, Double.POSITIVE_INFINITY)) {
                    bestCost.put(next, nextCost);
                    previous.put(next, current);
                    open.add(new SearchNode(step.point(), nextDirection, nextCost, nextCost + heuristic(step.point(), goal)));
                }
            }
        }

        return List.of();
    }

    /** Descripción: Ejecuta la operación «turnCost» correspondiente a la responsabilidad de SearchNode. Entradas: previousDirection: dato de entrada necesario para la operación; nextDirection: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private double turnCost(int previousDirection, int nextDirection) {
        if(previousDirection == Direction.NONE.code || previousDirection == nextDirection) {
            return 0;
        }

        return Math.max(0, properties.getRoutingTurnCost());
    }

    /** Descripción: Ejecuta la operación «heuristic» correspondiente a la responsabilidad de SearchNode. Entradas: current: dato de entrada necesario para la operación; goal: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private double heuristic(GridPoint current, GridPoint goal) {
        return (Math.abs(goal.x() - current.x()) + Math.abs(goal.y() - current.y())) * Math.max(1, properties.getRoutingGridSize());
    }

    /** Descripción: Ejecuta la operación «reconstruct» correspondiente a la responsabilidad de SearchNode. Entradas: goal: dato de entrada necesario para la operación; previous: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<GridPoint> reconstruct(SearchState goal, Map<SearchState, SearchState> previous) {
        List<GridPoint> reversed = new ArrayList<>();
        SearchState current = goal;
        reversed.add(current.point());

        while(previous.containsKey(current)) {
            current = previous.get(current);
            reversed.add(current.point());
        }

        java.util.Collections.reverse(reversed);
        return reversed;
    }

    /** Descripción: Ejecuta la operación «fallbackRoute» correspondiente a la responsabilidad de SearchNode. Entradas: source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; nodes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<RoutePointDesign> fallbackRoute(RoutePointDesign source, RoutePointDesign target, List<NodeDesign> nodes) {
        List<List<RoutePointDesign>> candidates = new ArrayList<>();
        candidates.add(List.of(source, new RoutePointDesign(target.x(), source.y()), target));
        candidates.add(List.of(source, new RoutePointDesign(source.x(), target.y()), target));
        double outerRight = nodes.stream().mapToDouble(node -> node.x() + node.width()).max().orElse(Math.max(source.x(), target.x())) + properties.getRoutingNodeClearance() * 2;
        double outerBottom = nodes.stream().mapToDouble(node -> node.y() + node.height()).max().orElse(Math.max(source.y(), target.y())) + properties.getRoutingNodeClearance() * 2;
        candidates.add(List.of(source, new RoutePointDesign(outerRight, source.y()), new RoutePointDesign(outerRight, target.y()), target));
        candidates.add(List.of(source, new RoutePointDesign(source.x(), outerBottom), new RoutePointDesign(target.x(), outerBottom), target));
        return candidates.stream().min(Comparator.comparingDouble(candidate -> fallbackCost(candidate, nodes))).orElse(List.of(source, target));
    }

    /** Descripción: Ejecuta la operación «fallbackCost» correspondiente a la responsabilidad de SearchNode. Entradas: points: dato de entrada necesario para la operación; nodes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private double fallbackCost(List<RoutePointDesign> points, List<NodeDesign> nodes) {
        double result = 0;

        for(int index = 0; index < points.size() - 1; index += 1) {
            RoutePointDesign first = points.get(index);
            RoutePointDesign second = points.get(index + 1);
            result += MetamodelRoutingGeometry.segmentLength(first, second);

            for(NodeDesign node : nodes) {
                if(MetamodelRoutingGeometry.segmentIntersectsRectangle(first, second, node, properties.getRoutingNodeClearance() / 2)) {
                    result += properties.getRoutingCrossingCost() * 4;
                }
            }
        }

        return result;
    }

    /** Descripción: Ejecuta la operación «selfLoop» correspondiente a la responsabilidad de SearchNode. Entradas: node: dato de entrada necesario para la operación; edge: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<RoutePointDesign> selfLoop(NodeDesign node, EdgeDesign edge) {
        RoutePointDesign source = MetamodelRoutingGeometry.handlePoint(node, edge.sourceHandle());
        RoutePointDesign target = MetamodelRoutingGeometry.handlePoint(node, edge.targetHandle());
        double clearance = properties.getRoutingNodeClearance();
        double right = node.x() + node.width() + clearance * 2;
        double bottom = node.y() + node.height() + clearance * 2;
        return List.of(source, new RoutePointDesign(right, source.y()), new RoutePointDesign(right, bottom), new RoutePointDesign(target.x(), bottom), target);
    }

    /** Descripción: Elimina o libera el recurso gestionado por «removeDuplicates» de forma controlada. Entradas: points: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<RoutePointDesign> removeDuplicates(List<RoutePointDesign> points) {
        List<RoutePointDesign> result = new ArrayList<>();

        for(RoutePointDesign point : points) {
            if(result.isEmpty()) {
                result.add(point);
                continue;
            }

            RoutePointDesign previous = result.get(result.size() - 1);

            if(!MetamodelRoutingGeometry.approximately(previous.x(), point.x()) || !MetamodelRoutingGeometry.approximately(previous.y(), point.y())) {
                result.add(point);
            }
        }

        return result;
    }

    /** Descripción: Ejecuta la operación «priority» correspondiente a la responsabilidad de SearchNode. Entradas: edge: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int priority(EdgeDesign edge) {
        String kind = edge.kind() == null ? "" : edge.kind();

        return switch(kind) {
            case "INHERITANCE", "IMPLEMENTATION" -> 0;
            case "CONTAINMENT" -> 1;
            case "AGGREGATION" -> 2;
            case "REFERENCE" -> 3;
            case "DEPENDENCY" -> 4;
            default -> 5;
        };
    }

    /** Descripción: Ejecuta la operación «estimatedLength» correspondiente a la responsabilidad de SearchNode. Entradas: edge: dato de entrada necesario para la operación; nodes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private double estimatedLength(EdgeDesign edge, Map<String, NodeDesign> nodes) {
        NodeDesign source = nodes.get(edge.sourceClassId());
        NodeDesign target = nodes.get(edge.targetClassId());

        if(source == null || target == null) {
            return 0;
        }

        double sourceX = source.x() + source.width() / 2;
        double sourceY = source.y() + source.height() / 2;
        double targetX = target.x() + target.width() / 2;
        double targetY = target.y() + target.height() / 2;
        return Math.abs(targetX - sourceX) + Math.abs(targetY - sourceY);
    }

    /** Descripción: Ejecuta la operación «indexNodes» correspondiente a la responsabilidad de SearchNode. Entradas: diagram: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, NodeDesign> indexNodes(MetamodelDiagramDocument diagram) {
        Map<String, NodeDesign> result = new LinkedHashMap<>();
        diagram.nodes().forEach(item -> result.put(item.elementId(), item));
        return result;
    }

    private enum Direction {
        NONE(0),
        HORIZONTAL(1),
        VERTICAL(2);

        private final int code;

        /** Descripción: Inicializa una instancia de Direction con sus dependencias y datos de entrada. Entradas: code: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ Direction(int code) {
            this.code = code;
        }
    }

    private record GridPoint(int x, int y) {
    }

    private record SearchState(GridPoint point, int direction) {
    }

    private record SearchNode(GridPoint point, int direction, double cost, double estimatedTotal) {
    }

    private record Step(GridPoint point, boolean horizontal) {
    }

    /**
     * Representa la cuadrícula, los obstáculos y los canales ya utilizados.
     */
    private static final class RoutingGrid {

        private final double gridSize;
        private final double originX;
        private final double originY;
        private final int maximumX;
        private final int maximumY;
        private final Set<GridPoint> blocked;
        private final Map<GridSegment, Integer> horizontalUsage = new HashMap<>();
        private final Map<GridSegment, Integer> verticalUsage = new HashMap<>();
        private final Map<GridPoint, Integer> horizontalCells = new HashMap<>();
        private final Map<GridPoint, Integer> verticalCells = new HashMap<>();
        private final List<NodeDesign> nodes;

        /** Descripción: Inicializa una instancia de RoutingGrid con sus dependencias y datos de entrada. Entradas: gridSize: dato de entrada necesario para la operación; originX: dato de entrada necesario para la operación; originY: dato de entrada necesario para la operación; maximumX: dato de entrada necesario para la operación; maximumY: dato de entrada necesario para la operación; blocked: dato de entrada necesario para la operación; nodes: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private RoutingGrid(double gridSize, double originX, double originY, int maximumX, int maximumY, Set<GridPoint> blocked, List<NodeDesign> nodes) {
            this.gridSize = gridSize;
            this.originX = originX;
            this.originY = originY;
            this.maximumX = maximumX;
            this.maximumY = maximumY;
            this.blocked = blocked;
            this.nodes = List.copyOf(nodes);
        }

        /** Descripción: Crea o prepara el resultado de «create» dentro de SearchNode. Entradas: nodes: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ private static RoutingGrid create(List<NodeDesign> nodes, MetamodelLayoutProperties properties) {
            double gridSize = Math.max(5, properties.getRoutingGridSize());
            double clearance = Math.max(gridSize, properties.getRoutingNodeClearance());
            double minimumX = nodes.stream().mapToDouble(NodeDesign::x).min().orElse(0) - clearance * 4;
            double minimumY = nodes.stream().mapToDouble(NodeDesign::y).min().orElse(0) - clearance * 4;
            double maximumCoordinateX = nodes.stream().mapToDouble(node -> node.x() + node.width()).max().orElse(800) + clearance * 4;
            double maximumCoordinateY = nodes.stream().mapToDouble(node -> node.y() + node.height()).max().orElse(600) + clearance * 4;
            double originX = Math.floor(minimumX / gridSize) * gridSize;
            double originY = Math.floor(minimumY / gridSize) * gridSize;
            int maximumX = Math.max(1, (int) Math.ceil((maximumCoordinateX - originX) / gridSize));
            int maximumY = Math.max(1, (int) Math.ceil((maximumCoordinateY - originY) / gridSize));
            Set<GridPoint> blocked = new HashSet<>();

            for(NodeDesign node : nodes) {
                int left = (int) Math.floor((node.x() - clearance - originX) / gridSize);
                int right = (int) Math.ceil((node.x() + node.width() + clearance - originX) / gridSize);
                int top = (int) Math.floor((node.y() - clearance - originY) / gridSize);
                int bottom = (int) Math.ceil((node.y() + node.height() + clearance - originY) / gridSize);

                for(int x = Math.max(0, left); x <= Math.min(maximumX, right); x += 1) {
                    for(int y = Math.max(0, top); y <= Math.min(maximumY, bottom); y += 1) {
                        blocked.add(new GridPoint(x, y));
                    }
                }
            }

            return new RoutingGrid(gridSize, originX, originY, maximumX, maximumY, blocked, nodes);
        }

        /** Descripción: Ejecuta la operación «snap» correspondiente a la responsabilidad de SearchNode. Entradas: point: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private GridPoint snap(RoutePointDesign point) {
            int x = (int) Math.round((point.x() - originX) / gridSize);
            int y = (int) Math.round((point.y() - originY) / gridSize);
            return new GridPoint(Math.max(0, Math.min(maximumX, x)), Math.max(0, Math.min(maximumY, y)));
        }

        /** Descripción: Ejecuta la operación «toPoint» correspondiente a la responsabilidad de SearchNode. Entradas: point: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RoutePointDesign toPoint(GridPoint point) {
            return new RoutePointDesign(originX + point.x() * gridSize, originY + point.y() * gridSize);
        }

        /** Descripción: Ejecuta la operación «neighbours» correspondiente a la responsabilidad de SearchNode. Entradas: point: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<Step> neighbours(GridPoint point) {
            List<Step> result = new ArrayList<>(4);
            add(result, point.x() - 1, point.y(), true);
            add(result, point.x() + 1, point.y(), true);
            add(result, point.x(), point.y() - 1, false);
            add(result, point.x(), point.y() + 1, false);
            return result;
        }

        /** Descripción: Ejecuta la operación «add» correspondiente a la responsabilidad de SearchNode. Entradas: result: dato de entrada necesario para la operación; x: dato de entrada necesario para la operación; y: dato de entrada necesario para la operación; horizontal: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void add(List<Step> result, int x, int y, boolean horizontal) {
            if(x >= 0 && x <= maximumX && y >= 0 && y <= maximumY) {
                result.add(new Step(new GridPoint(x, y), horizontal));
            }
        }

        /** Descripción: Evalúa la condición «isBlocked» sin modificar el estado externo. Entradas: point: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isBlocked(GridPoint point) {
            return blocked.contains(point);
        }

        /** Descripción: Ejecuta la operación «congestionCost» correspondiente a la responsabilidad de SearchNode. Entradas: first: dato de entrada necesario para la operación; second: dato de entrada necesario para la operación; horizontal: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ private double congestionCost(GridPoint first, GridPoint second, boolean horizontal, MetamodelLayoutProperties properties) {
            GridSegment segment = GridSegment.of(first, second);
            Map<GridSegment, Integer> usage = horizontal ? horizontalUsage : verticalUsage;
            Map<GridPoint, Integer> perpendicular = horizontal ? verticalCells : horizontalCells;
            double edgeGapFactor = 1 + Math.max(0, properties.getRoutingEdgeGap()) / Math.max(1, gridSize);
            double result = usage.getOrDefault(segment, 0) * Math.max(0, properties.getRoutingCongestionCost()) * edgeGapFactor;
            result += perpendicular.getOrDefault(second, 0) * Math.max(0, properties.getRoutingCrossingCost());

            if(!properties.isParallelRouting() && usage.getOrDefault(segment, 0) > 0) {
                result += properties.getRoutingCrossingCost() * 5;
            }

            return result;
        }

        /** Descripción: Ejecuta la operación «reserve» correspondiente a la responsabilidad de SearchNode. Entradas: route: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void reserve(List<RoutePointDesign> route) {
            for(int index = 0; index < route.size() - 1; index += 1) {
                GridPoint first = snap(route.get(index));
                GridPoint second = snap(route.get(index + 1));

                if(first.x() == second.x()) {
                    int minimum = Math.min(first.y(), second.y());

                    for(int y = minimum; y < Math.max(first.y(), second.y()); y += 1) {
                        GridPoint start = new GridPoint(first.x(), y);
                        GridPoint end = new GridPoint(first.x(), y + 1);
                        verticalUsage.merge(GridSegment.of(start, end), 1, Integer::sum);
                        verticalCells.merge(end, 1, Integer::sum);
                    }
                } else if(first.y() == second.y()) {
                    int minimum = Math.min(first.x(), second.x());

                    for(int x = minimum; x < Math.max(first.x(), second.x()); x += 1) {
                        GridPoint start = new GridPoint(x, first.y());
                        GridPoint end = new GridPoint(x + 1, first.y());
                        horizontalUsage.merge(GridSegment.of(start, end), 1, Integer::sum);
                        horizontalCells.merge(end, 1, Integer::sum);
                    }
                }
            }
        }

        /** Descripción: Ejecuta la operación «gridSize» correspondiente a la responsabilidad de SearchNode. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private double gridSize() {
            return gridSize;
        }

        /** Descripción: Ejecuta la operación «nodes» correspondiente a la responsabilidad de SearchNode. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private List<NodeDesign> nodes() {
            return nodes;
        }
    }

    private record GridSegment(GridPoint first, GridPoint second) {

        /** Descripción: Ejecuta la operación «of» correspondiente a la responsabilidad de SearchNode. Entradas: first: dato de entrada necesario para la operación; second: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static GridSegment of(GridPoint first, GridPoint second) {
            if(first.x() < second.x() || first.x() == second.x() && first.y() <= second.y()) {
                return new GridSegment(first, second);
            }

            return new GridSegment(second, first);
        }
    }
}
