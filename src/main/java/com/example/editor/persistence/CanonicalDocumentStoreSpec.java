package com.example.editor.persistence;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Function;

import tools.jackson.databind.ObjectMapper;

/** Configuration required to create one typed canonical document store. */
public record CanonicalDocumentStoreSpec<T>(String namespace, Path localDirectory, String suffix, boolean useEtag,
        ObjectMapper objectMapper, Class<T> type, Function<T, String> idExtractor) {
    public CanonicalDocumentStoreSpec {
        namespace = requireNamespace(namespace);
        localDirectory = Objects.requireNonNull(localDirectory, "localDirectory");
        suffix = Objects.requireNonNull(suffix, "suffix");
        objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        type = Objects.requireNonNull(type, "type");
        idExtractor = Objects.requireNonNull(idExtractor, "idExtractor");
    }
    /** Descripción: Valida las condiciones necesarias para «requireNamespace» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String requireNamespace(String value) {
        if(value == null || value.isBlank() || !value.matches("[a-z0-9][a-z0-9._-]{0,95}")) {
            throw new IllegalArgumentException("Canonical persistence namespace is invalid: " + value);
        }
        return value;
    }
}
