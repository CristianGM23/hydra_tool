package com.example.editor.project.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Regresión de la incidencia de datos: las pruebas de importación y reinicio no pueden operar sobre el {@code data/} real. */
class ExternalImportStorageGuardTests {
    @TempDir Path root;

    private static Path realData() { return Path.of("data").toAbsolutePath().normalize(); }

    /** Huella de solo lectura de {@code data/}: ruta, tamaño y fecha de cada entrada. Vacía si el directorio no existe. */
    private static String snapshot(Path data) throws IOException {
        if (!Files.isDirectory(data)) return "";
        try (Stream<Path> walk = Files.walk(data)) {
            return walk.sorted().map(path -> {
                try { return data.relativize(path) + "|" + (Files.isDirectory(path) ? "d" : Files.size(path)) + "|" + Files.getLastModifiedTime(path).toMillis(); }
                catch (IOException failure) { throw new IllegalStateException(failure); }
            }).collect(Collectors.joining("\n"));
        }
    }

    @Test
    @DisplayName("Fase 0: un almacén dentro del data/ real, fuera de la raíz o con «..» se rechaza; los temporales se aceptan")
    void rejectsRealAndEscapingStorageAndAcceptsTemporaryOnes() throws Exception {
        assertThat(Files.exists(Path.of("pom.xml"))).as("la prueba se ejecuta desde la raíz del proyecto").isTrue();
        assertThatThrownBy(() -> ExternalImportStorageGuard.requireInside(root, List.of(realData().resolve("metamodels"))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("fuera de la raíz");
        assertThatThrownBy(() -> ExternalImportStorageGuard.requireInside(root, List.of(root.resolve("metamodels").resolve("..").resolve("..").resolve("otro"))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ExternalImportStorageGuard.requireInside(root, List.of(root)))
                .as("la propia raíz no es un almacén").isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ExternalImportStorageGuard.requireInside(realData(), List.of(realData().resolve("metamodels"))))
                .as("una raíz que es el data/ real se rechaza aunque los almacenes estén dentro")
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("almacenamiento real");
        ExternalImportStorageGuard.requireInside(root, List.of(root.resolve("metamodels"), root.resolve("assets")));
    }

    @Test
    @DisplayName("Fase 0: el proceso hijo se detiene (código 3) antes de arrancar Spring si un almacén apunta al data/ real, sin modificarlo")
    void theChildProcessRefusesRealStorageAndLeavesItUntouched() throws Exception {
        String before = snapshot(realData());
        Path cwd = Files.createDirectories(root.resolve("child-cwd"));
        int rejected = probe(cwd, realData().resolve("metamodels").toString());
        int accepted = probe(cwd, root.resolve("metamodels").toString());
        assertThat(rejected).as("almacén real: debe rechazarse").isEqualTo(ExternalImportStorageGuard.REJECTED_EXIT_CODE);
        assertThat(accepted).as("almacén temporal: debe aceptarse").isZero();
        assertThat(snapshot(realData())).as("el data/ real no debe cambiar").isEqualTo(before);
    }

    private int probe(Path cwd, String storage) throws Exception {
        List<String> command = List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Dspring.devtools.restart.enabled=false",
                "-cp", System.getProperty("java.class.path"), ExternalImportCrashChild.class.getName(), "guard-probe", root.toString(), storage, "-", "-");
        Process process = new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        if (!process.waitFor(2, TimeUnit.MINUTES)) { process.destroyForcibly(); throw new IllegalStateException("el hijo no terminó"); }
        return process.exitValue();
    }
}
