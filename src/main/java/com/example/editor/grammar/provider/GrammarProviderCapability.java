package com.example.editor.grammar.provider;

/** Provider features negotiated by HYDRA instead of branching on a technology in the UI. */
public enum GrammarProviderCapability {
    ANALYZE,
    INCREMENTAL_ANALYSIS,
    CODE_ACTIONS,
    COMPLETION,
    HOVER,
    DEFINITION,
    REFERENCES,
    RENAME,
    FORMATTING,
    FOLDING,
    DOCUMENT_SYMBOLS,
    SEMANTIC_TOKENS,
    SEMANTIC_MAPPING,
    ECORE_INFERENCE,
    ECORE_IMPORT,
    PROJECT_GENERATION,
    LANGUAGE_SERVER,
    BROWSER_WORKER,
    JVM_RUNTIME,
    LEXER_PARSER,
    VISITOR_LISTENER,
    TEXTUAL_RUNTIME,
    STANDALONE_WEB,
    STANDALONE_DESKTOP
}
