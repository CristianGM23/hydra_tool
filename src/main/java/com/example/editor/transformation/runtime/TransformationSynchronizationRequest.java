package com.example.editor.transformation.runtime;

import com.example.editor.validation.model.CanonicalModelDocument;

/** Solicitud de sincronización M2M bidireccional batch o incremental. */
public record TransformationSynchronizationRequest(
        CanonicalModelDocument sourceModel,
        CanonicalModelDocument targetModel,
        TransformationSynchronizationSide changedSide,
        TransformationSynchronizationMode mode,
        CanonicalModelDocument baselineSourceModel,
        CanonicalModelDocument baselineTargetModel,
        TransformationTrace baselineTrace) {
    public TransformationSynchronizationRequest {
        changedSide = changedSide == null ? TransformationSynchronizationSide.SOURCE : changedSide;
        mode = mode == null ? TransformationSynchronizationMode.BATCH : mode;
    }

    /** Compatibilidad con clientes Fix38: las peticiones de tres campos siguen siendo batch. */
    /** Descripción: Inicializa una instancia de TransformationSynchronizationRequest con sus dependencias y datos de entrada. Entradas: sourceModel: dato de entrada necesario para la operación; targetModel: dato de entrada necesario para la operación; changedSide: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationSynchronizationRequest(CanonicalModelDocument sourceModel, CanonicalModelDocument targetModel,
            TransformationSynchronizationSide changedSide) {
        this(sourceModel, targetModel, changedSide, TransformationSynchronizationMode.BATCH, null, null, null);
    }
}
