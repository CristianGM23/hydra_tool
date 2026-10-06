package com.example.editor.operation.runtime;
import java.util.List;
import com.example.editor.model.ModelDocumentResponse;
public record OperationInvocationResult(String operationId, Object value, ModelDocumentResponse document, boolean undoAvailable, List<String> messages) { public OperationInvocationResult { messages = messages == null ? List.of() : List.copyOf(messages); } }
