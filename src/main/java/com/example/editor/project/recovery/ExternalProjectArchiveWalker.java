package com.example.editor.project.recovery;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.stereotype.Component;

import com.example.editor.project.LocalProjectProperties;
import com.example.editor.project.ProjectException;

/**
 * Recorre de forma segura ZIP/JAR/WAR/EAR anidados sin extraerlos al sistema de
 * ficheros. Conserva rutas virtuales con el separador {@code !/}.
 */
@Component
public class ExternalProjectArchiveWalker {
    private final LocalProjectProperties properties;

    /** Descripción: Inicializa una instancia de ExternalProjectArchiveWalker con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public ExternalProjectArchiveWalker(LocalProjectProperties properties) {
        this.properties = properties;
    }

    /** Recorre un contenedor externo y devuelve todas sus entradas legibles. */
    /** Descripción: Ejecuta la operación «walk» correspondiente a la responsabilidad de ExternalProjectArchiveWalker. Entradas: sourceName: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; compressedBytes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ List<ExternalProjectArchiveEntry> walk(String sourceName, InputStream source, long compressedBytes) {
        if(source == null) throw invalid("The external project source is empty");
        Budget budget = new Budget(properties, compressedBytes);
        List<ExternalProjectArchiveEntry> result = new ArrayList<>();
        readContainer(normalizeContainerName(sourceName), source, "", 0, budget, result);
        if(result.isEmpty()) throw invalid("The external project contains no readable files");
        return List.copyOf(result);
    }

    /** Descripción: Obtiene la información correspondiente a «readContainer» dentro de ExternalProjectArchiveWalker. Entradas: containerName: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; prefix: dato de entrada necesario para la operación; depth: dato de entrada necesario para la operación; budget: dato de entrada necesario para la operación; result: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void readContainer(String containerName, InputStream source, String prefix, int depth,
            Budget budget, List<ExternalProjectArchiveEntry> result) {
        if(depth > properties.getMaxNestedArchiveDepth()) return;
        try(ZipInputStream zip = new ZipInputStream(source)) {
            ZipEntry entry;
            while((entry = zip.getNextEntry()) != null) {
                budget.checkDeadline();
                if(entry.isDirectory()) {
                    zip.closeEntry();
                    continue;
                }
                String relative = normalizePath(entry.getName());
                String virtualPath = prefix + relative;
                byte[] bytes = readBounded(zip, properties.getMaxUploadFileSizeBytes());
                budget.add(bytes.length, entry.getCompressedSize());
                result.add(new ExternalProjectArchiveEntry(virtualPath, bytes, bytes.length, sha256(bytes)));
                zip.closeEntry();

                if(depth < properties.getMaxNestedArchiveDepth() && isArchive(relative, bytes)) {
                    readContainer(containerName + "!/" + relative, new ByteArrayInputStream(bytes),
                            virtualPath + "!/", depth + 1, budget, result);
                }
            }
        } catch(ProjectException exception) {
            throw exception;
        } catch(IOException exception) {
            throw new ProjectException(ProjectException.Reason.INVALID_REQUEST,
                    "Could not inspect external project container: " + containerName, exception);
        }
    }

    /** Descripción: Obtiene la información correspondiente a «readBounded» dentro de ExternalProjectArchiveWalker. Entradas: input: valor de entrada que debe procesarse; maxBytes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] readBounded(InputStream input, long maxBytes) throws IOException {
        long limit = Math.max(1L, maxBytes);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        long total = 0;
        int read;
        while((read = input.read(buffer)) >= 0) {
            if(read == 0) continue;
            total += read;
            if(total > limit) throw tooLarge("A project resource exceeds the configured per-file limit");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    /** Descripción: Evalúa la condición «isArchive» sin modificar el estado externo. Entradas: path: ruta o ubicación utilizada por la operación; bytes: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isArchive(String path, byte[] bytes) {
        String lower = path.toLowerCase(Locale.ROOT);
        boolean extension = lower.endsWith(".zip") || lower.endsWith(".jar")
                || lower.endsWith(".war") || lower.endsWith(".ear");
        return extension && bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K';
    }

    /** Descripción: Ejecuta la operación «normalizeContainerName» correspondiente a la responsabilidad de ExternalProjectArchiveWalker. Entradas: sourceName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String normalizeContainerName(String sourceName) {
        String value = sourceName == null || sourceName.isBlank() ? "external-project.zip" : sourceName;
        return normalizePath(value);
    }

    /** Descripción: Ejecuta la operación «normalizePath» correspondiente a la responsabilidad de ExternalProjectArchiveWalker. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String normalizePath(String value) {
        String normalized = value == null ? "" : value.replace('\\', '/').trim();
        if(normalized.isBlank() || normalized.indexOf('\0') >= 0 || normalized.startsWith("/")
                || normalized.matches("^[A-Za-z]:.*")) throw invalid("Unsafe path in external project: " + value);
        Path path = Path.of(normalized).normalize();
        if(path.isAbsolute() || path.startsWith("..")) throw invalid("Unsafe path in external project: " + value);
        String portable = path.toString().replace('\\', '/');
        if(portable.equals("..") || portable.startsWith("../")) throw invalid("Unsafe path in external project: " + value);
        return portable;
    }

    /** Descripción: Ejecuta la operación «sha256» correspondiente a la responsabilidad de ExternalProjectArchiveWalker. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch(NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    /** Descripción: Ejecuta la operación «invalid» correspondiente a la responsabilidad de ExternalProjectArchiveWalker. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ProjectException invalid(String message) {
        return new ProjectException(ProjectException.Reason.INVALID_REQUEST, message);
    }

    /** Descripción: Ejecuta la operación «tooLarge» correspondiente a la responsabilidad de ExternalProjectArchiveWalker. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ProjectException tooLarge(String message) {
        return new ProjectException(ProjectException.Reason.FILE_TOO_LARGE, message);
    }

    private static final class Budget {
        private final int maxEntries;
        private final long maxBytes;
        private final double maxRatio;
        private final long compressedBytes;
        private final long deadlineNanos;
        private int entries;
        private long bytes;

        /** Descripción: Inicializa una instancia de Budget con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; compressedBytes: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private Budget(LocalProjectProperties properties, long compressedBytes) {
            maxEntries = Math.max(1, properties.getMaxArchiveEntries());
            maxBytes = Math.max(1L, properties.getMaxArchiveSizeBytes());
            maxRatio = Math.max(1D, properties.getMaxArchiveCompressionRatio());
            this.compressedBytes = Math.max(1L, compressedBytes);
            deadlineNanos = System.nanoTime() + Duration.ofMillis(
                    Math.max(1000L, properties.getMaxArchiveReadMillis())).toNanos();
        }

        /** Descripción: Ejecuta la operación «add» correspondiente a la responsabilidad de ExternalProjectArchiveWalker. Entradas: size: dato de entrada necesario para la operación; compressedEntrySize: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void add(long size, long compressedEntrySize) {
            checkDeadline();
            entries++;
            bytes += Math.max(0L, size);
            if(entries > maxEntries) throw new ProjectException(ProjectException.Reason.FILE_TOO_LARGE,
                    "The external project contains too many archive entries");
            if(bytes > maxBytes) throw new ProjectException(ProjectException.Reason.FILE_TOO_LARGE,
                    "The expanded external project exceeds the configured size limit");
            long denominator = compressedEntrySize > 0 ? compressedEntrySize : compressedBytes;
            if(denominator > 0 && (double) Math.max(1L, size) / denominator > maxRatio) {
                throw new ProjectException(ProjectException.Reason.FILE_TOO_LARGE,
                        "The external project contains an entry with an unsafe compression ratio");
            }
        }

        /** Descripción: Valida las condiciones necesarias para «checkDeadline» y rechaza entradas no válidas. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkDeadline() {
            if(System.nanoTime() > deadlineNanos) throw new ProjectException(ProjectException.Reason.INVALID_REQUEST,
                    "External project analysis exceeded the configured archive-read time limit");
        }
    }
}
