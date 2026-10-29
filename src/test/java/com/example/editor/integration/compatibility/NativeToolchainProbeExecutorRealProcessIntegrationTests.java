package com.example.editor.integration.compatibility;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.integration.verification.ExternalToolCircuitBreaker;
import com.example.editor.integration.verification.ExternalToolProcessRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Evidencia FUERTE real, sin mocks, de que sondear una herramienta nativa ejecuta de verdad un
 * proceso del sistema operativo: cubre directamente la etapa "a1" (Sondear herramientas: "Se
 * ejecuta un comando de version de una lista cerrada") de toolchains_check_install. Reutiliza el
 * mismo HydraExternalProcessRunner ya probado en el lote de seguridad, pero a traves de toda la
 * cadena real de produccion especifica de toolchains: NativeToolchainProbeExecutor ->
 * ExternalToolchainCommandRunner -> ExternalToolProcessRunner -> HydraExternalProcessRunner.
 */
class NativeToolchainProbeExecutorRealProcessIntegrationTests {

    @TempDir
    Path workingDirectory;

    private NativeToolchainProbeExecutor executor() {
        ExternalToolCircuitBreaker breaker = new ExternalToolCircuitBreaker();
        ExternalToolProcessRunner processRunner = new ExternalToolProcessRunner(breaker);
        ExternalToolchainCommandRunner commandRunner = new ExternalToolchainCommandRunner(processRunner);
        return new NativeToolchainProbeExecutor(commandRunner);
    }

    private String realJavaExecutable() {
        return ProcessHandle.current().info().command()
                .orElseThrow(() -> new IllegalStateException("Could not resolve the current JVM's java executable"));
    }

    @Test
    void probingARealAllowedExecutableWithARealVersionCommandPasses() {
        NativeToolchainProbeCommand versionCommand = new NativeToolchainProbeCommand("version",
                "Check the real java version", List.of(realJavaExecutable(), "-version"), 0, 15, true);
        NativeToolchainProbeDefinition definition = new NativeToolchainProbeDefinition("java", "any",
                workingDirectory, List.of(versionCommand), Map.of());

        NativeToolchainProbeResult result = executor().execute(definition);

        // Oraculo independiente: el estado y el mensaje vienen de haber lanzado de verdad el
        // proceso real y haber comparado su codigo de salida real (0) con el esperado.
        assertThat(result.status()).isEqualTo(CompatibilityStatus.PASSED);
        assertThat(result.message()).isEqualTo("All configured native commands passed");
        assertThat(result.evidence()).anyMatch(line -> line.equals("version=PASSED"));
        // La salida real de "java -version" (stdout o stderr segun la distribucion) queda como
        // evidencia real capturada, no inventada.
        assertThat(result.evidence().stream().anyMatch(line -> line.toLowerCase(java.util.Locale.ROOT).contains("version")))
                .isTrue();
    }

    @Test
    void aRequiredCommandThatRealExitsWithAnUnexpectedCodeIsHonestlyReportedAsFailed() {
        // Un flag invalido real para la propia JVM en curso produce de verdad un codigo de salida
        // distinto de 0; no se simula el fallo, se provoca uno real y se comprueba que HYDRA no
        // finge un resultado correcto.
        NativeToolchainProbeCommand brokenCommand = new NativeToolchainProbeCommand("broken",
                "Deliberately invalid real flag", List.of(realJavaExecutable(), "--this-flag-does-not-exist"),
                0, 15, true);
        NativeToolchainProbeDefinition definition = new NativeToolchainProbeDefinition("java", "any",
                workingDirectory, List.of(brokenCommand), Map.of());

        NativeToolchainProbeResult result = executor().execute(definition);

        assertThat(result.status()).isEqualTo(CompatibilityStatus.FAILED);
        assertThat(result.message()).isEqualTo("At least one required native command failed or was unavailable");
    }
}
