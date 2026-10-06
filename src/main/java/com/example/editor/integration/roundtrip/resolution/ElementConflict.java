package com.example.editor.integration.roundtrip.resolution;

import java.util.Objects;
import java.util.Set;

/** Conflict associated with one canonical element and one generated artifact. */
public record ElementConflict(
        String elementId,
        String artifactPath,
        String hydraHash,
        String nativeHash,
        boolean containsManualRegion,
        Set<ConflictResolutionChoice> supportedChoices) {

    public ElementConflict {
        elementId = requireText(elementId, "elementId");
        artifactPath = safeRelativePath(artifactPath);
        hydraHash = requireText(hydraHash, "hydraHash");
        nativeHash = requireText(nativeHash, "nativeHash");
        supportedChoices = Set.copyOf(Objects.requireNonNull(supportedChoices, "supportedChoices"));
        if(supportedChoices.isEmpty()){
            throw new IllegalArgumentException("supportedChoices must not be empty");
        }
    }

    /** Descripción: Ejecuta la operación «key» correspondiente a la responsabilidad de ElementConflict. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String key(){
        return elementId + "@" + artifactPath;
    }

    /** Descripción: Valida las condiciones necesarias para «requireText» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String requireText(String value, String field){
        Objects.requireNonNull(value, field);
        if(value.isBlank()){
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    /** Descripción: Ejecuta la operación «safeRelativePath» correspondiente a la responsabilidad de ElementConflict. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String safeRelativePath(String value){
        String path = requireText(value, "artifactPath").replace('\\', '/');
        if(path.startsWith("/") || path.contains("../") || path.equals("..")){
            throw new IllegalArgumentException("artifactPath must be a safe relative path");
        }
        return path;
    }
}
