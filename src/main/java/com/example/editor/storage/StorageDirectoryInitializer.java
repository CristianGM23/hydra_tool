package com.example.editor.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.example.editor.config.StorageProperties;

/**
 * Inicializa los directorios utilizados por la aplicación.
 *
 * Al arrancar Spring Boot, esta clase comprueba los directorios configurados
 * en application.properties. Si no existen y la creación automática está
 * activada, los crea.
 *
 * También comprueba que ninguna ruta configurada sea un archivo normal,
 * porque la aplicación necesita trabajar con directorios.
 */
@Component
@Order(0)
public class StorageDirectoryInitializer implements ApplicationRunner {

    private final StorageProperties properties;

    /**
     * Crea el inicializador utilizando la configuración de almacenamiento.
     *
     * Entrada:
     * @param properties contiene las rutas configuradas en application.properties.
     *
     * Salida:
     * Se crea una instancia preparada para inicializar los directorios.
     */
    /** Descripción: Inicializa una instancia de StorageDirectoryInitializer con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public StorageDirectoryInitializer(StorageProperties properties) {
        this.properties = properties;
    }

    /**
     * Se ejecuta automáticamente cuando Spring Boot termina de arrancar.
     *
     * Entrada:
     * @param arguments contiene los argumentos utilizados para iniciar la aplicación.
     *
     * Salida:
     * No devuelve ningún valor. Inicializa los directorios configurados.
     */
    @Override
    /** Descripción: Procesa la operación «run» coordinando las responsabilidades de StorageDirectoryInitializer. Entradas: arguments: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void run(ApplicationArguments arguments) {
        initialize();
    }

    /**
     * Comprueba la configuración y crea los directorios necesarios.
     *
     * Entrada:
     * No recibe parámetros. Utiliza las propiedades almacenadas en la clase.
     *
     * Salida:
     * No devuelve ningún valor. Puede crear los directorios configurados.
     */
    /** Descripción: Crea o prepara el resultado de «initialize» dentro de StorageDirectoryInitializer. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void initialize() {
        if (!properties.createDirectories()) {
            return;
        }
        for (Path directory : properties.allDirectories()) {
            createDirectory(directory);
        }
    }

    /**
     * Comprueba y crea un directorio concreto.
     *
     * Entrada:
     * @param directory ruta del directorio que se debe comprobar o crear.
     *
     * Salida:
     * No devuelve ningún valor.
     *
     * @throws IllegalStateException si la ruta es un archivo o no puede crearse.
     */
    /** Descripción: Crea o prepara el resultado de «createDirectory» dentro de StorageDirectoryInitializer. Entradas: directory: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void createDirectory(Path directory) {
        try {
            // Una ruta existente solo es válida cuando representa un directorio.
            if (Files.exists(directory)) {
                if (!Files.isDirectory(directory)) {
                    throw new IllegalStateException("Configured path exists but is not a directory: " + directory.toAbsolutePath());
                }
            } else {
                Files.createDirectories(directory);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create directory: " + directory.toAbsolutePath(),exception);
        }
    }
}