package com.example.editor.metamodel.behavior;

import com.example.editor.metamodel.core.OperationBehaviorModel;

/** Documento de comportamientos cargado junto con su ETag. */
public record OperationBehaviorModelLoadResult(OperationBehaviorModel behaviorModel, String etag) { }
