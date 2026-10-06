package com.example.editor.metamodel.importing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;

/** Converts a browser directory selection into a deterministic, bounded archive for the existing metamodel import pipeline. */
@Service
public class MetamodelBrowserDirectoryArchiveService {
    private final MetamodelImportProperties properties;

    /** Descripción: Inicializa el archivador de directorios seleccionados en el navegador. Entradas: properties: límites configurables de importación. Salida: deja el servicio listo para reutilizar el pipeline seguro de importación existente. */
    /** Descripción: Inicializa una instancia de MetamodelBrowserDirectoryArchiveService con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelBrowserDirectoryArchiveService(MetamodelImportProperties properties) {
        this.properties = properties;
    }

    /** Descripción: Empaqueta de forma determinista una selección de directorio del navegador. Entradas: files: archivos seleccionados; paths: rutas relativas declaradas por el navegador. Salida: devuelve un ZIP acotado que puede analizar el importador neutral existente. */
    /** Descripción: Ejecuta la operación «archive» correspondiente a la responsabilidad de MetamodelBrowserDirectoryArchiveService. Entradas: files: archivo o contenido que debe procesarse; paths: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ public byte[] archive(List<? extends MultipartFile> files, List<String> paths) {
        if (files == null || files.isEmpty()) throw invalid("Select a metamodel folder before importing");
        if (paths == null || paths.size() != files.size()) throw invalid("The selected metamodel folder paths do not match the uploaded files");
        int maximumFiles = Math.min(properties.getMaxArchiveEntries(), properties.getMaxSourceFiles());
        if (files.size() > maximumFiles) throw invalid("The selected metamodel folder contains too many files");

        Set<String> entries = new LinkedHashSet<>();
        long totalBytes = 0L;
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            for (int index = 0; index < files.size(); index++) {
                MultipartFile file = files.get(index);
                String path = normalize(paths.get(index));
                if (!entries.add(path)) throw invalid("The selected metamodel folder contains a duplicate path: " + path);
                long declaredSize = file.getSize();
                if (declaredSize < 0 || declaredSize > properties.getMaxSourceFileSizeBytes()) {
                    throw invalid("A metamodel source file exceeds the configured size limit: " + path);
                }
                ZipEntry entry = new ZipEntry(path);
                entry.setTime(0L);
                zip.putNextEntry(entry);
                long copied = copyBounded(file, zip, path, properties.getMaxSourceFileSizeBytes());
                zip.closeEntry();
                totalBytes += copied;
                if (totalBytes > properties.getMaxArchiveUncompressedBytes()) {
                    throw invalid("The selected metamodel folder exceeds the configured import-size limit");
                }
            }
            zip.finish();
            if (buffer.size() > properties.getMaxArchiveUncompressedBytes()) throw invalid("The selected metamodel folder exceeds the configured archive-size limit");
            return buffer.toByteArray();
        } catch (MetamodelDesignException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new MetamodelDesignException(Reason.STORAGE_ERROR, "Could not read the selected metamodel folder", exception);
        }
    }

    /**
     * Descripción: copia un archivo del multipart imponiendo el límite sobre los bytes realmente leídos y no solo sobre el tamaño declarado.
     * Entradas: file: archivo recibido; zip: destino; path: ruta segura; maximumBytes: límite por archivo.
     * Salida: devuelve el número real de bytes copiados o rechaza la entrada antes de desbordar el presupuesto.
     */
    /** Descripción: Ejecuta la operación «copyBounded» correspondiente a la responsabilidad de MetamodelBrowserDirectoryArchiveService. Entradas: file: archivo o contenido que debe procesarse; zip: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; maximumBytes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static long copyBounded(MultipartFile file, ZipOutputStream zip, String path, long maximumBytes) throws IOException {
        byte[] chunk = new byte[8192];
        long copied = 0L;
        try (InputStream input = file.getInputStream()) {
            for (int read; (read = input.read(chunk)) >= 0;) {
                if (read == 0) continue;
                copied += read;
                if (copied > maximumBytes) throw invalid("A metamodel source file exceeds the configured size limit: " + path);
                zip.write(chunk, 0, read);
            }
        }
        return copied;
    }

    /** Descripción: Normaliza una ruta relativa proveniente de webkitRelativePath sin permitir traversal ni rutas absolutas. Entradas: rawPath: ruta relativa recibida del navegador. Salida: devuelve una ruta portable y segura para el ZIP. */
    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de MetamodelBrowserDirectoryArchiveService. Entradas: rawPath: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ String normalize(String rawPath) {
        String path = rawPath == null ? "" : rawPath.replace('\\', '/').trim();
        while (path.startsWith("/")) path = path.substring(1);
        if (path.isBlank() || path.equals("..") || path.startsWith("../") || path.contains("/../") || path.contains("//") || path.indexOf('\0') >= 0 || path.matches("^[A-Za-z]:.*")) {
            throw invalid("The selected metamodel folder contains an invalid path");
        }
        return path;
    }

    /** Descripción: crea un error de petición inválida conservando el contrato de errores del diseñador de metamodelos. Entradas: message: explicación segura del rechazo. Salida: devuelve la excepción tipada que debe propagar el pipeline de importación. */
    /** Descripción: Ejecuta la operación «invalid» correspondiente a la responsabilidad de MetamodelBrowserDirectoryArchiveService. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static MetamodelDesignException invalid(String message) {
        return new MetamodelDesignException(Reason.INVALID_REQUEST, message);
    }
}
