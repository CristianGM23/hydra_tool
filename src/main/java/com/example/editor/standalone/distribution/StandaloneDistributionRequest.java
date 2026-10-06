package com.example.editor.standalone.distribution;

/** Selects a generated editor below the configured standalone distribution root. */
public record StandaloneDistributionRequest(String editorDirectory, StandaloneDistributionProfile profile) {
    public StandaloneDistributionRequest {
        if(editorDirectory == null || editorDirectory.isBlank()){throw new IllegalArgumentException("editorDirectory is required");}
        editorDirectory = editorDirectory.trim().replace('\\', '/');
        if(profile == null){throw new IllegalArgumentException("profile is required");}
    }
}
