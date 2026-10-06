package com.example.editor.artifact.registry;

import java.util.List;

/** Grafo completo de dependencias con aristas tipadas, ciclos y diagnósticos de disponibilidad. */
public record HydraArtifactDependencyGraph(List<HydraArtifactGraphNode> nodes,
        List<List<String>> cycles, List<HydraArtifactGraphEdge> edges, boolean ready) {
    public HydraArtifactDependencyGraph {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        cycles = cycles == null ? List.of() : cycles.stream().map(List::copyOf).toList();
        edges = edges == null ? List.of() : List.copyOf(edges);
    }

    /** Descripción: Mantiene compatibilidad con consumidores anteriores que no materializaban las aristas. Entradas: nodes: nodos; cycles: ciclos; ready: disponibilidad. Salida: crea un grafo sin aristas explícitas. */
    public HydraArtifactDependencyGraph(List<HydraArtifactGraphNode> nodes, List<List<String>> cycles, boolean ready) {
        this(nodes, cycles, List.of(), ready);
    }
}
