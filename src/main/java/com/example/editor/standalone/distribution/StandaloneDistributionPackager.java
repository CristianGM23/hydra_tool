package com.example.editor.standalone.distribution;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.example.editor.io.AtomicFiles;
import com.example.editor.standalone.StandaloneEditorGenerationProperties;

/** Creates a reproducible ZIP from an editor project after successful validation. */
@Service
public class StandaloneDistributionPackager {
    private static final long REPRODUCIBLE_TIMESTAMP = 315532800000L;
    private static final Set<String> EXCLUDED_SEGMENTS = Set.of("node_modules", "target", ".git", ".hydra-roundtrip-backups", "coverage", "playwright-report", "test-results", ".vite");
    private static final int HASH_BUFFER_BYTES = 64 * 1024;
    private final StandaloneEditorGenerationProperties properties;

    @Autowired
    /** Descripción: Inicializa el empaquetador con los límites configurables del editor standalone. Entradas: properties: límites de generación y archivo. Salida: deja el servicio preparado para empaquetar con presupuestos explícitos. */
    /** Descripción: Inicializa una instancia de StandaloneDistributionPackager con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public StandaloneDistributionPackager(StandaloneEditorGenerationProperties properties) { this.properties = Objects.requireNonNull(properties, "properties"); }

    /** Descripción: Inicializa una instancia compatible para pruebas focales usando la configuración standalone por defecto. Entradas: no requiere parámetros. Salida: deja el empaquetador listo con límites conservadores por defecto. */
    /** Descripción: Inicializa una instancia de StandaloneDistributionPackager con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ public StandaloneDistributionPackager() { this(new StandaloneEditorGenerationProperties()); }

    /** Descripción: Empaqueta una distribución validada mediante inventario acotado, ZIP temporal y publicación atómica, sin materializar el archivo completo para calcular su hash. Entradas: editorRoot: raíz del editor; archive: destino; validation: resultado de validación. Salida: devuelve ruta, entradas y SHA-256 del ZIP publicado. */
    /** Descripción: Ejecuta la operación «packageProject» correspondiente a la responsabilidad de StandaloneDistributionPackager. Entradas: editorRoot: dato de entrada necesario para la operación; archive: archivo o contenido que debe procesarse; validation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public StandaloneDistributionPackageResult packageProject(Path editorRoot, Path archive,
            StandaloneDistributionValidationReport validation){
        Objects.requireNonNull(validation, "validation");
        if(!validation.valid()){throw new IllegalStateException("Cannot package an invalid standalone distribution");}
        Path root = Objects.requireNonNull(editorRoot, "editorRoot").toAbsolutePath().normalize();
        Path output = Objects.requireNonNull(archive, "archive").toAbsolutePath().normalize();
        if(output.startsWith(root)){throw new IllegalArgumentException("Archive must be written outside the packaged editor root");}
        Path temporary = null;
        try{
            List<Path> files = collectFiles(root);
            Path parent = output.getParent();
            if(parent == null) throw new IllegalArgumentException("Archive must have a parent directory");
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, "." + output.getFileName() + "-", ".tmp");
            try(ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))){
                for(Path file : files){
                    if(Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IOException("Standalone distribution entry changed while packaging: " + file);
                    }
                    String entryName = normalized(root.relativize(file));
                    ZipEntry entry = new ZipEntry(entryName);
                    entry.setTime(REPRODUCIBLE_TIMESTAMP);
                    zip.putNextEntry(entry);
                    Files.copy(file, zip);
                    zip.closeEntry();
                }
            }
            long archiveBytes = Files.size(temporary);
            if(archiveBytes > properties.getMaximumArchiveBytes()) throw new IllegalStateException("Standalone distribution archive exceeds configured size limit");
            String digest = hash(temporary, properties.getMaximumArchiveBytes());
            AtomicFiles.moveReplacing(temporary, output);
            temporary = null;
            return new StandaloneDistributionPackageResult(output,
                    files.stream().map(path -> normalized(root.relativize(path))).toList(), digest);
        }catch(IOException exception){throw new IllegalStateException("Could not package standalone editor", exception);}
        finally{
            if(temporary != null) try { Files.deleteIfExists(temporary); } catch(IOException ignored) { /* best-effort cleanup */ }
        }
    }

    /** Descripción: Recorre la distribución sin seguir enlaces y limita explícitamente la cardinalidad antes de ordenar el inventario reproducible. Entradas: root: raíz del editor. Salida: devuelve únicamente ficheros portables admitidos y ordenados. */
    /** Descripción: Ejecuta la operación «collectFiles» correspondiente a la responsabilidad de StandaloneDistributionPackager. Entradas: root: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<Path> collectFiles(Path root) throws IOException {
        if(Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException("Standalone editor root must be a real directory");
        int maximumEntries = Math.max(1, properties.getMaximumEntries());
        ArrayList<Path> files = new ArrayList<>(Math.min(maximumEntries, 512));
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            /** Descripción: Omite subárboles no publicables y evita descender por directorios simbólicos. Entradas: dir: directorio visitado; attrs: atributos observados. Salida: continúa o salta el subárbol. */
            /** Descripción: Ejecuta la operación «preVisitDirectory» correspondiente a la responsabilidad de StandaloneDistributionPackager. Entradas: dir: dato de entrada necesario para la operación; attrs: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if(!dir.equals(root) && (attrs.isSymbolicLink() || excluded(root.relativize(dir)))) return FileVisitResult.SKIP_SUBTREE;
                return FileVisitResult.CONTINUE;
            }
            /** Descripción: Añade un fichero regular portable al inventario acotado y rechaza enlaces simbólicos. Entradas: file: fichero visitado; attrs: atributos observados. Salida: continúa el recorrido o falla si se supera la cardinalidad. */
            /** Descripción: Ejecuta la operación «visitFile» correspondiente a la responsabilidad de StandaloneDistributionPackager. Entradas: file: archivo o contenido que debe procesarse; attrs: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if(attrs.isSymbolicLink()) throw new IOException("Symbolic links are not portable in standalone distributions: " + normalized(root.relativize(file)));
                if(!attrs.isRegularFile() || excluded(root.relativize(file))) return FileVisitResult.CONTINUE;
                if(files.size() >= maximumEntries) throw new IOException("Standalone distribution exceeds configured entry limit of " + maximumEntries);
                files.add(file);
                return FileVisitResult.CONTINUE;
            }
        });
        files.sort(Comparator.comparing(path -> normalized(root.relativize(path))));
        return List.copyOf(files);
    }

    /** Descripción: Determina si una ruta pertenece a un segmento excluido del paquete standalone. Entradas: relative: ruta relativa. Salida: true si contiene un directorio no publicable. */
    /** Descripción: Ejecuta la operación «excluded» correspondiente a la responsabilidad de StandaloneDistributionPackager. Entradas: relative: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean excluded(Path relative){for(Path segment : relative){if(EXCLUDED_SEGMENTS.contains(segment.toString())){return true;}}return false;}
    /** Descripción: Normaliza una ruta a separadores portables ZIP. Entradas: value: ruta. Salida: devuelve la ruta portable. */
    /** Descripción: Ejecuta la operación «normalized» correspondiente a la responsabilidad de StandaloneDistributionPackager. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String normalized(Path value){return value.toString().replace('\\', '/');}

    /** Descripción: Calcula SHA-256 del archivo mediante streaming y aborta si cambia o supera el presupuesto durante la lectura. Entradas: path: archivo; maximumBytes: presupuesto. Salida: devuelve el hash hexadecimal. */
    /** Descripción: Evalúa la condición «hash» sin modificar el estado externo. Entradas: path: ruta o ubicación utilizada por la operación; maximumBytes: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private String hash(Path path, long maximumBytes) throws IOException {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch(NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
        long total = 0L; byte[] buffer = new byte[HASH_BUFFER_BYTES];
        try(InputStream input = Files.newInputStream(path)) {
            int read;
            while((read = input.read(buffer)) != -1) {
                if(read == 0) continue;
                try { total = Math.addExact(total, read); }
                catch(ArithmeticException exception) { throw new IOException("Standalone archive size overflow", exception); }
                if(total > maximumBytes) throw new IOException("Standalone distribution archive exceeds configured size limit");
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
