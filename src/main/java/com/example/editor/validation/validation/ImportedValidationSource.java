package com.example.editor.validation.validation;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Archivo externo conservado para trazabilidad y reimportación. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ImportedValidationSource(String id, String technology, String fileName, String contentHash, ConversionStatus conversionStatus, Instant importedAt, Map<String, String> annotations) {
    public ImportedValidationSource {
        id = StableIdentifierPolicy.requireElementId(id, "id");
        if(technology == null || technology.isBlank()) throw new IllegalArgumentException("technology must not be blank");
        if(fileName == null || fileName.isBlank()) throw new IllegalArgumentException("fileName must not be blank");
        if(contentHash == null || contentHash.isBlank()) throw new IllegalArgumentException("contentHash must not be blank");
        if(conversionStatus == null) throw new IllegalArgumentException("conversionStatus must not be null");
        annotations = annotations == null || annotations.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
    }
}
