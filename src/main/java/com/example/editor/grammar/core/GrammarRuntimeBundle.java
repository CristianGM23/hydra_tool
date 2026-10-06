package com.example.editor.grammar.core;

import java.util.Map;

/** Materialized textual runtime metadata produced without exposing provider-specific implementation classes. */
public record GrammarRuntimeBundle(GrammarRuntimeDescriptor descriptor, Map<String, String> files, Map<String, String> metadata) {
    public GrammarRuntimeBundle {
        descriptor = descriptor == null ? new GrammarRuntimeDescriptor("", "", java.util.List.of(), "", "", Map.of()) : descriptor;
        files = files == null ? Map.of() : Map.copyOf(files);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
