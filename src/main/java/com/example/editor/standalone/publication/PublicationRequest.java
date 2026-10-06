package com.example.editor.standalone.publication;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Configurable request for publishing an already validated standalone distribution. */
public record PublicationRequest(String productId, String version, PublicationChannel channel,
        PublicationSigningMode signingMode, String distributionDirectory, String updateBaseUrl,
        List<PublicationArtifactRequest> artifacts, Map<String, String> metadata) {
    public PublicationRequest {
        productId = required(productId, "productId");
        version = required(version, "version");
        channel = channel == null ? PublicationChannel.INTERNAL : channel;
        signingMode = signingMode == null ? PublicationSigningMode.CHECKSUM_ONLY : signingMode;
        distributionDirectory = required(distributionDirectory, "distributionDirectory").replace('\\', '/');
        updateBaseUrl = Objects.requireNonNullElse(updateBaseUrl, "").trim();
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        if(artifacts.isEmpty()){throw new IllegalArgumentException("At least one publication artifact is required");}
    }
    /** Descripción: Valida las condiciones necesarias para «required» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String required(String value, String field){
        String result = Objects.requireNonNullElse(value, "").trim();
        if(result.isBlank()){throw new IllegalArgumentException(field + " must not be blank");}
        return result;
    }
}
