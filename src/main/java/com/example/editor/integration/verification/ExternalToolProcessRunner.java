package com.example.editor.integration.verification;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import com.example.editor.concurrent.BoundedPermitPool;
import com.example.editor.security.process.HydraExternalProcessPolicy;
import com.example.editor.security.process.HydraExternalProcessRunRequest;
import com.example.editor.security.process.HydraExternalProcessRunResult;
import com.example.editor.security.process.HydraExternalProcessRunStatus;
import com.example.editor.security.process.HydraExternalProcessRunner;
import com.example.editor.security.process.HydraExternalProcessSecurityProperties;
import com.example.editor.security.process.HydraProcessTreeTerminator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Ejecuta una herramienta nativa con timeout, salida acotada y cancelación forzada. */
@Component
public class ExternalToolProcessRunner {
    private final ExternalToolCircuitBreaker circuitBreaker;
    private final TechnologyProjectValidationProperties properties;
    private final BoundedPermitPool processSlots;
    private final HydraExternalProcessRunner processRunner;

    @Autowired
    /** Inicializa el runner de validación reutilizando el lanzador neutral de procesos externos. */
    /** Descripción: Inicializa una instancia de ExternalToolProcessRunner con sus dependencias y datos de entrada. Entradas: circuitBreaker: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento; blockingIoExecutor: dato de entrada necesario para la operación; securityPolicy: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ExternalToolProcessRunner(ExternalToolCircuitBreaker circuitBreaker,
            TechnologyProjectValidationProperties properties,
            @Qualifier("hydraBlockingIoExecutor") Executor blockingIoExecutor,
            HydraExternalProcessPolicy securityPolicy) {
        this(circuitBreaker, properties, blockingIoExecutor, securityPolicy, new HydraProcessTreeTerminator());
    }

    /** Constructor focalizado que permite sustituir el terminador en pruebas. */
    /** Descripción: Inicializa una instancia de ExternalToolProcessRunner con sus dependencias y datos de entrada. Entradas: circuitBreaker: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento; blockingIoExecutor: dato de entrada necesario para la operación; securityPolicy: dato de entrada necesario para la operación; processTreeTerminator: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ ExternalToolProcessRunner(ExternalToolCircuitBreaker circuitBreaker,
            TechnologyProjectValidationProperties properties, Executor blockingIoExecutor,
            HydraExternalProcessPolicy securityPolicy, HydraProcessTreeTerminator processTreeTerminator) {
        this.circuitBreaker = circuitBreaker;
        this.properties = properties;
        this.processRunner = new HydraExternalProcessRunner(blockingIoExecutor, securityPolicy, processTreeTerminator);
        this.processSlots = new BoundedPermitPool(properties.getMaximumConcurrentProcesses());
    }

    /** Compatibility constructor used by focused unit tests and embedders. */
    /** Descripción: Inicializa una instancia de ExternalToolProcessRunner con sus dependencias y datos de entrada. Entradas: circuitBreaker: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ExternalToolProcessRunner(ExternalToolCircuitBreaker circuitBreaker) {
        this(circuitBreaker, new TechnologyProjectValidationProperties(), ForkJoinPool.commonPool(),
                new HydraExternalProcessPolicy(new HydraExternalProcessSecurityProperties()));
    }

    /** Ejecuta una herramienta tecnológica y adapta el resultado neutral al contrato de validación existente. */
    /** Descripción: Procesa la operación «run» coordinando las responsabilidades de ExternalToolProcessRunner. Entradas: id: identificador que delimita el recurso objetivo; description: dato de entrada necesario para la operación; workingDirectory: dato de entrada necesario para la operación; command: dato de entrada necesario para la operación; expectedExitCode: dato de entrada necesario para la operación; timeoutSeconds: dato de entrada necesario para la operación; environment: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TechnologyValidationStepResult run(String id, String description, Path workingDirectory,
            List<String> command, int expectedExitCode, int timeoutSeconds, Map<String, String> environment) {
        if (command == null || command.isEmpty()) {
            return result(id, description, TechnologyValidationStepStatus.FAILED, List.of(), null,
                    0L, "", "", List.of("Command is empty"));
        }
        String executable = command.get(0);
        if (!circuitBreaker.allow(executable)) {
            return result(id, description, TechnologyValidationStepStatus.SKIPPED, command, null,
                    0L, "", "", List.of("Circuit breaker is open for " + executable));
        }

        BoundedPermitPool.Permit permit = null;
        try {
            permit = processSlots.tryAcquire(properties.getProcessAcquireTimeoutSeconds(), TimeUnit.SECONDS);
            if (permit == null) {
                return result(id, description, TechnologyValidationStepStatus.SKIPPED, command, null,
                        0L, "", "", List.of("Native validation capacity is busy; retry later"));
            }
            HydraExternalProcessRunResult execution = processRunner.run(new HydraExternalProcessRunRequest(
                    workingDirectory, command, timeoutSeconds, environment,
                    properties.getMaximumProcessOutputBytes(), properties.getProcessOutputJoinTimeoutSeconds(),
                    properties.getProcessTerminationGraceMillis(),
                    properties.getProcessTerminationForceWaitMillis(),
                    properties.getProcessTerminationMaximumPasses(),
                    properties.getProcessTreeObservationIntervalMillis()));
            return adapt(id, description, command, expectedExitCode, executable, execution);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return result(id, description, TechnologyValidationStepStatus.CANCELLED, command, null,
                    0L, "", exception.getMessage(), List.of("Validation was interrupted while waiting for capacity"));
        } finally {
            if (permit != null) permit.close();
        }
    }

    /** Descripción: Ejecuta la operación «adapt» correspondiente a la responsabilidad de ExternalToolProcessRunner. Entradas: id: identificador que delimita el recurso objetivo; description: dato de entrada necesario para la operación; command: dato de entrada necesario para la operación; expectedExitCode: dato de entrada necesario para la operación; executable: dato de entrada necesario para la operación; execution: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TechnologyValidationStepResult adapt(String id, String description, List<String> command,
            int expectedExitCode, String executable, HydraExternalProcessRunResult execution) {
        TechnologyValidationStepStatus status;
        List<String> diagnostics = new ArrayList<>(execution.diagnostics());
        switch (execution.status()) {
            case COMPLETED -> {
                status = execution.exitCode() != null && execution.exitCode() == expectedExitCode
                        ? TechnologyValidationStepStatus.PASSED : TechnologyValidationStepStatus.FAILED;
                if (status == TechnologyValidationStepStatus.PASSED) circuitBreaker.success(executable);
                else circuitBreaker.failure(executable);
            }
            case TIMED_OUT -> {
                circuitBreaker.failure(executable);
                status = TechnologyValidationStepStatus.TIMED_OUT;
            }
            case CANCELLED -> status = TechnologyValidationStepStatus.CANCELLED;
            case TOOL_UNAVAILABLE -> {
                circuitBreaker.failure(executable);
                status = TechnologyValidationStepStatus.TOOL_UNAVAILABLE;
                if (diagnostics.isEmpty()) diagnostics.add("Executable is unavailable: " + executable);
            }
            case REJECTED -> {
                circuitBreaker.failure(executable);
                boolean unavailable = diagnostics.stream().anyMatch(message ->
                        message != null && message.startsWith("External executable is not allowed:"));
                status = unavailable ? TechnologyValidationStepStatus.TOOL_UNAVAILABLE
                        : TechnologyValidationStepStatus.FAILED;
            }
            case FAILED -> {
                circuitBreaker.failure(executable);
                status = TechnologyValidationStepStatus.FAILED;
            }
            default -> throw new IllegalStateException("Unsupported process status: " + execution.status());
        }
        return result(id, description, status, command, execution.exitCode(), execution.durationMillis(),
                execution.stdout(), execution.stderr(), diagnostics);
    }

    /** Número de slots de proceso disponibles para validaciones nativas. */
    /** Descripción: Ejecuta la operación «availableProcessSlots» correspondiente a la responsabilidad de ExternalToolProcessRunner. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int availableProcessSlots() { return processSlots.availablePermits(); }

    /** Instantánea de capacidad de los slots de procesos nativos. */
    /** Descripción: Ejecuta la operación «capacityStatus» correspondiente a la responsabilidad de ExternalToolProcessRunner. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public BoundedPermitPool.Snapshot capacityStatus() { return processSlots.snapshot(); }

    /** Descripción: Ejecuta la operación «result» correspondiente a la responsabilidad de ExternalToolProcessRunner. Entradas: id: identificador que delimita el recurso objetivo; description: dato de entrada necesario para la operación; status: dato de entrada necesario para la operación; command: dato de entrada necesario para la operación; exitCode: dato de entrada necesario para la operación; duration: dato de entrada necesario para la operación; stdout: dato de entrada necesario para la operación; stderr: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TechnologyValidationStepResult result(String id, String description,
            TechnologyValidationStepStatus status, List<String> command, Integer exitCode,
            long duration, String stdout, String stderr, List<String> diagnostics) {
        return new TechnologyValidationStepResult(id, description, status, command, exitCode,
                duration, stdout, stderr, diagnostics);
    }
}
