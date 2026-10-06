package com.example.editor.portability.orchestration;

import com.example.editor.portability.transaction.CapabilityLayerTransactionResult;

/** Preview plus the optional AI persistence transaction. */
public record CapabilityOrchestrationResult(CapabilityOrchestrationPreview preview,
        CapabilityLayerTransactionResult transaction) { }
