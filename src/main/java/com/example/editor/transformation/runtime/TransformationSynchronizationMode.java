package com.example.editor.transformation.runtime;

/** Estrategia de reconciliación para una transformación M2M bidireccional. */
public enum TransformationSynchronizationMode {
    /** Reconstruye el lado opuesto completo, comportamiento disponible desde schema 1.1/Fix38. */
    BATCH,
    /** Propaga únicamente el delta respecto a una baseline y reutiliza correspondencias previas. */
    INCREMENTAL
}
