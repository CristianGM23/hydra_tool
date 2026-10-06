package com.example.editor.validation.validation;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Traza el origen externo de una regla sin convertirlo en dependencia del núcleo. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ValidationProvenance(String technology, String sourceFile, String sourceRule, String sourceHash, Instant importedAt, String originalFragment) {
    public ValidationProvenance {
        technology = technology == null ? "" : technology;
        sourceFile = sourceFile == null ? "" : sourceFile;
        sourceRule = sourceRule == null ? "" : sourceRule;
        sourceHash = sourceHash == null ? "" : sourceHash;
        originalFragment = originalFragment == null ? "" : originalFragment;
    }
}
