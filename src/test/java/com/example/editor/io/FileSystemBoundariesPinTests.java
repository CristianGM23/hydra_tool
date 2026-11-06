package com.example.editor.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TOCTOU de SEC-WIN-JUNCTION: comprobar la ruta real de un fichero y después operar con él por nombre deja una ventana en la que otro proceso
 * puede sustituir uno de sus directorios por una unión (por HTTP, el borrado de un proyecto con el jar 20 borró ficheros de fuera en 2 de 5
 * repeticiones). Fijar el fichero cierra esa ventana porque NTFS no deja renombrar un directorio que contiene un fichero abierto.
 */
class FileSystemBoundariesPinTests {
    @TempDir Path temporary;
    private Path file;

    @BeforeEach void setUp() throws IOException {
        Assumptions.assumeTrue(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows"), "the pin relies on NTFS rename rules");
        file = temporary.resolve("d").resolve("a").resolve("b").resolve("f.txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "inside");
    }

    @Test void noDirectoryAboveAPinnedFileCanBeRenamedSoNoneCanBeReplacedByAJunction() throws IOException {
        List<Path> directories = List.of(file.getParent(), file.getParent().getParent(), temporary.resolve("d"));
        try(Closeable pin = FileSystemBoundaries.pin(file)) {
            for(Path directory : directories) {
                assertThatThrownBy(() -> Files.move(directory, directory.resolveSibling(directory.getFileName() + "-moved")))
                        .as("renaming %s while the file is pinned", temporary.relativize(directory)).isInstanceOf(IOException.class);
            }
        }
        Files.move(temporary.resolve("d"), temporary.resolve("d-moved"));
        assertThat(temporary.resolve("d-moved")).as("once the pin is closed the directory can be renamed again").isDirectory();
    }

    @Test void aPinnedFileCanStillBeDeletedOrMovedByTheOperationItGuards() throws IOException {
        Path moved = file.resolveSibling("g.txt");
        try(Closeable pin = FileSystemBoundaries.pin(file)) {
            Files.move(file, moved);
        }
        assertThat(moved).hasContent("inside");
        try(Closeable pin = FileSystemBoundaries.pin(moved)) {
            Files.delete(moved);
        }
        assertThat(Files.exists(moved, LinkOption.NOFOLLOW_LINKS)).isFalse();
    }
}
