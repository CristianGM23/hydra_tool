package com.example.editor.grammar.core;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Deterministic generated project held in memory until the compilation has fully succeeded. */
public record GeneratedGrammarProject(String projectId, Map<String,String> files, Map<String,String> metadata) {
    public GeneratedGrammarProject {
        projectId = text(projectId);
        Map<String,String> normalized = new LinkedHashMap<>();
        if (files != null) files.forEach((path, content) -> normalized.put(
                GrammarPathPolicy.requirePortableRelative(path, "generatedArtifact.path"), content == null ? "" : content));
        files = Map.copyOf(normalized);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
    /** Descripción: Ejecuta la operación «expandedBytes» correspondiente a la responsabilidad de GeneratedGrammarProject. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public long expandedBytes() {
        return files.entrySet().stream().mapToLong(entry -> entry.getKey().getBytes(StandardCharsets.UTF_8).length
                + entry.getValue().getBytes(StandardCharsets.UTF_8).length).sum();
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de GeneratedGrammarProject. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value==null?"":value.trim();}
}
