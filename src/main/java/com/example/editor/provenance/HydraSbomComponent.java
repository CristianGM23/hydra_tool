package com.example.editor.provenance;

import java.util.List;
import java.util.Map;

/** One verifiable component in a HYDRA distribution inventory. */
public record HydraSbomComponent(String componentId, String type, String name,
        String version, String sha256, String license, String origin,
        List<String> relationships, Map<String, String> attributes) {
    public HydraSbomComponent {
        componentId = text(componentId); type = text(type); name = text(name);
        version = text(version); sha256 = text(sha256); license = text(license); origin = text(origin);
        relationships = relationships == null ? List.of() : List.copyOf(relationships);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de HydraSbomComponent. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value == null ? "" : value.trim();}
}
