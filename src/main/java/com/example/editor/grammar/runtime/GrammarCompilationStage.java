package com.example.editor.grammar.runtime;

/** Ordered stages of the explicit grammar compilation/promote pipeline. */
public enum GrammarCompilationStage {
    FREEZE_DRAFT,
    FULL_ANALYSIS,
    RESOLVE_IMPORTS,
    VALIDATE_SEMANTIC_BINDING,
    RECONCILE_SEMANTIC_CONTRACT,
    CHECK_HYDRA_LAYERS,
    GENERATE_PROJECT,
    GENERATE_SEMANTIC_ARTIFACTS,
    MATERIALIZE_RUNTIME,
    SMOKE_TEST,
    REGISTER_ARTIFACTS,
    PROMOTE_SNAPSHOT
}
