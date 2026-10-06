package com.example.editor.integration.injection;

/** Directorio relativo autorizado sobre el que se previsualiza o aplica una integración. */
public record TechnologyInjectionRequest(String targetDirectory) {
    public TechnologyInjectionRequest {
        targetDirectory = targetDirectory == null ? "" : targetDirectory.trim().replace('\\', '/');
        if(targetDirectory.isBlank()){throw new IllegalArgumentException("targetDirectory is required");}
        if(targetDirectory.startsWith("/") || targetDirectory.contains("../") || targetDirectory.equals("..")){
            throw new IllegalArgumentException("Unsafe target directory: " + targetDirectory);
        }
    }
}
