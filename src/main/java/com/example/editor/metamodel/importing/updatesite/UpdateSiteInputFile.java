package com.example.editor.metamodel.importing.updatesite;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;

/**
 * Archivo de entrada de un update site.
 *
 * <p>Puede estar respaldado por memoria (entradas pequeñas/remotas) o por un
 * fichero temporal de staging. El contrato es deliberadamente reutilizable:
 * {@link #openStream()} puede invocarse varias veces hasta que el propietario
 * de la petición llame a {@link #cleanup()}.</p>
 */
public final class UpdateSiteInputFile {
    private final String path;
    private final byte[] inMemoryContent;
    private final Path stagedPath;
    private final long size;
    private final boolean deleteOnCleanup;

    /** Crea una entrada in-memory compatible con el contrato histórico. */
    /** Descripción: Inicializa una instancia de UpdateSiteInputFile con sus dependencias y datos de entrada. Entradas: path: ruta o ubicación utilizada por la operación; content: valor de entrada que debe procesarse. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteInputFile(String path, byte[] content) {
        this(path, content == null ? new byte[0] : content.clone(), null,
                content == null ? 0L : content.length, false);
    }

    /** Descripción: Inicializa una instancia de UpdateSiteInputFile con sus dependencias y datos de entrada. Entradas: path: ruta o ubicación utilizada por la operación; content: valor de entrada que debe procesarse; stagedPath: ruta o ubicación utilizada por la operación; size: dato de entrada necesario para la operación; deleteOnCleanup: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private UpdateSiteInputFile(String path, byte[] content, Path stagedPath, long size, boolean deleteOnCleanup) {
        this.path = normalizePath(path);
        this.inMemoryContent = content;
        this.stagedPath = stagedPath == null ? null : stagedPath.toAbsolutePath().normalize();
        this.size = Math.max(0L, size);
        this.deleteOnCleanup = deleteOnCleanup;
    }

    /** Crea una entrada respaldada por un fichero temporal ya escrito en disco. */
    /** Descripción: Ejecuta la operación «staged» correspondiente a la responsabilidad de UpdateSiteInputFile. Entradas: path: ruta o ubicación utilizada por la operación; stagedPath: ruta o ubicación utilizada por la operación; size: dato de entrada necesario para la operación; deleteOnCleanup: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static UpdateSiteInputFile staged(String path, Path stagedPath, long size, boolean deleteOnCleanup) {
        if(stagedPath == null) {
            throw new IllegalArgumentException("stagedPath must not be null");
        }
        return new UpdateSiteInputFile(path, null, stagedPath, size, deleteOnCleanup);
    }

    /** Descripción: Ejecuta la operación «path» correspondiente a la responsabilidad de UpdateSiteInputFile. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String path() {
        return path;
    }

    /** Tamaño conocido sin materializar el contenido en heap. */
    /** Descripción: Ejecuta la operación «size» correspondiente a la responsabilidad de UpdateSiteInputFile. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public long size() {
        return size;
    }

    /**
     * Abre un stream nuevo en cada llamada. El llamador es responsable de
     * cerrarlo. Tras {@link #cleanup()}, una entrada staged deja de ser legible.
     */
    /** Descripción: Crea o prepara el resultado de «openStream» dentro de UpdateSiteInputFile. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public InputStream openStream() {
        if(inMemoryContent != null) {
            return new ByteArrayInputStream(inMemoryContent);
        }
        try {
            return Files.newInputStream(stagedPath, StandardOpenOption.READ);
        } catch(IOException exception) {
            throw new MetamodelDesignException(Reason.STORAGE_ERROR,
                    "Could not open staged update-site input: " + path, exception);
        }
    }

    /**
     * Compatibilidad con consumidores existentes que todavía necesitan un
     * byte[]. Las entradas staged solo se materializan cuando este método se
     * invoca explícitamente.
     */
    /** Descripción: Ejecuta la operación «content» correspondiente a la responsabilidad de UpdateSiteInputFile. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public byte[] content() {
        if(inMemoryContent != null) {
            return inMemoryContent.clone();
        }
        try(InputStream input = openStream()) {
            return com.example.editor.io.BoundedIo.read(input, Math.max(1L, size));
        } catch(IOException exception) {
            throw new MetamodelDesignException(Reason.STORAGE_ERROR,
                    "Could not read staged update-site input: " + path, exception);
        }
    }

    /** Elimina únicamente el fichero temporal que pertenece a esta entrada. */
    /** Descripción: Ejecuta la operación «cleanup» correspondiente a la responsabilidad de UpdateSiteInputFile. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void cleanup() {
        if(!deleteOnCleanup || stagedPath == null) {
            return;
        }
        try {
            Files.deleteIfExists(stagedPath);
        } catch(IOException ignored) {
            // Best effort: a cleanup failure must not mask the operation result.
        }
    }

    /** Descripción: Ejecuta la operación «normalizePath» correspondiente a la responsabilidad de UpdateSiteInputFile. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalizePath(String value) {
        return value == null ? "" : value.replace('\\', '/').trim();
    }
}
