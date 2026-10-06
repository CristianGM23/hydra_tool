package com.example.editor.persistence.evolution;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;

/** Aplica migraciones de esquema registradas de forma determinista, planificable y sin ciclos. */
@Service
public final class HydraSchemaEvolutionService {
    private static final int MAX_STEPS = 32;
    private final Map<String, Map<String, HydraJsonSchemaEvolution>> evolutions;

    /** Descripción: Inicializa el servicio indexando las migraciones por namespace y versión origen, rechazando ambigüedades y ciclos. Entradas: evolutions: migraciones registradas por Spring. Salida: deja un grafo inmutable y validado. */
    public HydraSchemaEvolutionService(List<HydraJsonSchemaEvolution> evolutions) {
        LinkedHashMap<String, Map<String, HydraJsonSchemaEvolution>> byNamespace = new LinkedHashMap<>();
        for (HydraJsonSchemaEvolution evolution : evolutions == null ? List.<HydraJsonSchemaEvolution>of() : evolutions) {
            String namespace = text(evolution.namespace(), "namespace");
            String from = text(evolution.fromVersion(), "fromVersion");
            String to = text(evolution.toVersion(), "toVersion");
            if (from.equals(to)) throw new IllegalStateException("Schema evolution must advance the version: " + namespace + " " + from);
            Map<String, HydraJsonSchemaEvolution> byVersion = byNamespace.computeIfAbsent(namespace, ignored -> new LinkedHashMap<>());
            if (byVersion.putIfAbsent(from, evolution) != null) {
                throw new IllegalStateException("Ambiguous schema evolution: " + namespace + " from " + from);
            }
        }
        byNamespace.forEach(HydraSchemaEvolutionService::validateAcyclic);
        LinkedHashMap<String, Map<String, HydraJsonSchemaEvolution>> immutable = new LinkedHashMap<>();
        byNamespace.forEach((namespace, values) -> immutable.put(namespace, Map.copyOf(values)));
        this.evolutions = Map.copyOf(immutable);
    }

    /** Descripción: Crea un servicio sin migraciones para constructores de compatibilidad y pruebas focales. Entradas: no requiere parámetros. Salida: devuelve un servicio vacío. */
    public static HydraSchemaEvolutionService none() { return new HydraSchemaEvolutionService(List.of()); }

    /** Descripción: Planifica una migración hasta una versión destino concreta sin modificar el documento. Entradas: namespace: familia documental; fromVersion: versión de partida; targetVersion: versión requerida. Salida: devuelve pasos ordenados y diagnósticos de alcanzabilidad. */
    public HydraSchemaEvolutionPlan plan(String namespace, String fromVersion, String targetVersion) {
        String safeNamespace = text(namespace, "namespace");
        String from = text(fromVersion, "fromVersion");
        String target = text(targetVersion, "targetVersion");
        if (from.equals(target)) return new HydraSchemaEvolutionPlan(safeNamespace, from, target, true, List.of(), List.of());
        Map<String, HydraJsonSchemaEvolution> steps = evolutions.getOrDefault(safeNamespace, Map.of());
        List<String> planned = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        String current = from;
        for (int index = 0; index < MAX_STEPS; index++) {
            if (!visited.add(current)) {
                return new HydraSchemaEvolutionPlan(safeNamespace, from, target, false, planned,
                        List.of("Schema evolution cycle detected at version " + current));
            }
            HydraJsonSchemaEvolution step = steps.get(current);
            if (step == null) {
                return new HydraSchemaEvolutionPlan(safeNamespace, from, target, false, planned,
                        List.of("No schema evolution step is registered from version " + current + " to reach " + target));
            }
            planned.add(current + "->" + step.toVersion());
            current = step.toVersion();
            if (target.equals(current)) return new HydraSchemaEvolutionPlan(safeNamespace, from, target, true, planned, List.of());
        }
        return new HydraSchemaEvolutionPlan(safeNamespace, from, target, false, planned,
                List.of("Schema evolution chain exceeds " + MAX_STEPS + " steps"));
    }

    /** Descripción: Evoluciona un documento hasta la última versión alcanzable mediante la cadena registrada, conservando compatibilidad con el comportamiento anterior. Entradas: namespace: familia documental; source: documento JSON. Salida: devuelve el documento resultante y los pasos aplicados. */
    public EvolutionResult evolve(String namespace, JsonNode source) {
        if (source == null || !source.isObject()) return new EvolutionResult(source, false, List.of());
        Map<String, HydraJsonSchemaEvolution> steps = evolutions.get(namespace);
        JsonNode schema = source.get("schemaVersion");
        if (steps == null || steps.isEmpty() || schema == null || !schema.isTextual() || schema.asString().isBlank()) {
            return new EvolutionResult(source, false, List.of());
        }
        return evolveChain(namespace, source, null);
    }

    /** Descripción: Evoluciona un documento hasta una versión destino concreta y falla cerrado si no existe una ruta completa. Entradas: namespace: familia documental; source: documento JSON; targetVersion: versión final requerida. Salida: devuelve el documento evolucionado y los pasos aplicados. */
    public EvolutionResult evolveTo(String namespace, JsonNode source, String targetVersion) {
        if (source == null || !source.isObject()) throw new IllegalArgumentException("Persisted JSON document is required");
        JsonNode schema = source.get("schemaVersion");
        if (schema == null || !schema.isTextual() || schema.asString().isBlank()) throw new IllegalStateException("schemaVersion is required for explicit migration");
        HydraSchemaEvolutionPlan plan = plan(namespace, schema.asString(), targetVersion);
        if (!plan.reachable()) throw new IllegalStateException(String.join("; ", plan.diagnostics()));
        return evolveChain(namespace, source, targetVersion);
    }

    /** Descripción: Devuelve los namespaces para los que existe al menos una migración registrada. Entradas: no requiere parámetros. Salida: lista ordenada de namespaces. */
    public List<String> namespaces() { return evolutions.keySet().stream().sorted().toList(); }

    /** Descripción: Ejecuta una cadena validada de migraciones hasta que no haya más pasos o se alcance la versión destino. Entradas: namespace: familia documental; source: documento; targetVersion: destino opcional. Salida: resultado de evolución. */
    private EvolutionResult evolveChain(String namespace, JsonNode source, String targetVersion) {
        Map<String, HydraJsonSchemaEvolution> steps = evolutions.getOrDefault(namespace, Map.of());
        JsonNode current = source;
        String version = source.get("schemaVersion").asString();
        List<String> applied = new ArrayList<>();
        for (int index = 0; index < MAX_STEPS; index++) {
            if (targetVersion != null && targetVersion.equals(version)) return new EvolutionResult(current, !applied.isEmpty(), List.copyOf(applied));
            HydraJsonSchemaEvolution step = steps.get(version);
            if (step == null) {
                if (targetVersion != null) throw new IllegalStateException("Schema evolution path stopped at " + version + " before " + targetVersion + " for " + namespace);
                return new EvolutionResult(current, !applied.isEmpty(), List.copyOf(applied));
            }
            JsonNode evolved = step.evolve(current.deepCopy());
            if (evolved == null || !evolved.isObject()) throw new IllegalStateException("Schema evolution returned an invalid document: " + namespace);
            JsonNode evolvedVersion = evolved.get("schemaVersion");
            if (evolvedVersion == null || !evolvedVersion.isTextual() || !step.toVersion().equals(evolvedVersion.asString())) {
                throw new IllegalStateException("Schema evolution did not publish expected version " + step.toVersion() + " for " + namespace);
            }
            applied.add(version + "->" + step.toVersion());
            current = evolved;
            version = step.toVersion();
        }
        throw new IllegalStateException("Schema evolution chain exceeds " + MAX_STEPS + " steps for " + namespace);
    }

    /** Descripción: Comprueba que las migraciones de un namespace no puedan volver a una versión ya visitada. Entradas: namespace: familia documental; steps: migraciones indexadas por versión origen. Salida: no devuelve valor y falla si detecta un ciclo. */
    private static void validateAcyclic(String namespace, Map<String, HydraJsonSchemaEvolution> steps) {
        for (String start : steps.keySet()) {
            Set<String> visited = new LinkedHashSet<>();
            String current = start;
            for (int index = 0; index <= MAX_STEPS; index++) {
                if (!visited.add(current)) throw new IllegalStateException("Cyclic schema evolution: " + namespace + " at " + current);
                HydraJsonSchemaEvolution step = steps.get(current);
                if (step == null) break;
                current = step.toVersion();
            }
        }
    }

    /** Descripción: Valida y normaliza los textos obligatorios de una migración. Entradas: value: valor; field: nombre del campo. Salida: texto normalizado. */
    private static String text(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalStateException("Schema evolution " + field + " is required");
        return value.trim();
    }

    public record EvolutionResult(JsonNode document, boolean changed, List<String> appliedSteps) {
        public EvolutionResult { appliedSteps = appliedSteps == null ? List.of() : List.copyOf(appliedSteps); }
    }
}
