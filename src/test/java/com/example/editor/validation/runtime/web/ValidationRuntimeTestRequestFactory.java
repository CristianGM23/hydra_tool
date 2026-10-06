package com.example.editor.validation.runtime.web;

import java.util.List;
import java.util.Map;

import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.runtime.ValidationChangeSet;
import com.example.editor.validation.runtime.ValidationExecutionRequest;
import com.example.editor.validation.runtime.ValidationRunMode;

final class ValidationRuntimeTestRequestFactory {
    private ValidationRuntimeTestRequestFactory() { }

    static ValidationExecutionRequest request() {
        CanonicalModelDocument model = new CanonicalModelDocument("1.0", "people-model", "people", "", "People",
                List.of(), List.of(), List.of(), Map.of());
        return new ValidationExecutionRequest(ValidationRunMode.FULL, model, List.of(), "", ValidationChangeSet.empty(), Map.of());
    }
}
