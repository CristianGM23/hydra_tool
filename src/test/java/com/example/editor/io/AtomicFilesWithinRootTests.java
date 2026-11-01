package com.example.editor.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * T-SEC-JUNCTION-AMPLIAR: TOCTOU de escritura. Quien escribe comprueba la cadena del destino y después escribe por nombre, así que un
 * directorio cambiado por una unión entre medias desvía la escritura fuera de la raíz y sustituye lo que haya allí. Las variantes «within»
 * de AtomicFiles abren el temporal antes de comprobar su ruta real y lo publican sin cerrarlo: con él abierto ningún antecesor se puede
 * renombrar. Un antecesor ya cambiado cuando se abre el temporal es lo que deja la carrera cuando la gana el atacante, y es lo que
 * reproducen aquí las uniones creadas de antemano.
 */
class AtomicFilesWithinRootTests {
    private static final String MARKER = "outside data";
    @TempDir Path temporary;
    private Path root;
    private Path outside;

    @BeforeEach void setUp() throws IOException {
        Assumptions.assumeTrue(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows"), "directory junctions and the pin rely on NTFS");
        root = temporary.resolve("root"); Files.createDirectories(root);
        outside = temporary.resolve("outside"); Files.createDirectories(outside);
        Files.writeString(outside.resolve("f.json"), MARKER);
    }

    @Test void aTemporaryOpenForWritingPinsItsDirectoriesAndCanStillBePublishedOverTheTarget() throws IOException {
        Path directory = root.resolve("d"); Files.createDirectories(directory);
        Path target = directory.resolve("f.json"); Files.writeString(target, "old");
        Path pending = directory.resolve(".f.json-pending.tmp");
        try(FileChannel channel = FileChannel.open(pending, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            channel.write(ByteBuffer.wrap("new".getBytes(StandardCharsets.UTF_8)));
            assertThatThrownBy(() -> Files.move(directory, directory.resolveSibling("d-moved")))
                    .as("the directory of an open temporary file cannot be renamed, so it cannot be replaced by a junction").isInstanceOf(IOException.class);
            Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        assertThat(target).as("the temporary file is published while it is still open").hasContent("new");
    }

    @Test void aWriteWhoseDirectoryIsAJunctionToTheOutsideIsRefusedAndWritesNothingThere() throws Exception {
        junction(root.resolve("datos"), outside);

        assertThatThrownBy(() -> AtomicFiles.writeReplacingWithin(root.resolve("datos").resolve("f.json"), "from HYDRA".getBytes(StandardCharsets.UTF_8), root))
                .isInstanceOf(FileSystemBoundaries.EscapeException.class)
                .hasMessageNotContaining(temporary.toString());
        assertThat(outside.resolve("f.json")).as("the file outside the root was replaced through the junction").hasContent(MARKER);
        assertThat(entries(outside)).as("the refused temporary file must not stay outside the root").containsExactly("f.json");
    }

    @Test void aCopyWhoseDirectoryIsAJunctionToTheOutsideIsRefusedAndWritesNothingThere() throws Exception {
        Path source = temporary.resolve("source.json"); Files.writeString(source, "from HYDRA");
        junction(root.resolve("datos"), outside);

        assertThatThrownBy(() -> AtomicFiles.copyReplacingBoundedWithin(source, root.resolve("datos").resolve("f.json"), root, 1024))
                .isInstanceOf(FileSystemBoundaries.EscapeException.class);
        assertThat(outside.resolve("f.json")).as("the file outside the root was replaced through the junction").hasContent(MARKER);
        assertThat(entries(outside)).as("the refused temporary file must not stay outside the root").containsExactly("f.json");
    }

    @Test void aJunctionThatStaysInsideTheRootIsNotAnEscape() throws Exception {
        Files.createDirectories(root.resolve("data"));
        junction(root.resolve("alias"), root.resolve("data"));

        AtomicFiles.writeReplacingWithin(root.resolve("alias").resolve("f.json"), "inside".getBytes(StandardCharsets.UTF_8), root);

        assertThat(root.resolve("data").resolve("f.json")).hasContent("inside");
    }

    @Test void aWriteAndACopyInsideTheRootReplaceTheTargetAndLeaveNoTemporaryFile() throws IOException {
        Path directory = root.resolve("a").resolve("b");
        Path target = directory.resolve("f.json");
        AtomicFiles.writeReplacingWithin(target, "first".getBytes(StandardCharsets.UTF_8), root);
        Path source = temporary.resolve("source.json"); Files.writeString(source, "second");

        long copied = AtomicFiles.copyReplacingBoundedWithin(source, target, root, 1024);

        assertThat(copied).isEqualTo(6);
        assertThat(target).hasContent("second");
        assertThat(entries(directory)).containsExactly("f.json");
        assertThatThrownBy(() -> AtomicFiles.copyReplacingBoundedWithin(source, target, root, 3))
                .as("the size budget still applies").isInstanceOf(IOException.class).isNotInstanceOf(FileSystemBoundaries.EscapeException.class);
        assertThat(target).as("a copy over the budget leaves the previous content").hasContent("second");
        assertThat(entries(directory)).containsExactly("f.json");
    }

    /** A root that does not exist yet has no real path: checked before creating it, its written form (here the 8.3 short name of %TEMP%) did
     *  not match the real path of the temporary file and every first write into a new workspace repository was refused. */
    @Test void aRootThatDoesNotExistYetIsCreatedAndIsNotAnEscape() throws IOException {
        Path fresh = temporary.resolve("fresh-root");
        Path target = fresh.resolve("a").resolve("f.json");

        AtomicFiles.writeReplacingWithin(target, "first".getBytes(StandardCharsets.UTF_8), fresh);

        assertThat(target).hasContent("first");
    }

    @Test void aProjectWriteThroughAJunctionIsRefusedAsAnInvalidRequest() throws Exception {
        junction(root.resolve("linked"), outside);

        assertThatThrownBy(() -> new com.example.editor.project.storage.ProjectFileOperations().writeAtomicWithin(root, root.resolve("linked").resolve("f.json"), "{}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(com.example.editor.project.ProjectException.class,
                        refused -> assertThat(refused.getReason()).isEqualTo(com.example.editor.project.ProjectException.Reason.INVALID_REQUEST));
        assertThat(outside.resolve("f.json")).as("a project write replaced a file outside the project through a junction").hasContent(MARKER);
    }

    private static java.util.List<String> entries(Path directory) throws IOException {
        try(Stream<Path> files = Files.list(directory)) { return files.map(path -> path.getFileName().toString()).sorted().toList(); }
    }

    /** mklink /J needs no privilege, unlike a symbolic link: any local user or tool can create one. */
    private static void junction(Path link, Path target) throws Exception {
        Process process = new ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), target.toString()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
    }
}
