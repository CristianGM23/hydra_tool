package com.example.editor.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.config.StorageProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba la creación y validación de los directorios de almacenamiento.
 *
 * Cada test utiliza una carpeta temporal para no modificar los directorios
 * reales de la aplicación.
 */
class StorageDirectoryInitializerTests {

    @TempDir
    Path temporaryDirectory;

    /**
     * Descripción:
     * Comprueba que se crean todos los directorios configurados.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando todas las rutas son directorios.
     */
    @Test
    void createsAllConfiguredDirectories() {
        StorageProperties properties = createProperties(true);
        StorageDirectoryInitializer initializer = new StorageDirectoryInitializer(properties);

        initializer.initialize();

        for (Path directory : properties.allDirectories()) {
            assertThat(directory).isDirectory();
        }
    }

    /**
     * Descripción:
     * Comprueba que ejecutar dos veces el inicializador no produce errores.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando la segunda ejecución es válida.
     */
    @Test
    void initializationIsIdempotent() {
        StorageProperties properties = createProperties(true);
        StorageDirectoryInitializer initializer = new StorageDirectoryInitializer(properties);

        initializer.initialize();

        assertThatCode(initializer::initialize).doesNotThrowAnyException();

        for (Path directory : properties.allDirectories()) {
            assertThat(directory).isDirectory();
        }
    }

    /**
     * Descripción:
     * Comprueba que los directorios no se crean cuando la opción está desactivada.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando ninguna ruta existe.
     */
    @Test
    void doesNotCreateDirectoriesWhenDisabled() {
        StorageProperties properties = createProperties(false);
        StorageDirectoryInitializer initializer = new StorageDirectoryInitializer(properties);

        initializer.initialize();

        for (Path directory : properties.allDirectories()) {
            assertThat(directory).doesNotExist();
        }
    }

    /**
     * Descripción:
     * Comprueba que una ruta existente como archivo es rechazada.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando se lanza IllegalStateException.
     *
     * @throws IOException cuando el archivo temporal no puede crearse.
     */
    @Test
    void rejectsAPathThatIsAnExistingFile() throws IOException {
        Path invalidPath = temporaryDirectory.resolve("metamodels");
        Files.writeString(invalidPath, "Temporary test file");

        StorageProperties properties = new StorageProperties(
            invalidPath,
            temporaryDirectory.resolve("models"),
            temporaryDirectory.resolve("notations"),
            temporaryDirectory.resolve("validations"),
            temporaryDirectory.resolve("quickfixes"),
            temporaryDirectory.resolve("generators"),
            temporaryDirectory.resolve("assets"),
            temporaryDirectory.resolve("exports"),
            true
        );

        StorageDirectoryInitializer initializer = new StorageDirectoryInitializer(properties);

        assertThatThrownBy(initializer::initialize)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Configured path exists but is not a directory");
    }

    /**
     * Descripción:
     * Crea una configuración de almacenamiento utilizada por los tests.
     *
     * Entrada:
     * @param enabled indica si se deben crear automáticamente los directorios.
     *
     * Salida:
     * @return una configuración cuyos directorios están dentro de la carpeta temporal.
     */
    private StorageProperties createProperties(boolean enabled) {
        return new StorageProperties(
            temporaryDirectory.resolve("metamodels"),
            temporaryDirectory.resolve("models"),
            temporaryDirectory.resolve("notations"),
            temporaryDirectory.resolve("validations"),
            temporaryDirectory.resolve("quickfixes"),
            temporaryDirectory.resolve("generators"),
            temporaryDirectory.resolve("assets"),
            temporaryDirectory.resolve("exports"),
            enabled
        );
    }
}