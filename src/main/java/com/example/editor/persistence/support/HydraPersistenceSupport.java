package com.example.editor.persistence.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteHashing;
import com.example.editor.io.AtomicFiles;
import tools.jackson.databind.ObjectWriter;

/** Shared deterministic storage helpers for current operational services. */
public final class HydraPersistenceSupport {
    /** Descripción: Inicializa una instancia de HydraPersistenceSupport con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private HydraPersistenceSupport() { }
    /** Descripción: Ejecuta la operación «safeId» correspondiente a la responsabilidad de HydraPersistenceSupport. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String safeId(String value, String field) {
        String safe = value == null ? "" : value.trim();
        if(!safe.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,239}")) throw new IllegalArgumentException(field + " is invalid");
        return safe;
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de HydraPersistenceSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public static String text(String value) { return value == null ? "" : value.trim(); }
    /** Descripción: Ejecuta la operación «sha256» correspondiente a la responsabilidad de HydraPersistenceSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public static String sha256(byte[] value) { return UpdateSiteHashing.sha256(value == null ? new byte[0] : value); }
    /** Descripción: Ejecuta la operación «fingerprint» correspondiente a la responsabilidad de HydraPersistenceSupport. Entradas: writer: componente de conversión utilizado durante el procesamiento; value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public static String fingerprint(ObjectWriter writer, Object value) {
        try { return sha256(writer.writeValueAsBytes(value)); }
        catch(Exception exception) { throw new IllegalStateException("Could not serialize deterministic evidence", exception); }
    }
    /** Descripción: Ejecuta la operación «atomicWrite» mediante el contrato atómico común, degradando solo si el filesystem declara que ATOMIC_MOVE no está soportado. Entradas: path: ruta de destino; bytes: contenido. Salida: no devuelve valor; propaga cualquier fallo real de E/S. */ public static void atomicWrite(Path path, byte[] bytes) {
        try { AtomicFiles.writeReplacing(path, bytes); }
        catch(IOException exception) { throw new IllegalStateException("Could not persist HYDRA evidence: " + path, exception); }
    }
    /** Descripción: Ejecuta la operación «jsonFiles» correspondiente a la responsabilidad de HydraPersistenceSupport. Entradas: root: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static List<Path> jsonFiles(Path root) {
        try {
            Files.createDirectories(root);
            try(var stream = Files.list(root)) { return stream.filter(path -> path.getFileName().toString().endsWith(".json")).sorted().toList(); }
        } catch(IOException exception) { return List.of(); }
    }
    /** Descripción: Ejecuta la operación «trim» correspondiente a la responsabilidad de HydraPersistenceSupport. Entradas: root: ruta o ubicación utilizada por la operación; maximum: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public static void trim(Path root, int maximum) {
        List<Path> paths;
        try {
            Files.createDirectories(root);
            try(var stream = Files.list(root)) {
                paths = stream.filter(Files::isRegularFile).sorted(Comparator.comparingLong(HydraPersistenceSupport::modified).reversed()).toList();
            }
        } catch(IOException exception) { return; }
        for(int index = Math.max(1, maximum); index < paths.size(); index++) try { Files.deleteIfExists(paths.get(index)); } catch(IOException ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/persistence/support/HydraPersistenceSupport.java",ignored);}
    }
    /** Descripción: Ejecuta la operación «modified» correspondiente a la responsabilidad de HydraPersistenceSupport. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static long modified(Path path) { try { return Files.getLastModifiedTime(path).toMillis(); } catch(IOException exception) { return 0L; } }
}
