package com.example.editor.portability.merge;

import com.example.editor.portability.transaction.CapabilityLayerTransactionResult;

/** Result of a preview-only or persisted deep semantic merge. */
public record CapabilitySemanticMergeResult(CapabilitySemanticMergePreview preview,
        CapabilityLayerTransactionResult transaction) { }
