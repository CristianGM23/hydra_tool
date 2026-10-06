package com.example.editor.metamodel.importing.updatesite;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;

/**
 * Lee un update site empaquetado o un conjunto de archivos de directorio.
 *
 * <p>Las entradas se vierten al almacén content-addressed a medida que se leen,
 * de forma que el repositorio resultante conserva en memoria únicamente rutas
 * y descriptores de blobStore. Los bytes se materializan de manera lazy cuando un
 * analizador concreto los solicita.</p>
 */
@Component
public class UpdateSiteArchiveReader {
    private final UpdateSiteImportProperties properties;
    private final UpdateSiteArtifactBlobStore blobStore;

    @Autowired
    /** Descripción: Inicializa una instancia de UpdateSiteArchiveReader con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; blobStore: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteArchiveReader(UpdateSiteImportProperties properties, UpdateSiteArtifactBlobStore blobStore) {
        this.properties = properties;
        this.blobStore = blobStore;
    }

    /** Constructor de compatibilidad para tests focalizados y embedders. */
    /** Descripción: Inicializa una instancia de UpdateSiteArchiveReader con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteArchiveReader(UpdateSiteImportProperties properties) {
        this(properties, new UpdateSiteArtifactBlobStore(properties));
    }

    /** Descripción: Obtiene la información correspondiente a «read» dentro de ArchiveBudget. Entradas: inputs: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ UpdateSiteRepositoryContent read(List<UpdateSiteInputFile> inputs) {
        if(inputs == null || inputs.isEmpty()) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "At least one update-site file is required");
        }

        long uploadBytes = inputs.stream().mapToLong(UpdateSiteInputFile::size).sum();
        if(uploadBytes > properties.getMaxUploadBytes()) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Update-site upload exceeds the configured size limit");
        }

        ArchiveBudget budget = new ArchiveBudget(properties.getMaxEntries(), properties.getMaxUncompressedBytes(), uploadBytes, properties.getMaxCompressionRatio(), properties.getMaxArchiveReadMillis(), properties.getMaxArchiveCpuMillis());
        Map<String, UpdateSiteStoredArtifact> files = new LinkedHashMap<>();
        for(UpdateSiteInputFile input : inputs.stream().sorted(Comparator.comparing(UpdateSiteInputFile::path)).toList()) {
            String path = normalize(input.path());
            if(isRepositoryZip(path, input)) {
                try(InputStream stream = input.openStream()) {
                    readArchive(path, stream, "", 0, files, budget, true);
                } catch(IOException exception) {
                    throw new MetamodelDesignException(Reason.INVALID_REQUEST,
                            "Could not read update-site archive: " + path, exception);
                }
            } else {
                try(InputStream stream = input.openStream()) {
                    store(files, path, stream, budget);
                } catch(IOException exception) {
                    throw new MetamodelDesignException(Reason.INVALID_REQUEST,
                            "Could not read update-site input: " + path, exception);
                }
            }
        }

        List<Map.Entry<String, UpdateSiteStoredArtifact>> archives = new ArrayList<>(files.entrySet()).stream()
                .filter(entry -> isJar(entry.getKey(), entry.getValue())).sorted(Map.Entry.comparingByKey()).toList();
        for(Map.Entry<String, UpdateSiteStoredArtifact> archive : archives) {
            try(InputStream stream = blobStore.open(archive.getValue())) {
                readArchive(archive.getKey(), stream, archive.getKey() + "!/", 1, files, budget, false);
            } catch(IOException exception) {
                throw new MetamodelDesignException(Reason.INVALID_REQUEST,
                        "Could not read update-site plugin archive: " + archive.getKey(), exception);
            }
        }

        if(files.isEmpty()) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "The update site contains no readable files");
        }

        StringBuilder fingerprintMaterial = new StringBuilder();
        files.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> fingerprintMaterial
                .append(entry.getKey()).append(':').append(entry.getValue().sha256()).append('\n'));
        return new UpdateSiteRepositoryContent(files, blobStore,
                UpdateSiteHashing.sha256(fingerprintMaterial.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                budget.bytes());
    }

    /** Descripción: Obtiene la información correspondiente a «readArchive» dentro de ArchiveBudget. Entradas: archivePath: ruta o ubicación utilizada por la operación; source: dato de entrada necesario para la operación; prefix: dato de entrada necesario para la operación; depth: dato de entrada necesario para la operación; files: archivo o contenido que debe procesarse; budget: dato de entrada necesario para la operación; repositoryContainer: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void readArchive(String archivePath, InputStream source, String prefix, int depth,
            Map<String, UpdateSiteStoredArtifact> files, ArchiveBudget budget, boolean repositoryContainer) {
        if(depth > properties.getMaxNestedArchiveDepth()) {
            return;
        }

        try(ZipInputStream input = new ZipInputStream(source)) {
            ZipEntry entry;
            while((entry = input.getNextEntry()) != null) {
                if(entry.isDirectory()) {
                    input.closeEntry();
                    continue;
                }
                String entryPath = normalize(entry.getName());
                String virtualPath = prefix + entryPath;
                UpdateSiteStoredArtifact artifact = blobStore.store(input, properties.getMaxEntryBytes(),
                        archivePath + "!/" + entryPath);
                put(files, virtualPath, artifact, budget);
                input.closeEntry();

                if(!repositoryContainer && depth < properties.getMaxNestedArchiveDepth()
                        && isNestedMetadataArchive(entryPath, artifact)) {
                    try(InputStream nested = blobStore.open(artifact)) {
                        readArchive(archivePath + "!/" + entryPath, nested, virtualPath + "!/",
                                depth + 1, files, budget, false);
                    }
                }
            }
        } catch(MetamodelDesignException exception) {
            throw exception;
        } catch(IOException exception) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST,
                    "Could not read update-site archive: " + archivePath, exception);
        }
    }

    /** Descripción: Ejecuta la operación «store» correspondiente a la responsabilidad de ArchiveBudget. Entradas: files: archivo o contenido que debe procesarse; path: ruta o ubicación utilizada por la operación; source: dato de entrada necesario para la operación; budget: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void store(Map<String, UpdateSiteStoredArtifact> files, String path, InputStream source,
            ArchiveBudget budget) {
        UpdateSiteStoredArtifact artifact = blobStore.store(source, properties.getMaxEntryBytes(), path);
        put(files, path, artifact, budget);
    }

    /** Descripción: Ejecuta la operación «put» correspondiente a la responsabilidad de ArchiveBudget. Entradas: files: archivo o contenido que debe procesarse; path: ruta o ubicación utilizada por la operación; artifact: dato de entrada necesario para la operación; budget: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void put(Map<String, UpdateSiteStoredArtifact> files, String path, UpdateSiteStoredArtifact artifact,
            ArchiveBudget budget) {
        // Incluso un path duplicado consume presupuesto de entrada: evita que un
        // ZIP abuse de duplicados para saltarse maxEntries/maxUncompressedBytes.
        budget.add(artifact.size());
        files.putIfAbsent(path, artifact);
    }

    /** Descripción: Evalúa la condición «isRepositoryZip» sin modificar el estado externo. Entradas: path: ruta o ubicación utilizada por la operación; input: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isRepositoryZip(String path, UpdateSiteInputFile input) {
        String lower = path.toLowerCase(Locale.ROOT);
        boolean supportedExtension = lower.endsWith(".zip") || lower.endsWith(".updatesite")
                || lower.endsWith(".p2") || lower.endsWith(".p2.zip")
                || lower.endsWith(".site.zip") || lower.endsWith(".repository.zip")
                || lower.endsWith(".update-site.zip") || lower.endsWith(".updatesite.zip")
                || lower.endsWith(".zipx") || lower.endsWith(".war") || lower.endsWith(".ear");
        if(!supportedExtension) return false;
        try(InputStream stream = input.openStream()) {
            return isZipSignature(stream);
        } catch(IOException exception) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST,
                    "Could not inspect update-site input: " + path, exception);
        }
    }

    /** Descripción: Evalúa la condición «isJar» sin modificar el estado externo. Entradas: path: ruta o ubicación utilizada por la operación; artifact: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isJar(String path, UpdateSiteStoredArtifact artifact) {
        if(!path.toLowerCase(Locale.ROOT).endsWith(".jar")) return false;
        try(InputStream stream = blobStore.open(artifact)) {
            return isZipSignature(stream);
        } catch(IOException exception) {
            return false;
        }
    }

    /** Descripción: Evalúa la condición «isNestedMetadataArchive» sin modificar el estado externo. Entradas: path: ruta o ubicación utilizada por la operación; artifact: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isNestedMetadataArchive(String path, UpdateSiteStoredArtifact artifact) {
        String lower = path.toLowerCase(Locale.ROOT);
        if(!lower.endsWith("content.jar") && !lower.endsWith("artifacts.jar")) return false;
        try(InputStream stream = blobStore.open(artifact)) {
            return isZipSignature(stream);
        } catch(IOException exception) {
            return false;
        }
    }

    /** Descripción: Evalúa la condición «isZipSignature» sin modificar el estado externo. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isZipSignature(InputStream content) throws IOException {
        int first = content.read();
        int second = content.read();
        return first == 'P' && second == 'K';
    }

    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de ArchiveBudget. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String normalize(String value) {
        String path = value == null ? "" : value.replace('\\', '/').trim();
        if(path.isBlank() || path.indexOf('\0') >= 0 || path.startsWith("/") || path.matches("^[A-Za-z]:.*")) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Unsafe update-site path: " + value);
        }
        Path normalized = Path.of(path).normalize();
        if(normalized.isAbsolute() || normalized.startsWith("..")) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Unsafe update-site path: " + value);
        }
        String result = normalized.toString().replace('\\', '/');
        if(result.equals("..") || result.startsWith("../")) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Unsafe update-site path: " + value);
        }
        return result;
    }

    private static final class ArchiveBudget {
        private final int maxEntries;
        private final long maxBytes;
        private final long compressedBytes;
        private final double maxCompressionRatio;
        private final long deadlineNanos;
        private final ThreadMXBean threadMxBean;
        private final long cpuStartNanos;
        private final long maxCpuNanos;
        private int entries;
        private long bytes;

        /** Descripción: Inicializa una instancia de ArchiveBudget con sus dependencias y datos de entrada. Entradas: maxEntries: dato de entrada necesario para la operación; maxBytes: dato de entrada necesario para la operación; compressedBytes: dato de entrada necesario para la operación; maxCompressionRatio: dato de entrada necesario para la operación; maxReadMillis: dato de entrada necesario para la operación; maxCpuMillis: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private ArchiveBudget(int maxEntries, long maxBytes, long compressedBytes, double maxCompressionRatio, long maxReadMillis, long maxCpuMillis) {
            this.maxEntries = maxEntries;
            this.maxBytes = maxBytes;
            this.compressedBytes = Math.max(1L, compressedBytes);
            this.maxCompressionRatio = maxCompressionRatio;
            this.deadlineNanos = System.nanoTime() + Math.multiplyExact(maxReadMillis, 1_000_000L);
            this.threadMxBean = ManagementFactory.getThreadMXBean();
            this.cpuStartNanos = threadMxBean.isCurrentThreadCpuTimeSupported() ? threadMxBean.getCurrentThreadCpuTime() : -1L;
            this.maxCpuNanos = Math.multiplyExact(maxCpuMillis, 1_000_000L);
        }

        /** Descripción: Ejecuta la operación «add» correspondiente a la responsabilidad de ArchiveBudget. Entradas: size: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void add(long size) {
            if(System.nanoTime() > deadlineNanos) throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Update-site archive analysis exceeded the configured time budget");
            if(cpuStartNanos >= 0L && threadMxBean.getCurrentThreadCpuTime() - cpuStartNanos > maxCpuNanos)
                throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Update-site archive analysis exceeded the configured CPU budget");
            entries++;
            bytes = Math.addExact(bytes, size);
            if(entries > maxEntries) {
                throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Update site exceeds the configured entry limit");
            }
            if(bytes > maxBytes) {
                throw new MetamodelDesignException(Reason.INVALID_REQUEST,
                        "Update site exceeds the configured uncompressed size limit");
            }
            if(((double)bytes / (double)compressedBytes) > maxCompressionRatio) {
                throw new MetamodelDesignException(Reason.INVALID_REQUEST,
                        "Update site exceeds the configured compression-ratio limit");
            }
        }

        /** Descripción: Ejecuta la operación «bytes» correspondiente a la responsabilidad de ArchiveBudget. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private long bytes() {
            return bytes;
        }
    }
}
