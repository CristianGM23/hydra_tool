package com.example.editor.integration.compiler;

import java.util.List;
import java.util.Map;
import com.example.editor.integration.IntegrationProfileModel;
import com.example.editor.integration.projection.CanonicalArtifactContent;

/** Entrada neutral consumida por todos los adaptadores tecnológicos. */
public record TechnologyCompilationRequest(IntegrationProfileModel profile,
        List<CanonicalArtifactContent> artifacts, String generatorVersion, Map<String, String> options) {
    public TechnologyCompilationRequest {
        if(profile == null){throw new IllegalArgumentException("profile is required");}
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        generatorVersion = generatorVersion == null || generatorVersion.isBlank() ? "HYDRA" : generatorVersion.trim();
        options = options == null ? Map.of() : Map.copyOf(options);
    }

    /** Descripción: Valida las condiciones necesarias para «require» y rechaza entradas no válidas. Entradas: kind: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public CanonicalArtifactContent require(com.example.editor.integration.IntegrationArtifactKind kind){
        return artifacts.stream().filter(value -> value.kind() == kind).findFirst()
                .orElseThrow(() -> new TechnologyCompilationException("Missing canonical artifact: " + kind));
    }

    /** Descripción: Obtiene la información correspondiente a «find» dentro de TechnologyCompilationRequest. Entradas: kind: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public java.util.Optional<CanonicalArtifactContent> find(com.example.editor.integration.IntegrationArtifactKind kind){
        return artifacts.stream().filter(value -> value.kind() == kind).findFirst();
    }
}
