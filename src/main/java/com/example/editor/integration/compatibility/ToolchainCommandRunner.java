package com.example.editor.integration.compatibility;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import com.example.editor.integration.verification.TechnologyValidationStepResult;

/** Neutral command runner contract, allowing deterministic tests without real native tools. */
@FunctionalInterface
public interface ToolchainCommandRunner {
    /** Descripción: Procesa la operación «run» coordinando las responsabilidades de ToolchainCommandRunner. Entradas: id: identificador que delimita el recurso objetivo; description: dato de entrada necesario para la operación; workingDirectory: dato de entrada necesario para la operación; command: dato de entrada necesario para la operación; expectedExitCode: dato de entrada necesario para la operación; timeoutSeconds: dato de entrada necesario para la operación; environment: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ TechnologyValidationStepResult run(String id, String description, Path workingDirectory,
            List<String> command, int expectedExitCode, int timeoutSeconds, Map<String, String> environment);
}
