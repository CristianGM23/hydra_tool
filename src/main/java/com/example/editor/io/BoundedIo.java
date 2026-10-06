package com.example.editor.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Lecturas y copias acotadas para impedir que una entrada grande o corrupta
 * obligue a materializar memoria sin un presupuesto explícito.
 */
public final class BoundedIo {
    /** Límite conservador para documentos internos que aún no tienen un presupuesto de dominio específico. */
    public static final long DEFAULT_INTERNAL_LIMIT_BYTES = Long.getLong("hydra.io.default-max-bytes", 268_435_456L);
    private static final int BUFFER_SIZE = 16 * 1024;

    /** Descripción: Inicializa una instancia de BoundedIo con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private BoundedIo() { }

    /** Descripción: Obtiene la información correspondiente a «read» dentro de BoundedIo. Entradas: path: ruta o ubicación utilizada por la operación; maxBytes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static byte[] read(Path path, long maxBytes) throws IOException {
        Objects.requireNonNull(path, "path");
        requirePositive(maxBytes);
        long knownSize = Files.size(path);
        if(knownSize > maxBytes) throw limit(path.toString(), maxBytes);
        try(InputStream input = Files.newInputStream(path)) { return read(input, maxBytes, path.toString()); }
    }

    /** Descripción: Obtiene la información correspondiente a «read» dentro de BoundedIo. Entradas: input: valor de entrada que debe procesarse; maxBytes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static byte[] read(InputStream input, long maxBytes) throws IOException {
        return read(input, maxBytes, "stream");
    }

    /** Descripción: Obtiene la información correspondiente a «readInternal» dentro de BoundedIo. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static byte[] readInternal(Path path) throws IOException {
        return read(path, DEFAULT_INTERNAL_LIMIT_BYTES);
    }

    /** Descripción: Lee texto UTF-8 con un presupuesto máximo explícito para evitar materializar ficheros arbitrariamente grandes. Entradas: path: ruta del fichero; maxBytes: máximo de bytes admitidos. Salida: devuelve el contenido UTF-8 completo cuando cabe en el presupuesto. */ public static String readUtf8(Path path, long maxBytes) throws IOException {
        return new String(read(path, maxBytes), StandardCharsets.UTF_8);
    }

    /** Descripción: Lee texto UTF-8 interno aplicando el presupuesto conservador común de HYDRA. Entradas: path: ruta del fichero. Salida: devuelve el contenido UTF-8 completo cuando cabe en el presupuesto interno. */ public static String readUtf8Internal(Path path) throws IOException {
        return readUtf8(path, DEFAULT_INTERNAL_LIMIT_BYTES);
    }

    /** Descripción: Ejecuta la operación «copy» correspondiente a la responsabilidad de BoundedIo. Entradas: input: valor de entrada que debe procesarse; output: dato de entrada necesario para la operación; maxBytes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static long copy(InputStream input, OutputStream output, long maxBytes) throws IOException {
        Objects.requireNonNull(input, "input"); Objects.requireNonNull(output, "output"); requirePositive(maxBytes);
        byte[] buffer = new byte[BUFFER_SIZE]; long total = 0; int read;
        while((read = input.read(buffer)) >= 0) {
            if(read == 0) continue;
            try { total = Math.addExact(total, read); }
            catch(ArithmeticException exception) { throw new IOException("Input size overflow while copying stream", exception); }
            if(total > maxBytes) throw limit("stream", maxBytes);
            output.write(buffer, 0, read);
        }
        return total;
    }

    /** Descripción: Obtiene la información correspondiente a «read» dentro de BoundedIo. Entradas: input: valor de entrada que debe procesarse; maxBytes: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static byte[] read(InputStream input, long maxBytes, String source) throws IOException {
        Objects.requireNonNull(input, "input"); requirePositive(maxBytes);
        int initial = (int)Math.min(maxBytes, 64 * 1024L);
        try(ByteArrayOutputStream output = new ByteArrayOutputStream(initial)) {
            copy(input, output, maxBytes);
            return output.toByteArray();
        } catch(ArithmeticException exception) {
            throw new IOException("Input size overflow while reading " + source, exception);
        }
    }

    /** Descripción: Valida las condiciones necesarias para «requirePositive» y rechaza entradas no válidas. Entradas: maxBytes: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void requirePositive(long maxBytes) {
        if(maxBytes <= 0) throw new IllegalArgumentException("maxBytes must be greater than zero");
    }

    /** Descripción: Ejecuta la operación «limit» correspondiente a la responsabilidad de BoundedIo. Entradas: source: dato de entrada necesario para la operación; maxBytes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static IOException limit(String source, long maxBytes) {
        return new IOException("Input exceeds configured limit of " + maxBytes + " bytes: " + source);
    }
}
