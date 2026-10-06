package com.example.editor.integration.compatibility;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.example.editor.integration.verification.ExternalToolProcessRunner;
import com.example.editor.integration.verification.TechnologyValidationStepResult;

/** Production bridge to HYDRA's bounded external-process runner. */
@Component
public class ExternalToolchainCommandRunner implements ToolchainCommandRunner {
    private final ExternalToolProcessRunner processRunner;
    /** Descripción: Inicializa una instancia de ExternalToolchainCommandRunner con sus dependencias y datos de entrada. Entradas: processRunner: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ExternalToolchainCommandRunner(ExternalToolProcessRunner processRunner){this.processRunner = processRunner;}
    /** Descripción: Procesa la operación «run» coordinando las responsabilidades de ExternalToolchainCommandRunner. Entradas: id: identificador que delimita el recurso objetivo; description: dato de entrada necesario para la operación; workingDirectory: dato de entrada necesario para la operación; command: dato de entrada necesario para la operación; expectedExitCode: dato de entrada necesario para la operación; timeoutSeconds: dato de entrada necesario para la operación; environment: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public TechnologyValidationStepResult run(String id, String description, Path workingDirectory,
            List<String> command, int expectedExitCode, int timeoutSeconds, Map<String, String> environment){
        return processRunner.run(id, description, workingDirectory, command, expectedExitCode, timeoutSeconds, environment);
    }
}
