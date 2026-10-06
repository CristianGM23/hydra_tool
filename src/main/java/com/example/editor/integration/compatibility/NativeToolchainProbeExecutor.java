package com.example.editor.integration.compatibility;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import com.example.editor.integration.verification.TechnologyValidationStepResult;
import com.example.editor.integration.verification.TechnologyValidationStepStatus;

/** Executes one versioned toolchain probe and preserves bounded, auditable evidence. */
@Service
public class NativeToolchainProbeExecutor {
    private final ToolchainCommandRunner runner;
    /** Descripción: Inicializa una instancia de NativeToolchainProbeExecutor con sus dependencias y datos de entrada. Entradas: runner: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public NativeToolchainProbeExecutor(ToolchainCommandRunner runner){this.runner = Objects.requireNonNull(runner, "runner");}

    /** Descripción: Procesa la operación «execute» coordinando las responsabilidades de NativeToolchainProbeExecutor. Entradas: definition: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public NativeToolchainProbeResult execute(NativeToolchainProbeDefinition definition){
        Objects.requireNonNull(definition, "definition");
        if(definition.commands().isEmpty()){
            return new NativeToolchainProbeResult(definition.technology(), definition.version(), CompatibilityStatus.NOT_RUN,
                    "No native commands were configured", List.of(), 0L);
        }
        List<String> evidence = new ArrayList<>();
        boolean requiredFailure = false; boolean optionalFailure = false; long duration = 0L;
        for(NativeToolchainProbeCommand command : definition.commands()){
            TechnologyValidationStepResult result = runner.run(command.id(), command.description(), definition.workingDirectory(),
                    command.command(), command.expectedExitCode(), command.timeoutSeconds(), definition.environment());
            duration += Math.max(0L, result.durationMillis());
            evidence.add(command.id() + "=" + result.status());
            result.diagnostics().stream().filter(value -> value != null && !value.isBlank()).limit(3)
                    .forEach(value -> evidence.add(command.id() + ": " + value.trim()));
            firstLine(result.stdout()).ifPresent(value -> evidence.add(command.id() + " stdout: " + value));
            firstLine(result.stderr()).ifPresent(value -> evidence.add(command.id() + " stderr: " + value));
            if(result.status() != TechnologyValidationStepStatus.PASSED){
                if(command.required()){requiredFailure = true;}else{optionalFailure = true;}
            }
        }
        CompatibilityStatus status = requiredFailure ? CompatibilityStatus.FAILED
                : optionalFailure ? CompatibilityStatus.PARTIAL : CompatibilityStatus.PASSED;
        String message = switch(status){
            case PASSED -> "All configured native commands passed";
            case PARTIAL -> "Required commands passed, but optional native evidence is incomplete";
            case FAILED -> "At least one required native command failed or was unavailable";
            case NOT_RUN -> "No native commands were configured";
        };
        return new NativeToolchainProbeResult(definition.technology(), definition.version(), status, message, evidence, duration);
    }

    /** Descripción: Ejecuta la operación «firstLine» correspondiente a la responsabilidad de NativeToolchainProbeExecutor. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private java.util.Optional<String> firstLine(String value){
        if(value == null || value.isBlank()){return java.util.Optional.empty();}
        String line = value.lines().findFirst().orElse("").trim();
        if(line.isBlank()){return java.util.Optional.empty();}
        return java.util.Optional.of(line.length() > 240 ? line.substring(0, 240) : line);
    }
}
