package com.example.editor.metamodel.importing;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;

/** Lee fuentes simples o contenedores anidados sin extraerlos al sistema de ficheros. */
@Component
public class SafeImportArchiveReader {
    private final MetamodelImportProperties properties;

    /** Descripción: Inicializa una instancia de SafeImportArchiveReader con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public SafeImportArchiveReader(MetamodelImportProperties properties) {
        this.properties = properties;
    }

    /** Lee todas las fuentes soportadas preservando rutas virtuales de contenedores anidados. */
    /** Descripción: Obtiene la información correspondiente a «read» dentro de Budget. Entradas: source: dato de entrada necesario para la operación; allowedExtensions: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public List<ImportSourceEntry> read(MetamodelImportSource source, Set<String> allowedExtensions) {
        if(!isZip(source)) {
            String path = normalizePath(source.fileName());
            if(!matches(path, allowedExtensions)) throw invalid("Unsupported source file inside import: " + path);
            if(source.content().length > properties.getMaxSourceFileSizeBytes()) throw invalid("Source file exceeds the configured per-file limit: " + path);
            return List.of(new ImportSourceEntry(path, source.content()));
        }
        Budget budget = new Budget(properties, source.content().length);
        List<ImportSourceEntry> result = new ArrayList<>();
        readArchive(source.fileName(), source.content(), "", 0, allowedExtensions, result, budget);
        if(result.isEmpty()) throw invalid("Import archive contains no supported source files");
        result.sort(Comparator.comparing(ImportSourceEntry::path));
        return List.copyOf(result);
    }

    /** Indica si la fuente es un contenedor ZIP/JAR reconocido por firma o extensión. */
    /** Descripción: Evalúa la condición «isZip» sin modificar el estado externo. Entradas: source: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isZip(MetamodelImportSource source) {
        byte[] content = source.content();
        String lower = source.fileName().toLowerCase(Locale.ROOT);
        return lower.endsWith(".zip") || lower.endsWith(".jar") || lower.endsWith(".war") || lower.endsWith(".ear")
                || hasZipSignature(content);
    }

    /** Descripción: Obtiene la información correspondiente a «readArchive» dentro de Budget. Entradas: archiveName: archivo o contenido que debe procesarse; archive: archivo o contenido que debe procesarse; prefix: dato de entrada necesario para la operación; depth: dato de entrada necesario para la operación; allowedExtensions: dato de entrada necesario para la operación; result: dato de entrada necesario para la operación; budget: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void readArchive(String archiveName, byte[] archive, String prefix, int depth,
            Set<String> allowedExtensions, List<ImportSourceEntry> result, Budget budget) {
        if(depth > properties.getMaxNestedArchiveDepth()) return;
        try(ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while((entry = input.getNextEntry()) != null) {
                budget.check();
                if(entry.isDirectory()) {
                    input.closeEntry();
                    continue;
                }
                String path = normalizePath(entry.getName());
                String virtualPath = prefix + path;
                boolean nested = isArchivePath(path);
                long perEntryLimit = nested ? Math.max(properties.getMaxFileSizeBytes(), properties.getMaxSourceFileSizeBytes())
                        : properties.getMaxSourceFileSizeBytes();
                byte[] content = readEntry(input, virtualPath, perEntryLimit);
                budget.add(content.length, entry.getCompressedSize());
                if(matches(virtualPath, allowedExtensions)) {
                    result.add(new ImportSourceEntry(virtualPath, content));
                    if(result.size() > properties.getMaxSourceFiles()) throw invalid("Import archive contains too many supported source files");
                }
                if(nested && depth < properties.getMaxNestedArchiveDepth() && hasZipSignature(content)) {
                    readArchive(archiveName + "!/" + path, content, virtualPath + "!/", depth + 1,
                            allowedExtensions, result, budget);
                }
                input.closeEntry();
            }
        } catch(MetamodelDesignException exception) {
            throw exception;
        } catch(IOException exception) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST,
                    "Could not read import archive: " + archiveName, exception);
        }
    }

    /** Descripción: Obtiene la información correspondiente a «readEntry» dentro de Budget. Entradas: input: valor de entrada que debe procesarse; path: ruta o ubicación utilizada por la operación; maximum: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] readEntry(ZipInputStream input, String path, long maximum) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16_384];
        int read;
        while((read = input.read(buffer)) >= 0) {
            if(read == 0) continue;
            if(output.size() + read > maximum) throw invalid("Archive entry exceeds the configured limit: " + path);
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    /** Descripción: Ejecuta la operación «normalizePath» correspondiente a la responsabilidad de Budget. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String normalizePath(String value) {
        String path = value == null ? "" : value.replace('\\', '/').trim();
        if(path.isBlank() || path.indexOf('\0') >= 0 || path.startsWith("/") || path.matches("^[A-Za-z]:.*"))
            throw invalid("Unsafe import archive path: " + value);
        Path normalized = Path.of(path).normalize();
        if(normalized.isAbsolute() || normalized.startsWith("..")) throw invalid("Unsafe import archive path: " + value);
        String result = normalized.toString().replace('\\', '/');
        if(result.equals("..") || result.startsWith("../")) throw invalid("Unsafe import archive path: " + value);
        return result;
    }

    /** Descripción: Evalúa la condición «matches» sin modificar el estado externo. Entradas: path: ruta o ubicación utilizada por la operación; allowedExtensions: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean matches(String path, Set<String> allowedExtensions) {
        String lower = path.toLowerCase(Locale.ROOT);
        return allowedExtensions.stream().map(item -> item.toLowerCase(Locale.ROOT)).anyMatch(lower::endsWith);
    }

    /** Descripción: Evalúa la condición «isArchivePath» sin modificar el estado externo. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isArchivePath(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".zip") || lower.endsWith(".jar") || lower.endsWith(".war") || lower.endsWith(".ear");
    }

    /** Descripción: Evalúa la condición «hasZipSignature» sin modificar el estado externo. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean hasZipSignature(byte[] content) {
        return content.length >= 4 && content[0] == 'P' && content[1] == 'K';
    }

    /** Descripción: Ejecuta la operación «invalid» correspondiente a la responsabilidad de Budget. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetamodelDesignException invalid(String message) {
        return new MetamodelDesignException(Reason.INVALID_REQUEST, message);
    }

    private static final class Budget {
        private final int maxEntries;
        private final long maxBytes;
        private final double maxRatio;
        private final long compressedBytes;
        private final long deadline;
        private int entries;
        private long bytes;

        /** Descripción: Inicializa una instancia de Budget con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; compressedBytes: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private Budget(MetamodelImportProperties properties, long compressedBytes) {
            maxEntries = Math.max(1, properties.getMaxArchiveEntries());
            maxBytes = Math.max(1L, properties.getMaxArchiveUncompressedBytes());
            maxRatio = Math.max(1D, properties.getMaxArchiveCompressionRatio());
            this.compressedBytes = Math.max(1L, compressedBytes);
            deadline = System.nanoTime() + Duration.ofMillis(Math.max(1000L, properties.getMaxArchiveReadMillis())).toNanos();
        }

        /** Descripción: Ejecuta la operación «add» correspondiente a la responsabilidad de Budget. Entradas: size: dato de entrada necesario para la operación; compressedEntrySize: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void add(long size, long compressedEntrySize) {
            check();
            entries++;
            bytes += Math.max(0L, size);
            if(entries > maxEntries) throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Import archive exceeds the configured entry limit");
            if(bytes > maxBytes) throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Import archive exceeds the configured uncompressed size limit");
            long denominator = compressedEntrySize > 0 ? compressedEntrySize : compressedBytes;
            if(denominator > 0 && (double) Math.max(1L, size) / denominator > maxRatio)
                throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Import archive contains an entry with an unsafe compression ratio");
        }

        /** Descripción: Valida las condiciones necesarias para «check» y rechaza entradas no válidas. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void check() {
            if(System.nanoTime() > deadline) throw new MetamodelDesignException(Reason.INVALID_REQUEST,
                    "Import archive analysis exceeded the configured read-time limit");
        }
    }
}
