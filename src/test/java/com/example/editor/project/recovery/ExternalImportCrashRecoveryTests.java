package com.example.editor.project.recovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regresión de #36 con <b>reinicios reales</b>: cada paso corre en una JVM nueva ({@link ExternalImportCrashChild}) y las
 * interrupciones son {@code Runtime.halt}, sin hooks ni cierre ordenado. El proceso de la prueba no usa Spring ni comparte
 * memoria con los hijos: solo observa el disco, de modo que la recuperación no puede depender del estado de la JVM anterior.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExternalImportCrashRecoveryTests {
    @TempDir static Path root;
    @TempDir static Path workspaces;
    @TempDir static Path archives;

    private record Outcome(int exitCode, String output) { }

    private Outcome child(String crash, String mode, String archive, String projectName) throws Exception {
        Path parent = Files.createDirectories(workspaces.resolve("ws"));
        List<String> command = new java.util.ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xmx768m", "-Dspring.devtools.restart.enabled=false", "-Dhydra.crash=" + crash, "-cp", System.getProperty("java.class.path"), ExternalImportCrashChild.class.getName(),
                mode, root.toString(), parent.toString(), archive, projectName));
        Path log = Files.createTempFile(archives, "child-", ".log");
        // El directorio de trabajo del hijo es temporal: ninguna ruta relativa (p. ej. ./data) puede resolverse dentro del repositorio.
        Process process = new ProcessBuilder(command).directory(Files.createDirectories(root.resolve("child-cwd")).toFile())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!process.waitFor(6, TimeUnit.MINUTES)) { process.destroyForcibly(); throw new IllegalStateException("child timed out: " + ImportFixtureArchives.text(log)); }
        return new Outcome(process.exitValue(), ImportFixtureArchives.text(log));
    }

    private String archive(String name, List<String> files) throws IOException {
        Path path = archives.resolve(name);
        Files.write(path, ImportFixtureArchives.zip(files));
        return path.toString();
    }

    private static void assertCrashed(Outcome outcome) { assertThat(outcome.exitCode()).as(outcome.output()).isEqualTo(137); }

    private Map<String, String> state() throws IOException { return ImportFixtureArchives.canonicalState(root); }

    private void assertRecoveredTo(Map<String, String> expected, String step) throws Exception {
        Outcome recovery = child("", "recover", "-", "-");
        assertThat(recovery.exitCode()).as(recovery.output()).isZero();
        Map<String, String> after = state();
        assertThat(after).as(step + ": " + ImportFixtureArchives.describe(expected, after) + "\n" + recovery.output()).isEqualTo(expected);
        StringBuilder leftover = new StringBuilder();
        for (String journal : ImportFixtureArchives.journals(root)) {
            String text = ImportFixtureArchives.text(root.resolve("external-import-journal").resolve(journal));
            leftover.append(journal).append(": ").append(text, 0, Math.min(2500, text.length())).append('\n');
        }
        assertThat(ImportFixtureArchives.journals(root)).as(step + ": no quedan diarios\n" + leftover).isEmpty();
        assertThat(recovery.output()).contains("PENDING=[]");
    }

    @Test @Order(1)
    @DisplayName("#36 reinicio real: un proceso terminado tras materializar se recupera al arrancar uno nuevo")
    void aProcessKilledAfterMaterializationIsRecoveredByTheNextOne() throws Exception {
        Outcome baseline = child("", "recover", "-", "-");
        assertThat(baseline.exitCode()).as(baseline.output()).isZero();
        Map<String, String> before = state();
        String full = archive("full.zip", ImportFixtureArchives.FULL);

        assertCrashed(child("AFTER_MATERIALIZATION", "import", full, "Killed Import"));

        Map<String, String> crashed = state();
        assertThat(crashed).as("la interrupción debe dejar residuo canónico y un diario").isNotEqualTo(before);
        assertThat(ImportFixtureArchives.journals(root)).hasSize(1);
        assertRecoveredTo(before, "primer reinicio");
        try (var children = Files.list(workspaces.resolve("ws"))) { assertThat(children.toList()).as("el proyecto a medias se retira").isEmpty(); }
        // Un reinicio más sobre un estado ya recuperado no cambia nada.
        assertRecoveredTo(before, "reinicio repetido");
    }

    @Test @Order(2)
    @DisplayName("#36 reinicio real: interrupción entre registrar la intención y ejecutar el efecto")
    void aKillBetweenRecordingAndExecutingIsReconciled() throws Exception {
        Map<String, String> before = state();
        assertCrashed(child("BEFORE_METAMODEL_CREATED", "import", archive("full.zip", ImportFixtureArchives.FULL), "Intent Only"));

        assertThat(ImportFixtureArchives.journals(root)).hasSize(1);
        assertThat(state()).as("el efecto no llegó a ejecutarse").isEqualTo(before);
        assertRecoveredTo(before, "intención sin efecto");
    }

    @Test @Order(3)
    @DisplayName("#36 reinicio real: efecto aplicado cuyo resultado no llegó a registrarse")
    void aKillAfterTheEffectButBeforeRecordingItIsReconciled() throws Exception {
        Map<String, String> before = state();
        assertCrashed(child("BEFORE_APPLIED_RECORDED", "import", archive("full.zip", ImportFixtureArchives.FULL), "Unrecorded Effect"));

        assertThat(ImportFixtureArchives.journals(root)).hasSize(1);
        assertThat(ImportFixtureArchives.text(root.resolve("external-import-journal").resolve(ImportFixtureArchives.journals(root).get(0))))
                .as("el diario aún dice que el efecto está solo previsto").contains("INTENDED");
        assertThat(state()).as("pero el metamodelo ya existe").isNotEqualTo(before);
        assertRecoveredTo(before, "efecto sin registrar");
    }

    @Test @Order(4)
    @DisplayName("#36 reinicio real: interrupción durante la propia compensación y segundo reinicio con pendientes")
    void aKillDuringCompensationLeavesPendingWorkThatTheSecondRestartCompletes() throws Exception {
        Map<String, String> before = state();
        assertCrashed(child("AFTER_MATERIALIZATION", "import", archive("full.zip", ImportFixtureArchives.FULL), "Double Crash"));
        assertThat(ImportFixtureArchives.journals(root)).hasSize(1);

        // Primer reinicio: la recuperación empieza y el proceso muere al borrar el primer editor.
        assertCrashed(child("DURING_COMPENSATION", "recover", "-", "-"));
        assertThat(ImportFixtureArchives.journals(root)).as("la compensación a medias conserva el diario").hasSize(1);
        assertThat(state()).as("la compensación quedó a medias").isNotEqualTo(before);

        // Segundo reinicio: termina lo pendiente sin duplicar ni perder nada.
        assertRecoveredTo(before, "segundo reinicio");
    }

    @Test @Order(5)
    @DisplayName("#36 reinicio real: se conservan los documentos preexistentes y se restauran las capas sustituidas")
    void preexistingDocumentsSurviveAndReplacedLayersAreRestoredAfterACrash() throws Exception {
        Outcome first = child("", "import", archive("metamodel.zip", ImportFixtureArchives.METAMODEL_ONLY), "Existing Metamodel");
        assertThat(first.exitCode()).as(first.output()).isZero();
        assertThat(first.output()).contains("IMPORT_OK");
        Map<String, String> preexisting = state();
        assertThat(preexisting.keySet()).anyMatch(path -> path.contains("hydratest.metamodel.json"));

        assertCrashed(child("AFTER_MATERIALIZATION", "import", archive("full.zip", ImportFixtureArchives.FULL), "Crash On Reused"));
        assertThat(state()).as("la importación añadió capas sobre el metamodelo existente").isNotEqualTo(preexisting);

        assertRecoveredTo(preexisting, "metamodelo reutilizado");
        assertThat(state().keySet()).as("el metamodelo preexistente no se borra").anyMatch(path -> path.contains("hydratest.metamodel.json"));
    }
}
