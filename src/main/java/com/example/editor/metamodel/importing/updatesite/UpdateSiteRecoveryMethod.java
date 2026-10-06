package com.example.editor.metamodel.importing.updatesite;

/** Estrategia usada para reconstruir un metamodelo neutral. */
public enum UpdateSiteRecoveryMethod {
    DIRECT_METAMODEL,
    GENMODEL,
    PLUGIN_REGISTRY,
    EMF_BYTECODE,
    SOURCE_INFERENCE,
    CODE_INFERENCE
}
