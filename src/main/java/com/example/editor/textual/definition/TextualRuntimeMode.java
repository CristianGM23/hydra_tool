package com.example.editor.textual.definition;

/** Requested deployment/runtime strategy. AUTO negotiates against the active grammar snapshot. */
public enum TextualRuntimeMode {
    AUTO,
    LANGUAGE_SERVER,
    BROWSER_WORKER,
    PARSER_BRIDGE
}
