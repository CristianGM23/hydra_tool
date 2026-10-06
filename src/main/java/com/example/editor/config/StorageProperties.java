package com.example.editor.config;

import java.nio.file.Path;
import java.util.List;

import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "dsl.storage")
@Validated
public record StorageProperties(

        @NotNull
        Path metamodels,

        @NotNull
        Path models,

        @NotNull
        Path notations,

        @NotNull
        Path validations,

        @NotNull
        Path quickFixes,

        @NotNull
        Path generators,

        @NotNull
        Path assets,

        @NotNull
        Path exports,

        boolean createDirectories
) {

    /** Descripción: Ejecuta la operación «allDirectories» correspondiente a la responsabilidad de StorageProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<Path> allDirectories() {
        return List.of(metamodels,models,notations,validations,quickFixes,generators,assets,exports);
    }
}