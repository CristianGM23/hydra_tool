package com.example.editor.portability;

/** Define dónde se ejecuta una capacidad portable sin confundir su presencia con su runtime. */
public enum CapabilityExecutionMode {
    EMBEDDED,
    REMOTE,
    HYBRID,
    PASSIVE
}
