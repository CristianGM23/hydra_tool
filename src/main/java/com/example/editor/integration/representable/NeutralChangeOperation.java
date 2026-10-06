package com.example.editor.integration.representable;

import java.util.Map;

/** Technology-neutral operation reconstructed from one external change. */
public record NeutralChangeOperation(String externalId, ExternalChangeKind changeKind,
        NeutralElementKind elementKind, String neutralPath, Map<String, Object> values) {
    public NeutralChangeOperation {
        elementKind = elementKind == null ? NeutralElementKind.UNKNOWN : elementKind;
        neutralPath = neutralPath == null ? "" : neutralPath.trim();
        values = values == null ? Map.of() : Map.copyOf(values);
    }
}
