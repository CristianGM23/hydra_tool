package com.example.editor.artifact.registry;

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

import org.springframework.stereotype.Service;

/** Construye y consulta el grafo determinista de dependencias entre artefactos registrados. */
@Service
public class HydraArtifactGraphService {
    /** Descripción: Construye el grafo completo, materializando dependencias, dependientes, aristas y ciclos. Entradas: snapshot: registro de artefactos. Salida: grafo determinista. */
    public HydraArtifactDependencyGraph build(HydraArtifactRegistrySnapshot snapshot) {
        Map<String, HydraArtifactDescriptor> byId = index(snapshot);
        Map<String, List<String>> dependants = dependants(byId, false);
        List<HydraArtifactGraphEdge> edges = byId.values().stream()
                .flatMap(item -> item.dependencies().stream().map(dependency -> new HydraArtifactGraphEdge(item.id(), dependency.artifactId(),
                        dependency.relationKind(), dependency.required(), dependency.minimumVersion())))
                .sorted(Comparator.comparing(HydraArtifactGraphEdge::fromArtifactId).thenComparing(HydraArtifactGraphEdge::toArtifactId)
                        .thenComparing(edge -> edge.relationKind().name()))
                .toList();

        List<HydraArtifactGraphNode> nodes = byId.values().stream().map(item -> {
            List<String> missing = item.dependencies().stream()
                    .filter(HydraArtifactDependency::required)
                    .map(HydraArtifactDependency::artifactId)
                    .filter(id -> !byId.containsKey(id))
                    .sorted().toList();
            List<String> incompatibleVersions = item.dependencies().stream()
                    .filter(HydraArtifactDependency::required)
                    .filter(dependency -> byId.containsKey(dependency.artifactId()))
                    .filter(dependency -> !HydraArtifactVersionPolicy.supports(byId.get(dependency.artifactId()).version(), dependency.minimumVersion()))
                    .map(dependency -> dependency.artifactId() + " requires >= " + dependency.minimumVersion()
                            + " but found " + byId.get(dependency.artifactId()).version())
                    .sorted().toList();
            List<String> diagnostics = new ArrayList<>();
            missing.stream().map(id -> "Missing required dependency: " + id).forEach(diagnostics::add);
            incompatibleVersions.stream().map(value -> "Incompatible dependency version: " + value).forEach(diagnostics::add);
            HydraArtifactStatus status = !missing.isEmpty() ? HydraArtifactStatus.MISSING_DEPENDENCY
                    : !incompatibleVersions.isEmpty() ? HydraArtifactStatus.INCOMPATIBLE_DEPENDENCY : item.status();
            return new HydraArtifactGraphNode(item.id(), status,
                    item.dependencies().stream().map(HydraArtifactDependency::artifactId).sorted().toList(),
                    dependants.getOrDefault(item.id(), List.of()).stream().sorted().toList(), diagnostics);
        }).sorted(Comparator.comparing(HydraArtifactGraphNode::artifactId)).toList();

        List<List<String>> cycles = cycles(byId);
        boolean ready = cycles.isEmpty() && nodes.stream().allMatch(node ->
                node.status() == HydraArtifactStatus.AVAILABLE || node.status() == HydraArtifactStatus.STALE);
        return new HydraArtifactDependencyGraph(nodes, cycles, edges, ready);
    }

    /** Descripción: Calcula el impacto transitivo de retirar un artefacto y propone un orden seguro de eliminación de dependientes antes del objetivo. Entradas: snapshot: registro; artifactId: artefacto objetivo. Salida: impacto con dependientes directos, transitivos y bloqueantes. */
    public HydraArtifactImpact impact(HydraArtifactRegistrySnapshot snapshot, String artifactId) {
        Map<String, HydraArtifactDescriptor> byId = index(snapshot);
        if (!byId.containsKey(artifactId)) throw new IllegalArgumentException("Artifact was not found in registry: " + artifactId);
        Map<String, List<String>> allDependants = dependants(byId, false);
        Map<String, List<String>> requiredDependants = dependants(byId, true);
        List<String> direct = allDependants.getOrDefault(artifactId, List.of()).stream().sorted().toList();
        List<String> transitive = reverseReachable(artifactId, allDependants);
        List<String> blockers = reverseReachable(artifactId, requiredDependants);
        List<String> deletionOrder = deletionOrder(artifactId, allDependants);
        return new HydraArtifactImpact(artifactId, direct, transitive, blockers, deletionOrder);
    }

    /** Descripción: Indexa el snapshot por identificador conservando un orden estable. Entradas: snapshot: registro. Salida: mapa ordenado. */
    private Map<String, HydraArtifactDescriptor> index(HydraArtifactRegistrySnapshot snapshot) {
        Map<String, HydraArtifactDescriptor> byId = new LinkedHashMap<>();
        if (snapshot != null) snapshot.artifacts().forEach(item -> byId.put(item.id(), item));
        return byId;
    }

    /** Descripción: Construye el índice inverso dependency->dependants y permite filtrar solo relaciones obligatorias. Entradas: byId: artefactos; requiredOnly: filtro. Salida: mapa de dependientes. */
    private Map<String, List<String>> dependants(Map<String, HydraArtifactDescriptor> byId, boolean requiredOnly) {
        Map<String, List<String>> result = new HashMap<>();
        byId.values().forEach(item -> item.dependencies().stream()
                .filter(dependency -> !requiredOnly || dependency.required())
                .forEach(dependency -> result.computeIfAbsent(dependency.artifactId(), ignored -> new ArrayList<>()).add(item.id())));
        result.values().forEach(values -> values.sort(String::compareTo));
        return result;
    }

    /** Descripción: Recorre el índice inverso para obtener dependientes transitivos sin duplicados. Entradas: artifactId: raíz; dependants: índice inverso. Salida: identificadores transitivos ordenados. */
    private List<String> reverseReachable(String artifactId, Map<String, List<String>> dependants) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>(dependants.getOrDefault(artifactId, List.of()));
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!result.add(current)) continue;
            dependants.getOrDefault(current, List.of()).forEach(queue::addLast);
        }
        return result.stream().sorted().toList();
    }

    /** Descripción: Calcula un orden postorden que elimina primero los dependientes más profundos y finalmente el objetivo. Entradas: artifactId: objetivo; dependants: índice inverso. Salida: orden seguro y determinista. */
    private List<String> deletionOrder(String artifactId, Map<String, List<String>> dependants) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        deletionVisit(artifactId, dependants, new HashSet<>(), ordered);
        return List.copyOf(ordered);
    }

    /** Descripción: Recorre recursivamente dependientes para construir el postorden de eliminación. Entradas: id: nodo; dependants: índice; visiting: protección de ciclos; ordered: resultado. Salida: no devuelve valor. */
    private void deletionVisit(String id, Map<String, List<String>> dependants, Set<String> visiting, LinkedHashSet<String> ordered) {
        if (!visiting.add(id)) return;
        for (String dependant : dependants.getOrDefault(id, List.of()).stream().sorted().toList()) deletionVisit(dependant, dependants, visiting, ordered);
        ordered.add(id);
        visiting.remove(id);
    }

    /** Descripción: Detecta ciclos dirigidos del grafo de dependencias. Entradas: byId: artefactos indexados. Salida: lista determinista de ciclos. */
    private List<List<String>> cycles(Map<String, HydraArtifactDescriptor> byId) {
        List<List<String>> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> active = new HashSet<>();
        Deque<String> path = new ArrayDeque<>();
        byId.keySet().stream().sorted().forEach(id -> visit(id, byId, visited, active, path, result));
        return result.stream().distinct().sorted(Comparator.comparing(values -> String.join("/", values))).toList();
    }

    /** Descripción: Ejecuta DFS para detectar un ciclo y conservar su recorrido mínimo observado. Entradas: id: nodo; byId: artefactos; visited: visitados; active: pila activa; path: camino; cycles: salida. Salida: no devuelve valor. */
    private void visit(String id, Map<String, HydraArtifactDescriptor> byId, Set<String> visited,
            Set<String> active, Deque<String> path, List<List<String>> cycles) {
        if (active.contains(id)) {
            List<String> current = new ArrayList<>(path);
            int start = current.indexOf(id);
            if (start >= 0) cycles.add(List.copyOf(current.subList(start, current.size())));
            return;
        }
        if (!visited.add(id)) return;
        active.add(id); path.addLast(id);
        HydraArtifactDescriptor descriptor = byId.get(id);
        if (descriptor != null) {
            descriptor.dependencies().stream().map(HydraArtifactDependency::artifactId)
                    .filter(byId::containsKey).sorted()
                    .forEach(dependency -> visit(dependency, byId, visited, active, path, cycles));
        }
        path.removeLast(); active.remove(id);
    }
}
