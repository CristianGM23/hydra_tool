package com.example.editor.integration.compatibility;

import java.util.List;

/** Selects safe, catalogued native probes. Arbitrary commands are never accepted. */
public record NativeToolchainCompatibilityRunRequest(List<String> selectedKeys) {
    public NativeToolchainCompatibilityRunRequest {
        selectedKeys = selectedKeys == null ? List.of() : List.copyOf(selectedKeys);
    }
}
