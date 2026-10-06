package com.example.editor.integration;
import java.util.List;
/** Comprobación reproducible que debe ejecutarse después de generar o inyectar un paquete. */
public record IntegrationVerificationStep(String id, String description, String workingDirectory,
        List<String> command, int expectedExitCode, boolean required) {
    public IntegrationVerificationStep {
        if(id == null || id.isBlank()) throw new IllegalArgumentException("verification step id is required");
        id = id.trim(); description = normalize(description); workingDirectory = normalize(workingDirectory);
        command = command == null ? List.of() : command.stream().map(IntegrationVerificationStep::normalize).filter(value -> !value.isBlank()).toList();
        if(command.isEmpty()) throw new IllegalArgumentException("verification command is required");
    }
    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de IntegrationVerificationStep. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalize(String value) { return value == null ? "" : value.trim(); }
}
