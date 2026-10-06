package com.example.editor.security.process;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import com.example.editor.audit.AuditRequestContext;

/**
 * Único lanzador compartido para procesos externos de propósito general.
 * Aplica política de seguridad, entorno mínimo, salida acotada, timeout y cierre del árbol de procesos.
 */
public final class HydraExternalProcessRunner {
    private final Executor blockingIoExecutor;
    private final HydraExternalProcessPolicy securityPolicy;
    private final HydraProcessTreeTerminator processTreeTerminator;

    /** Crea el lanzador compartido con la política y el ejecutor de E/S recibidos. */
    /** Descripción: Inicializa una instancia de HydraExternalProcessRunner con sus dependencias y datos de entrada. Entradas: blockingIoExecutor: dato de entrada necesario para la operación; securityPolicy: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public HydraExternalProcessRunner(Executor blockingIoExecutor, HydraExternalProcessPolicy securityPolicy) {
        this(blockingIoExecutor, securityPolicy, new HydraProcessTreeTerminator());
    }

    /** Constructor inyectable para pruebas focalizadas del cierre de árboles. */
    /** Descripción: Inicializa una instancia de HydraExternalProcessRunner con sus dependencias y datos de entrada. Entradas: blockingIoExecutor: dato de entrada necesario para la operación; securityPolicy: dato de entrada necesario para la operación; processTreeTerminator: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public HydraExternalProcessRunner(Executor blockingIoExecutor, HydraExternalProcessPolicy securityPolicy,
            HydraProcessTreeTerminator processTreeTerminator) {
        if (blockingIoExecutor == null) throw new IllegalArgumentException("blockingIoExecutor is required");
        if (securityPolicy == null) throw new IllegalArgumentException("securityPolicy is required");
        if (processTreeTerminator == null) throw new IllegalArgumentException("processTreeTerminator is required");
        this.blockingIoExecutor = blockingIoExecutor;
        this.securityPolicy = securityPolicy;
        this.processTreeTerminator = processTreeTerminator;
    }

    /** Ejecuta una orden sin shell y devuelve únicamente evidencia acotada del proceso. */
    /** Descripción: Procesa la operación «run» coordinando las responsabilidades de HydraExternalProcessRunner. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public HydraExternalProcessRunResult run(HydraExternalProcessRunRequest request) {
        if (request == null) throw new IllegalArgumentException("request is required");
        Instant started = Instant.now();
        Process process = null;
        boolean globalPermit = false;
        HydraProcessTreeTerminator.ProcessTreeTracker tracker = null;
        try {
            securityPolicy.validateCommand(request.command());
            securityPolicy.validateWorkingDirectory(request.workingDirectory());
            securityPolicy.validateRuntimeBudget(request);
            globalPermit = securityPolicy.acquireGlobalProcessPermit(Math.min(request.timeoutSeconds(), 30));
            if(!globalPermit) throw new IllegalArgumentException("Global external-process capacity is busy; retry later");

            ProcessBuilder builder = new ProcessBuilder(new ArrayList<>(request.command()));
            builder.directory(request.workingDirectory().toFile());
            builder.redirectErrorStream(false);
            securityPolicy.configureMinimalEnvironment(builder, request.environment());
            process = builder.start();
            tracker = processTreeTerminator.track(process,
                    Duration.ofMillis(request.treeObservationIntervalMillis()));

            Process runningProcess = process;
            CompletableFuture<byte[]> stdout = CompletableFuture.supplyAsync(
                    () -> readBounded(runningProcess.getInputStream(), request.maximumOutputBytes()),
                    blockingIoExecutor);
            CompletableFuture<byte[]> stderr = CompletableFuture.supplyAsync(
                    () -> readBounded(runningProcess.getErrorStream(), request.maximumOutputBytes()),
                    blockingIoExecutor);

            boolean finished = process.waitFor(request.timeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                HydraProcessTreeTerminator.TerminationReport termination = terminate(tracker, process, request);
                tracker = null;
                List<String> diagnostics = new ArrayList<>();
                diagnostics.add("Process exceeded " + request.timeoutSeconds() + " seconds");
                if (!termination.terminated()) diagnostics.add("Process tree termination left "
                        + termination.survivingProcesses() + " surviving process(es)");
                return result(HydraExternalProcessRunStatus.TIMED_OUT, null, started,
                        text(stdout, request.outputJoinTimeoutSeconds()),
                        text(stderr, request.outputJoinTimeoutSeconds()), diagnostics);
            }

            long lingering = tracker == null ? 0L : tracker.aliveObservedProcesses();
            HydraProcessTreeTerminator.TerminationReport completion = terminate(tracker, process, request);
            tracker = null;
            int exitCode = process.exitValue();
            byte[] stdoutBytes = bytes(stdout, request.outputJoinTimeoutSeconds());
            byte[] stderrBytes = bytes(stderr, request.outputJoinTimeoutSeconds());
            List<String> diagnostics = new ArrayList<>();
            if (lingering > 0) diagnostics.add("Process exited but left " + lingering
                    + " descendant process(es); HYDRA terminated the tracked tree");
            if (!completion.terminated()) diagnostics.add("Process completion left "
                    + completion.survivingProcesses() + " surviving process(es)");
            if (stdoutBytes.length >= request.maximumOutputBytes()
                    || stderrBytes.length >= request.maximumOutputBytes())
                diagnostics.add("Process output reached the configured capture limit and may be truncated");
            return result(HydraExternalProcessRunStatus.COMPLETED, exitCode, started,
                    new String(stdoutBytes, StandardCharsets.UTF_8),
                    new String(stderrBytes, StandardCharsets.UTF_8), diagnostics);
        } catch (IllegalArgumentException exception) {
            return result(HydraExternalProcessRunStatus.REJECTED, null, started, "", "",
                    List.of(safeMessage(exception)));
        } catch (IOException exception) {
            return result(HydraExternalProcessRunStatus.TOOL_UNAVAILABLE, null, started, "",
                    safeMessage(exception), List.of("Executable is unavailable"));
        } catch (InterruptedException exception) {
            HydraProcessTreeTerminator.TerminationReport termination = terminate(tracker, process, request);
            tracker = null;
            Thread.currentThread().interrupt();
            List<String> diagnostics = new ArrayList<>();
            diagnostics.add("External process execution was interrupted");
            if (!termination.terminated()) diagnostics.add("Process tree termination left "
                    + termination.survivingProcesses() + " surviving process(es)");
            return result(HydraExternalProcessRunStatus.CANCELLED, null, started, "",
                    safeMessage(exception), diagnostics);
        } catch (RuntimeException exception) {
            return result(HydraExternalProcessRunStatus.FAILED, null, started, "", "",
                    List.of("External process execution failed: " + safeMessage(exception)));
        } finally {
            if(globalPermit) securityPolicy.releaseGlobalProcessPermit();
            if (tracker != null) terminate(tracker, process, request);
            else if (process != null && process.isAlive()) terminate(null, process, request);
        }
    }

    /** Descripción: Obtiene la información correspondiente a «readBounded» dentro de HydraExternalProcessRunner. Entradas: input: valor de entrada que debe procesarse; maximumBytes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] readBounded(InputStream input, int maximumBytes) {
        try (input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int remaining = maximumBytes;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (remaining <= 0) continue;
                int retained = Math.min(read, remaining);
                output.write(buffer, 0, retained);
                remaining -= retained;
            }
            return output.toByteArray();
        } catch (IOException exception) {
            return ("Could not read process output: " + safeMessage(exception)).getBytes(StandardCharsets.UTF_8);
        }
    }

    /** Descripción: Ejecuta la operación «bytes» correspondiente a la responsabilidad de HydraExternalProcessRunner. Entradas: future: dato de entrada necesario para la operación; timeoutSeconds: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] bytes(CompletableFuture<byte[]> future, int timeoutSeconds) throws InterruptedException {
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            return "Process output collection timed out".getBytes(StandardCharsets.UTF_8);
        } catch (ExecutionException exception) {
            return ("Could not collect process output: " + safeMessage(exception)).getBytes(StandardCharsets.UTF_8);
        }
    }

    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de HydraExternalProcessRunner. Entradas: future: dato de entrada necesario para la operación; timeoutSeconds: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String text(CompletableFuture<byte[]> future, int timeoutSeconds) throws InterruptedException {
        return new String(bytes(future, timeoutSeconds), StandardCharsets.UTF_8);
    }

    /** Descripción: Ejecuta la operación «terminate» correspondiente a la responsabilidad de HydraExternalProcessRunner. Entradas: tracker: dato de entrada necesario para la operación; process: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private HydraProcessTreeTerminator.TerminationReport terminate(
            HydraProcessTreeTerminator.ProcessTreeTracker tracker, Process process,
            HydraExternalProcessRunRequest request) {
        if (tracker != null) return processTreeTerminator.terminateTracked(tracker,
                Duration.ofMillis(request.terminationGraceMillis()),
                Duration.ofMillis(request.terminationForceWaitMillis()),
                request.terminationMaximumPasses());
        return processTreeTerminator.terminate(process,
                Duration.ofMillis(request.terminationGraceMillis()),
                Duration.ofMillis(request.terminationForceWaitMillis()),
                request.terminationMaximumPasses());
    }

    /** Descripción: Ejecuta la operación «result» correspondiente a la responsabilidad de HydraExternalProcessRunner. Entradas: status: dato de entrada necesario para la operación; exitCode: dato de entrada necesario para la operación; started: dato de entrada necesario para la operación; stdout: dato de entrada necesario para la operación; stderr: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private HydraExternalProcessRunResult result(HydraExternalProcessRunStatus status, Integer exitCode,
            Instant started, String stdout, String stderr, List<String> diagnostics) {
        List<String> correlated = new ArrayList<>(diagnostics == null ? List.of() : diagnostics);
        if(AuditRequestContext.active()) correlated.add("correlationId=" + AuditRequestContext.correlationId());
        return new HydraExternalProcessRunResult(status, exitCode,
                Math.max(0L, Duration.between(started, Instant.now()).toMillis()),
                stdout, stderr, List.copyOf(correlated));
    }

    /** Descripción: Ejecuta la operación «safeMessage» correspondiente a la responsabilidad de HydraExternalProcessRunner. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String safeMessage(Throwable exception) {
        Throwable cause = exception instanceof ExecutionException && exception.getCause() != null
                ? exception.getCause() : exception;
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }
}
