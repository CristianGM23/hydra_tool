package com.example.editor.metamodel.importing.updatesite;

/** Etapa estable del flujo de recuperación p2 mostrada en logs y dashboard. */
public enum UpdateSiteImportStage {
    SOURCE,
    P2_METADATA,
    FEATURES,
    BUNDLES,
    METAMODEL_RECOVERY,
    GRAMMAR_ANALYSIS,
    LAYER_RECOVERY,
    RECONCILIATION,
    MATERIALIZATION,
    RELEASE_ANALYSIS,
    SEMANTIC_RECOVERY
}
