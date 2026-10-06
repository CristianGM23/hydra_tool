package com.example.editor.metamodel.behavior;

import com.example.editor.metamodel.core.OperationBehaviorModel;
import com.example.editor.metamodel.validation.ValidationReport;

/** Resultado de guardar bindings y comprobar sus referencias. */
public record OperationBehaviorModelSaveResponse(OperationBehaviorModel behaviorModel, ValidationReport validation, String etag) { }
