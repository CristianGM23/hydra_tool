package com.example.editor.security.process;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Evidencia FUERTE real, sin mocks, de {@link HydraExternalProcessRunner} lanzando procesos
 * del sistema operativo de verdad: cubre directamente las etapas "b" (lanzar aislado), "d3"
 * (el programa existe / se declara no disponible sin simular), "c" (entregar a la capacidad
 * con el resultado acotado) y "x" (rechazar o declarar no disponible, sin fingir resultados)
 * de security_filter_sandbox. Ningún stdout/stderr/exitCode se inventa: todo proviene de un
 * proceso real (el propio ejecutable "java" de la JVM en curso) o de un fallo real de arranque.
 */
class HydraExternalProcessRunnerRealExecutionIntegrationTests {

    @TempDir
    Path workingDirectory;

    private final Executor blockingIoExecutor = Executors.newCachedThreadPool();

    private HydraExternalProcessRunRequest request(List<String> command) {
        return new HydraExternalProcessRunRequest(workingDirectory, command, 20, java.util.Map.of(),
                65536, 10, 500L, 1000L, 3, 50L);
    }

    @Test
    void realAllowedExecutableRunsIsolatedAndReturnsABoundedCompletedResult() {
        String realJavaExecutable = ProcessHandle.current().info().command()
                .orElseThrow(() -> new IllegalStateException("Could not resolve the current JVM's java executable"));
        HydraExternalProcessSecurityProperties properties = new HydraExternalProcessSecurityProperties();
        HydraExternalProcessRunner runner = new HydraExternalProcessRunner(blockingIoExecutor, new HydraExternalProcessPolicy(properties));

        HydraExternalProcessRunResult result = runner.run(request(List.of(realJavaExecutable, "-version")));

        // "b": el proceso se lanzó de verdad, con entorno mínimo y cierre garantizado (sin excepción, sin colgarse).
        // "c": el resultado entregado está acotado (exit code real, stdout/stderr reales, nunca null).
        assertThat(result.status()).isEqualTo(HydraExternalProcessRunStatus.COMPLETED);
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.durationMillis()).isGreaterThanOrEqualTo(0L);
        // La salida de "java -version" va a stderr en la mayoría de distribuciones; comprobamos
        // que al menos uno de los dos flujos reales contiene la palabra "version", sin asumir cuál.
        assertThat((result.stdout() + result.stderr()).toLowerCase(java.util.Locale.ROOT)).contains("version");
    }

    @Test
    void commandNotOnTheAllowListIsRejectedWithoutEverStartingAProcess() {
        HydraExternalProcessSecurityProperties properties = new HydraExternalProcessSecurityProperties();
        HydraExternalProcessRunner runner = new HydraExternalProcessRunner(blockingIoExecutor, new HydraExternalProcessPolicy(properties));

        // "x": rechazado por política antes de intentar lanzar nada; ningún resultado fingido.
        HydraExternalProcessRunResult result = runner.run(request(List.of("definitely-not-an-allowed-executable", "--help")));

        assertThat(result.status()).isEqualTo(HydraExternalProcessRunStatus.REJECTED);
        assertThat(result.exitCode()).isNull();
        assertThat(result.diagnostics()).anySatisfy(line -> assertThat(line).contains("not allowed"));
    }

    @Test
    void allowedButMissingExecutableIsHonestlyDeclaredUnavailableRatherThanSimulated() {
        HydraExternalProcessSecurityProperties properties = new HydraExternalProcessSecurityProperties();
        // Permitimos explícitamente un nombre de ejecutable que no existe en ningún PATH real,
        // para ejercitar la rama real de "el programa no existe" (IOException de ProcessBuilder),
        // no un valor fabricado: el runner intenta de verdad arrancarlo y falla de verdad.
        properties.setAllowedExecutables(Set.of("hydra-definitely-missing-binary-9f3c2a"));
        HydraExternalProcessRunner runner = new HydraExternalProcessRunner(blockingIoExecutor, new HydraExternalProcessPolicy(properties));

        // "d3"/"x": el programa no existe; se declara TOOL_UNAVAILABLE, nunca un éxito simulado.
        HydraExternalProcessRunResult result = runner.run(request(List.of("hydra-definitely-missing-binary-9f3c2a", "--help")));

        assertThat(result.status()).isEqualTo(HydraExternalProcessRunStatus.TOOL_UNAVAILABLE);
        assertThat(result.exitCode()).isNull();
        assertThat(result.diagnostics()).contains("Executable is unavailable");
    }
}
