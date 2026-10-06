package com.example.editor.event.flow.runtime;

import java.time.Instant;
import com.example.editor.event.flow.FlowNodeKind;

public record FlowNodeExecution(String nodeId, FlowNodeKind nodeKind, FlowExecutionStatus status, String message, Instant executedAt) {
    public FlowNodeExecution { if(executedAt == null) executedAt = Instant.now(); message = message == null ? "" : message; }
}
