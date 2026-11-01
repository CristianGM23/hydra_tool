package com.example.editor.metamodel.layout.routing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
            // the import analysis job cancels with an interrupt when it exceeds its deadline: stop instead of routing the rest
            checkInterrupted();
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
        // the A* below would flood the whole free region reachable from start before giving up; when the goal cannot be reached at all
        // the answer (no grid route -> fallback) is known in O(1) from the connected components of the free cells
        if(!grid.connected(start, goal)) {
            return List.of();
        }

        // Same A* as before (same costs, same neighbour order, same total order of the open set: estimated total, x, y, direction), with the
        // search state indexed by cell and direction in arrays instead of hash maps of records: the expansions, and so the routes, are identical.
        SearchSpace space = grid.searchSpace();
        space.begin();
        StateHeap open = new StateHeap(grid.width());
        int startState = grid.state(start.x(), start.y(), Direction.NONE.code);
        space.set(startState, 0.0, -1);
        open.add(heuristic(start.x(), start.y(), goal), 0, startState);
        int expanded = 0;
        int budget = Math.max(1000, properties.getRoutingMaximumExpandedStates());
        int width = grid.width();

        while(open.size() > 0 && expanded < budget) {
            double currentCost = open.topCost();
            int current = open.poll();
            double knownCost = space.cost(current);

            if(currentCost > knownCost + 0.0001) {
                continue;
            }

            int cell = current / 3;
            int x = cell % width;
            int y = cell / width;
            int direction = current % 3;

            if(x == goal.x() && y == goal.y()) {
                return reconstruct(grid, space, current);
            }

            expanded += 1;

            if((expanded & 1023) == 0) {
                checkInterrupted();
            }

            for(int k = 0; k < 4; k += 1) {
                int nx = x + NEIGHBOUR_DX[k];
                int ny = y + NEIGHBOUR_DY[k];

                if(!grid.inside(nx, ny)) {
                    continue;
                }

                if(grid.blocked(nx, ny) && !(nx == goal.x() && ny == goal.y()) && !(nx == start.x() && ny == start.y())) {
                    continue;
                }

                boolean horizontal = k < 2;
                int nextDirection = horizontal ? Direction.HORIZONTAL.code : Direction.VERTICAL.code;
                double transition = grid.gridSize();
                transition += turnCost(direction, nextDirection);
                transition += grid.congestionCost(x, y, nx, ny, horizontal, properties);
                int next = grid.state(nx, ny, nextDirection);
                double nextCost = knownCost + transition;

                if(nextCost + 0.0001 < space.cost(next)) {
                    space.set(next, nextCost, current);
                    open.add(nextCost + heuristic(nx, ny, goal), nextCost, next);
                }
            }
        }

        return List.of();
    }

    /** Descripción: Abandona el encaminado si el hilo ha sido interrumpido (misma convención que el analizador textual). Salida: lanza CancellationException. Entradas: no requiere parámetros de entrada. */
    private static void checkInterrupted() {
        if(Thread.currentThread().isInterrupted()) {
            throw new java.util.concurrent.CancellationException("Metamodel edge routing cancelled");
        }
    }

    // neighbour order of RoutingGrid.neighbours: left, right, up, down (the first two are horizontal steps)
    private static final int[] NEIGHBOUR_DX = {-1, 1, 0, 0};
    private static final int[] NEIGHBOUR_DY = {0, 0, -1, 1};

    /** Descripción: Ejecuta la operación «turnCost» correspondiente a la responsabilidad de SearchNode. Entradas: previousDirection: dato de entrada necesario para la operación; nextDirection: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private double turnCost(int previousDirection, int nextDirection) {
        if(previousDirection == Direction.NONE.code || previousDirection == nextDirection) {
            return 0;
        }

        return Math.max(0, properties.getRoutingTurnCost());
    }

    /** Descripción: Ejecuta la operación «heuristic» correspondiente a la responsabilidad de SearchNode. Entradas: current: dato de entrada necesario para la operación; goal: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private double heuristic(int x, int y, GridPoint goal) {
        return (Math.abs(goal.x() - x) + Math.abs(goal.y() - y)) * Math.max(1, properties.getRoutingGridSize());
    }

    /** Descripción: Reconstruye el camino desde el estado destino siguiendo los predecesores. Entradas: rejilla, espacio de búsqueda y estado destino. Salida: celdas del camino, del origen al destino. */ private List<GridPoint> reconstruct(RoutingGrid grid, SearchSpace space, int goal) {
        List<GridPoint> reversed = new ArrayList<>();
        int current = goal;
        reversed.add(grid.point(current));

        while(space.previous(current) >= 0) {
            current = space.previous(current);
            reversed.add(grid.point(current));
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

    private record Step(GridPoint point, boolean horizontal) {
    }

    /**
     * Estado del A* por estado (celda x dirección) en arrays reutilizables: el coste y el predecesor solo valen para la búsqueda en curso
     * (marca de generación), así que no se reinicializan entre aristas.
     */
    private static final class SearchSpace {
        private final double[] cost;
        private final int[] previous;
        private final int[] generationOf;
        private int generation;

        /** Descripción: Reserva el espacio de una rejilla. Entradas: número de estados (celdas x 3 direcciones). Salida: deja la instancia inicializada y lista para su uso. */ private SearchSpace(int states) {
            cost = new double[states];
            previous = new int[states];
            generationOf = new int[states];
        }

        /** Descripción: Empieza una búsqueda nueva invalidando la anterior en O(1). Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void begin() {
            generation += 1;
        }

        /** Descripción: Coste conocido de un estado en esta búsqueda (infinito si no se ha alcanzado). Entradas: state: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private double cost(int state) {
            return generationOf[state] == generation ? cost[state] : Double.POSITIVE_INFINITY;
        }

        /** Descripción: Predecesor de un estado en esta búsqueda (-1 si no tiene). Entradas: state: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int previous(int state) {
            return generationOf[state] == generation ? previous[state] : -1;
        }

        /** Descripción: Registra el mejor coste de un estado y su predecesor. Entradas: state: dato de entrada necesario para la operación; value: valor de entrada que debe procesarse; from: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void set(int state, double value, int from) {
            generationOf[state] = generation;
            cost[state] = value;
            previous[state] = from;
        }
    }

    /**
     * Montículo binario de estados abiertos con el MISMO orden total que la PriorityQueue anterior: coste estimado total (Double.compare), x,
     * y, dirección. Dos entradas distintas nunca empatan en esa clave, así que el orden de extracción es el mismo con cualquier montículo.
     */
    private static final class StateHeap {
        private final int width;
        private double[] estimated = new double[256];
        private double[] cost = new double[256];
        private int[] state = new int[256];
        private int size;

        /** Descripción: Crea el montículo. Entradas: ancho de la rejilla en celdas (para desempatar por x e y). Salida: deja la instancia inicializada y lista para su uso. */ private StateHeap(int width) {
            this.width = width;
        }

        /** Descripción: Ejecuta la operación «size» correspondiente a la responsabilidad de OrthogonalMetamodelEdgeRoutingEngine. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private int size() {
            return size;
        }

        /** Descripción: Ejecuta la operación «topCost» correspondiente a la responsabilidad de OrthogonalMetamodelEdgeRoutingEngine. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private double topCost() {
            return cost[0];
        }

        /** Descripción: Ejecuta la operación «add» correspondiente a la responsabilidad de OrthogonalMetamodelEdgeRoutingEngine. Entradas: estimatedTotal: dato de entrada necesario para la operación; knownCost: dato de entrada necesario para la operación; value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void add(double estimatedTotal, double knownCost, int value) {
            if(size == state.length) {
                estimated = java.util.Arrays.copyOf(estimated, size * 2);
                cost = java.util.Arrays.copyOf(cost, size * 2);
                state = java.util.Arrays.copyOf(state, size * 2);
            }
            int index = size++;
            while(index > 0) {
                int parent = (index - 1) >>> 1;
                if(compare(estimatedTotal, value, estimated[parent], state[parent]) >= 0) break;
                move(parent, index);
                index = parent;
            }
            estimated[index] = estimatedTotal;
            cost[index] = knownCost;
            state[index] = value;
        }

        /** Descripción: Ejecuta la operación «poll» correspondiente a la responsabilidad de OrthogonalMetamodelEdgeRoutingEngine. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private int poll() {
            int result = state[0];
            size -= 1;
            if(size > 0) {
                double lastEstimated = estimated[size];
                double lastCost = cost[size];
                int lastState = state[size];
                int index = 0;
                while(true) {
                    int child = 2 * index + 1;
                    if(child >= size) break;
                    if(child + 1 < size && compare(estimated[child + 1], state[child + 1], estimated[child], state[child]) < 0) child += 1;
                    if(compare(lastEstimated, lastState, estimated[child], state[child]) <= 0) break;
                    move(child, index);
                    index = child;
                }
                estimated[index] = lastEstimated;
                cost[index] = lastCost;
                state[index] = lastState;
            }
            return result;
        }

        /** Descripción: Ejecuta la operación «move» correspondiente a la responsabilidad de OrthogonalMetamodelEdgeRoutingEngine. Entradas: from: dato de entrada necesario para la operación; to: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void move(int from, int to) {
            estimated[to] = estimated[from];
            cost[to] = cost[from];
            state[to] = state[from];
        }

        /** Descripción: Orden de la PriorityQueue anterior: estimado, x, y, dirección. Entradas: firstEstimated: dato de entrada necesario para la operación; first: dato de entrada necesario para la operación; secondEstimated: dato de entrada necesario para la operación; second: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int compare(double firstEstimated, int first, double secondEstimated, int second) {
            int byEstimate = Double.compare(firstEstimated, secondEstimated);
            if(byEstimate != 0) return byEstimate;
            int firstCell = first / 3;
            int secondCell = second / 3;
            int byX = Integer.compare(firstCell % width, secondCell % width);
            if(byX != 0) return byX;
            int byY = Integer.compare(firstCell / width, secondCell / width);
            if(byY != 0) return byY;
            return Integer.compare(first % 3, second % 3);
        }
    }

    /**
     * Representa la cuadrícula, los obstáculos y los canales ya utilizados (arrays por celda y por segmento; las reglas y la aritmética son las
     * de la versión anterior con conjuntos y mapas).
     */
    private static final class RoutingGrid {

        private final double gridSize;
        private final double originX;
        private final double originY;
        private final int maximumX;
        private final int maximumY;
        private final int width;
        private final boolean[] blocked;
        // uso de un segmento horizontal (x, y)-(x+1, y) en el índice de la celda (x, y); vertical (x, y)-(x, y+1) en el de (x, y)
        private final int[] horizontalUsage;
        private final int[] verticalUsage;
        // celdas de llegada de los tramos reservados (para el coste de cruce)
        private final int[] horizontalCells;
        private final int[] verticalCells;
        private final List<NodeDesign> nodes;
        private SearchSpace searchSpace;

        /** Descripción: Inicializa la rejilla. Entradas: tamaño, origen, extensión, celdas bloqueadas y nodos. Salida: deja la instancia inicializada y lista para su uso. */ private RoutingGrid(double gridSize, double originX, double originY, int maximumX, int maximumY, boolean[] blocked, List<NodeDesign> nodes) {
            this.gridSize = gridSize;
            this.originX = originX;
            this.originY = originY;
            this.maximumX = maximumX;
            this.maximumY = maximumY;
            this.width = maximumX + 1;
            this.blocked = blocked;
            int cells = blocked.length;
            this.horizontalUsage = new int[cells];
            this.verticalUsage = new int[cells];
            this.horizontalCells = new int[cells];
            this.verticalCells = new int[cells];
            this.nodes = List.copyOf(nodes);
        }

        /** Descripción: Crea la rejilla de los nodos con su holgura. Entradas: nodos y propiedades. Salida: la rejilla. */ private static RoutingGrid create(List<NodeDesign> nodes, MetamodelLayoutProperties properties) {
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
            boolean[] blocked = new boolean[(maximumX + 1) * (maximumY + 1)];

            for(NodeDesign node : nodes) {
                int left = (int) Math.floor((node.x() - clearance - originX) / gridSize);
                int right = (int) Math.ceil((node.x() + node.width() + clearance - originX) / gridSize);
                int top = (int) Math.floor((node.y() - clearance - originY) / gridSize);
                int bottom = (int) Math.ceil((node.y() + node.height() + clearance - originY) / gridSize);

                for(int x = Math.max(0, left); x <= Math.min(maximumX, right); x += 1) {
                    for(int y = Math.max(0, top); y <= Math.min(maximumY, bottom); y += 1) {
                        blocked[y * (maximumX + 1) + x] = true;
                    }
                }
            }

            return new RoutingGrid(gridSize, originX, originY, maximumX, maximumY, blocked, nodes);
        }

        /** Descripción: Ajusta un punto a la celda más próxima dentro de la rejilla. Entradas: point: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private GridPoint snap(RoutePointDesign point) {
            int x = (int) Math.round((point.x() - originX) / gridSize);
            int y = (int) Math.round((point.y() - originY) / gridSize);
            return new GridPoint(Math.max(0, Math.min(maximumX, x)), Math.max(0, Math.min(maximumY, y)));
        }

        /** Descripción: Coordenadas de diagrama de una celda. Entradas: point: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RoutePointDesign toPoint(GridPoint point) {
            return new RoutePointDesign(originX + point.x() * gridSize, originY + point.y() * gridSize);
        }

        /** Descripción: Vecinos dentro de la rejilla en el orden izquierda, derecha, arriba, abajo. Entradas: point: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<Step> neighbours(GridPoint point) {
            List<Step> result = new ArrayList<>(4);
            add(result, point.x() - 1, point.y(), true);
            add(result, point.x() + 1, point.y(), true);
            add(result, point.x(), point.y() - 1, false);
            add(result, point.x(), point.y() + 1, false);
            return result;
        }

        /** Descripción: Ejecuta la operación «add» correspondiente a la responsabilidad de OrthogonalMetamodelEdgeRoutingEngine. Entradas: result: dato de entrada necesario para la operación; x: dato de entrada necesario para la operación; y: dato de entrada necesario para la operación; horizontal: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void add(List<Step> result, int x, int y, boolean horizontal) {
            if(inside(x, y)) {
                result.add(new Step(new GridPoint(x, y), horizontal));
            }
        }

        /** Descripción: Ejecuta la operación «inside» correspondiente a la responsabilidad de OrthogonalMetamodelEdgeRoutingEngine. Entradas: x: dato de entrada necesario para la operación; y: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean inside(int x, int y) {
            return x >= 0 && x <= maximumX && y >= 0 && y <= maximumY;
        }

        /** Descripción: Evalúa la condición «isBlocked» sin modificar el estado externo. Entradas: point: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isBlocked(GridPoint point) {
            return blocked[index(point)];
        }

        /** Descripción: Ejecuta la operación «blocked» correspondiente a la responsabilidad de OrthogonalMetamodelEdgeRoutingEngine. Entradas: x: dato de entrada necesario para la operación; y: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean blocked(int x, int y) {
            return blocked[y * width + x];
        }

        /** Descripción: Ejecuta la operación «width» correspondiente a la responsabilidad de OrthogonalMetamodelEdgeRoutingEngine. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private int width() {
            return width;
        }

        /** Descripción: Índice de estado (celda x 3 + dirección). Entradas: x: dato de entrada necesario para la operación; y: dato de entrada necesario para la operación; direction: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int state(int x, int y, int direction) {
            return (y * width + x) * 3 + direction;
        }

        /** Descripción: Ejecuta la operación «point» correspondiente a la responsabilidad de OrthogonalMetamodelEdgeRoutingEngine. Entradas: state: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private GridPoint point(int state) {
            int cell = state / 3;
            return new GridPoint(cell % width, cell / width);
        }

        /** Descripción: Espacio de búsqueda reutilizado por todas las aristas de esta rejilla. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private SearchSpace searchSpace() {
            if(searchSpace == null) searchSpace = new SearchSpace(blocked.length * 3);
            return searchSpace;
        }

        /**
         * Descripción: Indica si findPath puede llegar de start a goal con sus mismas reglas de paso: una celda bloqueada solo se pisa si es el
         * origen o el destino. El origen une las componentes de sus vecinos libres; el destino es alcanzable si es el origen, es vecino suyo o
         * él o un vecino libre suyo cae en una de esas componentes. Los obstáculos no cambian durante el encaminado (reserve solo altera costes),
         * así que las componentes se calculan una vez. Entradas: start y goal. Salida: false solo cuando no existe ningún camino.
         */
        private boolean connected(GridPoint start, GridPoint goal) {
            if(start.equals(goal)) {
                return true;
            }
            int[] labels = components();
            Set<Integer> reachable = new HashSet<>();
            if(!isBlocked(start)) reachable.add(labels[index(start)]);
            for(Step step : neighbours(start)) {
                if(step.point().equals(goal)) return true;
                if(!isBlocked(step.point())) reachable.add(labels[index(step.point())]);
            }
            if(!isBlocked(goal) && reachable.contains(labels[index(goal)])) return true;
            for(Step step : neighbours(goal)) {
                if(!isBlocked(step.point()) && reachable.contains(labels[index(step.point())])) return true;
            }
            return false;
        }

        private int[] componentLabels;

        /** Descripción: Etiqueta (una vez) las componentes conexas de las celdas libres con un recorrido en anchura. Salida: etiqueta por celda (-1 si está bloqueada). Entradas: no requiere parámetros de entrada. */
        private int[] components() {
            if(componentLabels != null) return componentLabels;
            int[] labels = new int[blocked.length];
            java.util.Arrays.fill(labels, -1);
            int next = 0;
            int[] queue = new int[blocked.length];
            for(int seed = 0; seed < blocked.length; seed += 1) {
                if(labels[seed] != -1 || blocked[seed]) continue;
                int head = 0;
                int tail = 0;
                labels[seed] = next;
                queue[tail++] = seed;
                while(head < tail) {
                    int cell = queue[head++];
                    int x = cell % width;
                    int y = cell / width;
                    for(int k = 0; k < 4; k += 1) {
                        int nx = x + NEIGHBOUR_DX[k];
                        int ny = y + NEIGHBOUR_DY[k];
                        if(!inside(nx, ny)) continue;
                        int neighbour = ny * width + nx;
                        if(labels[neighbour] == -1 && !blocked[neighbour]) {
                            labels[neighbour] = next;
                            queue[tail++] = neighbour;
                        }
                    }
                }
                next += 1;
            }
            componentLabels = labels;
            return labels;
        }

        /** Descripción: Ejecuta la operación «index» correspondiente a la responsabilidad de OrthogonalMetamodelEdgeRoutingEngine. Entradas: point: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int index(GridPoint point) {
            return point.y() * width + point.x();
        }

        /** Descripción: Coste de congestión y cruce de un paso de (x, y) a (nx, ny), con la misma fórmula que la versión con mapas. Entradas: x: dato de entrada necesario para la operación; y: dato de entrada necesario para la operación; nx: dato de entrada necesario para la operación; ny: dato de entrada necesario para la operación; horizontal: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ private double congestionCost(int x, int y, int nx, int ny, boolean horizontal, MetamodelLayoutProperties properties) {
            int segment = horizontal ? y * width + Math.min(x, nx) : Math.min(y, ny) * width + x;
            int used = horizontal ? horizontalUsage[segment] : verticalUsage[segment];
            int crossing = horizontal ? verticalCells[ny * width + nx] : horizontalCells[ny * width + nx];
            double edgeGapFactor = 1 + Math.max(0, properties.getRoutingEdgeGap()) / Math.max(1, gridSize);
            double result = used * Math.max(0, properties.getRoutingCongestionCost()) * edgeGapFactor;
            result += crossing * Math.max(0, properties.getRoutingCrossingCost());

            if(!properties.isParallelRouting() && used > 0) {
                result += properties.getRoutingCrossingCost() * 5;
            }

            return result;
        }

        /** Descripción: Reserva los tramos ortogonales de una ruta ya trazada. Entradas: route: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void reserve(List<RoutePointDesign> route) {
            for(int index = 0; index < route.size() - 1; index += 1) {
                GridPoint first = snap(route.get(index));
                GridPoint second = snap(route.get(index + 1));

                if(first.x() == second.x()) {
                    int minimum = Math.min(first.y(), second.y());

                    for(int y = minimum; y < Math.max(first.y(), second.y()); y += 1) {
                        verticalUsage[y * width + first.x()] += 1;
                        verticalCells[(y + 1) * width + first.x()] += 1;
                    }
                } else if(first.y() == second.y()) {
                    int minimum = Math.min(first.x(), second.x());

                    for(int x = minimum; x < Math.max(first.x(), second.x()); x += 1) {
                        horizontalUsage[first.y() * width + x] += 1;
                        horizontalCells[first.y() * width + x + 1] += 1;
                    }
                }
            }
        }

        /** Descripción: Ejecuta la operación «gridSize» correspondiente a la responsabilidad de OrthogonalMetamodelEdgeRoutingEngine. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private double gridSize() {
            return gridSize;
        }

        /** Descripción: Ejecuta la operación «nodes» correspondiente a la responsabilidad de OrthogonalMetamodelEdgeRoutingEngine. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private List<NodeDesign> nodes() {
            return nodes;
        }
    }
}
