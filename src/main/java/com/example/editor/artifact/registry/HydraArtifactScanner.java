package com.example.editor.artifact.registry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Bounded scanner that inventories canonical HYDRA files without following symbolic links. */
@Component
public class HydraArtifactScanner {
    private static final int HASH_BUFFER_BYTES = 64 * 1024;
    private final HydraArtifactScannerProperties properties;

    /** Descripción: Inicializa el escáner con presupuestos configurables de cardinalidad, profundidad y bytes. Entradas: properties: límites de escaneo de artefactos. Salida: deja el escáner preparado con límites explícitos. */
    /** Descripción: Inicializa una instancia de HydraArtifactScanner con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public HydraArtifactScanner(HydraArtifactScannerProperties properties) {
        this.properties = properties;
    }

    /** Descripción: Escanea artefactos canónicos sin seguir enlaces simbólicos y con límites de cardinalidad y bytes. Entradas: root: raíz que debe inventariarse. Salida: devuelve descriptores deterministas ordenados por ruta relativa o falla antes de devolver un inventario parcial si se exceden los presupuestos globales. */
    /** Descripción: Ejecuta la operación «scan» correspondiente a la responsabilidad de HashResult. Entradas: root: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ public List<HydraArtifactDescriptor> scan(Path root){
        Path safeRoot = root.toAbsolutePath().normalize();
        if(!Files.isDirectory(safeRoot)){return List.of();}
        List<HydraArtifactDescriptor> result = new ArrayList<>();
        ScanBudget budget = new ScanBudget(properties.getMaximumScanFiles(), properties.getMaximumScanTotalBytes());
        try {
            Files.walkFileTree(safeRoot, java.util.Set.of(), properties.getMaximumScanDepth(), new SimpleFileVisitor<>() {
                /** Descripción: Procesa un fichero del inventario sin seguir enlaces, aplicando cardinalidad y presupuestos antes de calcular su descriptor. Entradas: path: fichero visitado; attributes: atributos observados durante el recorrido. Salida: continúa el recorrido o propaga un exceso de límite. */
                /** Descripción: Ejecuta la operación «visitFile» correspondiente a la responsabilidad de HashResult. Entradas: path: ruta o ubicación utilizada por la operación; attributes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) throws IOException {
                    if(!attributes.isRegularFile() || Files.isSymbolicLink(path)) return FileVisitResult.CONTINUE;
                    budget.see(path);
                    long bytes = attributes.size();
                    if(bytes > properties.getMaximumScanFileBytes()) return FileVisitResult.CONTINUE;
                    budget.accept(path, bytes);
                    result.add(descriptor(safeRoot, path, bytes, attributes.lastModifiedTime().toInstant()));
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch(IOException exception) {
            throw new IllegalStateException("Could not scan canonical artifacts", exception);
        }
        result.sort(Comparator.comparing(HydraArtifactDescriptor::relativePath));
        return List.copyOf(result);
    }

    /** Descripción: Construye un descriptor verificando de nuevo que la ruta siga siendo un fichero regular y calculando su SHA-256 por streaming. Entradas: root: raíz canónica; path: fichero; expectedBytes: tamaño observado durante el recorrido; modifiedAt: fecha observada durante el recorrido. Salida: devuelve el descriptor del artefacto sin materializar su contenido completo. */
    /** Descripción: Ejecuta la operación «descriptor» correspondiente a la responsabilidad de HashResult. Entradas: root: ruta o ubicación utilizada por la operación; path: ruta o ubicación utilizada por la operación; expectedBytes: dato de entrada necesario para la operación; modifiedAt: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private HydraArtifactDescriptor descriptor(Path root, Path path, long expectedBytes, Instant modifiedAt) throws IOException {
        if(Files.isSymbolicLink(path) || !Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Artifact changed type while being scanned: " + path);
        }
        String relative = root.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
        String id = relative.replaceAll("[^A-Za-z0-9._/-]+", "-");
        HashResult hash = sha256(path, properties.getMaximumScanFileBytes());
        if(hash.bytes() != expectedBytes) throw new IOException("Artifact changed size while being scanned: " + relative);
        return new HydraArtifactDescriptor(id, path.getFileName().toString(), kind(relative), "1.0",
                producer(relative), relative, hash.sha256(), HydraArtifactStatus.AVAILABLE,
                modifiedAt, List.of(), Map.of("size", Long.toString(hash.bytes())));
    }

    /** Descripción: Determina la categoría neutral de un artefacto a partir de su ruta portable. Entradas: path: ruta portable. Salida: devuelve la categoría semántica reconocida o OTHER. */
    /** Descripción: Ejecuta la operación «kind» correspondiente a la responsabilidad de HashResult. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private HydraArtifactKind kind(String path){
        String value = path.toLowerCase();
        if(value.contains("metamodel")) return HydraArtifactKind.METAMODEL;
        if(value.contains("validation")) return HydraArtifactKind.VALIDATION;
        if(value.contains("quickfix") || value.contains("quick-fix")) return HydraArtifactKind.QUICK_FIX;
        if(value.contains("event-flow")) return HydraArtifactKind.EVENT_FLOW;
        if(value.contains("event-binding")) return HydraArtifactKind.EVENT_BINDING;
        if(value.contains("event")) return HydraArtifactKind.EVENT_DEFINITION;
        if(value.contains("gamification")) return HydraArtifactKind.GAMIFICATION;
        if(value.contains("editor-definition")) return HydraArtifactKind.EDITOR_DEFINITION;
        if(value.contains("integration")) return HydraArtifactKind.INTEGRATION_PROFILE;
        if(value.endsWith(".xmi")) return HydraArtifactKind.MODEL;
        return HydraArtifactKind.OTHER;
    }

    /** Descripción: Deduce el productor lógico de un artefacto a partir del primer segmento de su ruta. Entradas: path: ruta portable. Salida: devuelve el productor o HYDRA para elementos situados en la raíz. */
    /** Descripción: Ejecuta la operación «producer» correspondiente a la responsabilidad de HashResult. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String producer(String path){
        String[] parts = path.replace('\\', '/').split("/");
        return parts.length > 1 ? parts[0] : "HYDRA";
    }

    /** Descripción: Calcula SHA-256 de un fichero mediante lectura incremental y comprueba que no supere el límite configurado aunque crezca durante el escaneo. Entradas: path: fichero; maximumBytes: presupuesto máximo. Salida: devuelve hash y bytes realmente leídos. */
    /** Descripción: Ejecuta la operación «sha256» correspondiente a la responsabilidad de HashResult. Entradas: path: ruta o ubicación utilizada por la operación; maximumBytes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private HashResult sha256(Path path, long maximumBytes) throws IOException {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch(NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is not available", exception); }
        long total = 0L;
        byte[] buffer = new byte[HASH_BUFFER_BYTES];
        try(InputStream input = Files.newInputStream(path)) {
            int read;
            while((read = input.read(buffer)) != -1) {
                if(read == 0) continue;
                try { total = Math.addExact(total, read); }
                catch(ArithmeticException exception) { throw new IOException("Artifact size overflow while hashing: " + path, exception); }
                if(total > maximumBytes) throw new IOException("Artifact exceeds configured scan file limit of " + maximumBytes + " bytes: " + path);
                digest.update(buffer, 0, read);
            }
        }
        return new HashResult(total, HexFormat.of().formatHex(digest.digest()));
    }

    private record HashResult(long bytes, String sha256) { }

    private static final class ScanBudget {
        private final int maximumFiles;
        private final long maximumBytes;
        private int visitedFiles;
        private long acceptedBytes;
        /** Descripción: Inicializa el presupuesto mutable de un único escaneo. Entradas: maximumFiles: máximo de ficheros visitables; maximumBytes: máximo de bytes aceptables. Salida: deja los contadores a cero con sus límites inmutables. */
        /** Descripción: Inicializa una instancia de ScanBudget con sus dependencias y datos de entrada. Entradas: maximumFiles: archivo o contenido que debe procesarse; maximumBytes: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private ScanBudget(int maximumFiles, long maximumBytes) { this.maximumFiles = maximumFiles; this.maximumBytes = maximumBytes; }
        /** Descripción: Registra un fichero regular visitado y aborta si la cardinalidad supera el presupuesto. Entradas: path: fichero observado. Salida: no devuelve valor; falla cerrado cuando se rebasa el máximo. */
        /** Descripción: Ejecuta la operación «see» correspondiente a la responsabilidad de HashResult. Entradas: path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void see(Path path) throws IOException {
            visitedFiles++;
            if(visitedFiles > maximumFiles) throw new IOException("Artifact scan exceeds configured file limit of " + maximumFiles + " at: " + path);
        }
        /** Descripción: Acumula los bytes de un artefacto aceptado y aborta ante overflow o exceso del presupuesto total. Entradas: path: fichero; bytes: tamaño aceptado. Salida: no devuelve valor; mantiene el contador acotado. */
        /** Descripción: Ejecuta la operación «accept» correspondiente a la responsabilidad de HashResult. Entradas: path: ruta o ubicación utilizada por la operación; bytes: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void accept(Path path, long bytes) throws IOException {
            try { acceptedBytes = Math.addExact(acceptedBytes, bytes); }
            catch(ArithmeticException exception) { throw new IOException("Artifact scan byte count overflow at: " + path, exception); }
            if(acceptedBytes > maximumBytes) throw new IOException("Artifact scan exceeds configured total byte limit of " + maximumBytes + " at: " + path);
        }
    }
}
