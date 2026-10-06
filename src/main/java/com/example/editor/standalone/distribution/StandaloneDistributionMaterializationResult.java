package com.example.editor.standalone.distribution;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Filesystem result of materializing one deterministic distribution plan. */
public record StandaloneDistributionMaterializationResult(Path root, List<String> writtenPaths,
        Map<String, String> sha256ByPath) {
    public StandaloneDistributionMaterializationResult {
        if(root == null){throw new IllegalArgumentException("root is required");}
        root = root.toAbsolutePath().normalize();
        writtenPaths = writtenPaths == null ? List.of() : List.copyOf(writtenPaths);
        sha256ByPath = sha256ByPath == null ? Map.of() : Map.copyOf(sha256ByPath);
    }
}
