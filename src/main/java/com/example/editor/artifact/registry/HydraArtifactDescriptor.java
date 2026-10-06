package com.example.editor.artifact.registry;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Versioned neutral artifact descriptor with traceable dependencies and provenance. */
public record HydraArtifactDescriptor(String id, String name, HydraArtifactKind kind, String version,
        String producer, String relativePath, String sha256, HydraArtifactStatus status,
        Instant modifiedAt, List<HydraArtifactDependency> dependencies, Map<String, String> metadata) {
    public HydraArtifactDescriptor {
        id = required(id, "id");
        name = Objects.requireNonNullElse(name, id).trim();
        kind = kind == null ? HydraArtifactKind.OTHER : kind;
        version = Objects.requireNonNullElse(version, "1.0").trim();
        producer = Objects.requireNonNullElse(producer, "HYDRA").trim();
        relativePath = Objects.requireNonNullElse(relativePath, "").replace('\\', '/').trim();
        sha256 = Objects.requireNonNullElse(sha256, "").trim().toLowerCase();
        status = status == null ? HydraArtifactStatus.AVAILABLE : status;
        modifiedAt = modifiedAt == null ? Instant.EPOCH : modifiedAt;
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
    /** Descripción: Valida las condiciones necesarias para «required» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String required(String value, String field){
        String result = Objects.requireNonNullElse(value, "").trim();
        if(result.isBlank()){throw new IllegalArgumentException(field + " must not be blank");}
        return result;
    }
}
