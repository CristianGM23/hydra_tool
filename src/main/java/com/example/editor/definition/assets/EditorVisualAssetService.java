package com.example.editor.definition.assets;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.example.editor.config.StorageProperties;
import com.example.editor.project.storage.ProjectVisualAssetPolicy;

/** Almacén canónico, inmutable y direccionado por contenido para imágenes usadas por editores visuales. */
@Service
public class EditorVisualAssetService {
    public static final String PREFIX = "hydra-assets/";
    private static final long MAX_ASSET_BYTES = 4L * 1024L * 1024L;
    private final Path root;
    private final boolean createDirectories;
    private final ProjectVisualAssetPolicy policy;

    /** Inicializa el almacén de assets visuales canónicos. */
    /** Descripción: Inicializa una instancia de EditorVisualAssetService con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; policy: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EditorVisualAssetService(StorageProperties properties, ProjectVisualAssetPolicy policy) {
        this.root = properties.assets().toAbsolutePath().normalize().resolve("editor-visual").normalize();
        this.createDirectories = properties.createDirectories();
        this.policy = policy;
    }

    /** Valida y guarda una imagen externa de forma content-addressed, devolviendo una ruta portable de HYDRA. */
    /** Descripción: Ejecuta la operación «store» correspondiente a la responsabilidad de EditorVisualAssetService. Entradas: sourcePath: ruta o ubicación utilizada por la operación; content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public String store(String sourcePath, byte[] content) {
        return storeTracked(sourcePath, content).path();
    }

    /** Igual que {@link #store} pero indica si este almacenamiento creó el fichero (titularidad) o reutilizó uno idéntico ya existente. */
    /** Descripción: Ejecuta la operación «storeTracked» correspondiente a la responsabilidad de EditorVisualAssetService. Entradas: sourcePath: ruta o ubicación utilizada por la operación; content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public StoredAsset storeTracked(String sourcePath, byte[] content) {
        ProjectVisualAssetPolicy.ProjectVisualAsset asset = policy.validate(sourcePath, content);
        byte[] validatedContent = asset.content();
        String digest = sha256(validatedContent);
        String extension = extension(sourcePath);
        String key = digest + "." + extension;
        Path destination = resolveKey(key);
        boolean created = true;
        try {
            if(!Files.exists(root)) {
                if(!createDirectories) throw new IllegalStateException("Editor visual asset directory is not available");
                Files.createDirectories(root);
            }
            try {
                Files.write(destination, validatedContent, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } catch(FileAlreadyExistsException alreadyExists) {
                created = false;
                // Content-addressed keys make concurrent writes idempotent. Verify the winner instead of serializing all assets globally.
                if(!Files.isRegularFile(destination) || Files.size(destination) != validatedContent.length || !digest.equals(sha256(destination)))
                    throw new IllegalStateException("Content-addressed visual asset collision detected", alreadyExists);
            }
        } catch(IOException exception) {
            throw new IllegalStateException("Could not store editor visual asset", exception);
        }
        return new StoredAsset(PREFIX + key, created);
    }

    /** Ruta portable que tendría el asset (valida y calcula el hash sin escribir): permite registrar la intención antes de guardar. */
    /** Descripción: Ejecuta la operación «portablePath» correspondiente a la responsabilidad de EditorVisualAssetService. Entradas: sourcePath: ruta o ubicación utilizada por la operación; content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public String portablePath(String sourcePath, byte[] content) {
        ProjectVisualAssetPolicy.ProjectVisualAsset asset = policy.validate(sourcePath, content);
        return PREFIX + sha256(asset.content()) + "." + extension(sourcePath);
    }

    /** Indica si el asset canónico ya existe en el almacén. */
    /** Descripción: Evalúa la condición «exists» sin modificar el estado externo. Entradas: portablePath: ruta o ubicación utilizada por la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean exists(String portablePath) {
        return Files.isRegularFile(resolveKey(key(portablePath)));
    }

    /** Elimina un asset canónico concreto. Es idempotente: devuelve false si ya no existía. Solo debe llamarlo quien creó el asset. */
    /** Descripción: Elimina o libera el recurso gestionado por «deleteIfPresent» de forma controlada. Entradas: portablePath: ruta o ubicación utilizada por la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean deleteIfPresent(String portablePath) {
        Path target = resolveKey(key(portablePath));
        try { return Files.deleteIfExists(target); }
        catch(IOException exception) { throw new IllegalStateException("Could not delete editor visual asset " + portablePath, exception); }
    }

    /** Resultado de guardar un asset: ruta portable y si el fichero lo creó esta operación. */
    public record StoredAsset(String path, boolean created) { }

    /** Lee y vuelve a validar un asset canónico antes de servirlo al navegador o a un exportador. */
    /** Descripción: Obtiene la información correspondiente a «read» dentro de EditorVisualAssetService. Entradas: portablePath: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EditorVisualAsset read(String portablePath) {
        String key = key(portablePath);
        Path source = resolveKey(key);
        try {
            byte[] content = com.example.editor.io.BoundedIo.read(source, MAX_ASSET_BYTES);
            ProjectVisualAssetPolicy.ProjectVisualAsset asset = policy.validate(key, content);
            return new EditorVisualAsset(key, asset.mediaType(), asset.content());
        } catch(IOException exception) {
            throw new IllegalArgumentException("Editor visual asset was not found: " + portablePath, exception);
        }
    }

    /** Indica si una ruta apunta al almacén canónico de figuras de editor. */
    /** Descripción: Evalúa la condición «canonical» sin modificar el estado externo. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public static boolean canonical(String path) { return path != null && path.replace('\\','/').startsWith(PREFIX); }

    /** Descripción: Obtiene la información correspondiente a «resolveKey» dentro de EditorVisualAssetService. Entradas: key: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Path resolveKey(String key) {
        Path resolved = root.resolve(key).normalize();
        if(!resolved.startsWith(root)) throw new IllegalArgumentException("Visual asset path escapes its storage root");
        return resolved;
    }

    /** Descripción: Ejecuta la operación «key» correspondiente a la responsabilidad de EditorVisualAssetService. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String key(String path) {
        String normalized = path == null ? "" : path.trim().replace('\\','/');
        if(normalized.startsWith(PREFIX)) normalized = normalized.substring(PREFIX.length());
        if(!normalized.matches("[0-9a-f]{64}\\.(svg|png|jpg|jpeg|gif|webp)"))
            throw new IllegalArgumentException("Invalid editor visual asset path");
        return normalized;
    }

    /** Descripción: Ejecuta la operación «extension» correspondiente a la responsabilidad de EditorVisualAssetService. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String extension(String path) {
        String value = path == null ? "" : path.toLowerCase(Locale.ROOT);
        int dot = value.lastIndexOf('.');
        String extension = dot < 0 ? "" : value.substring(dot + 1);
        if(!java.util.Set.of("svg","png","jpg","jpeg","gif","webp").contains(extension))
            throw new IllegalArgumentException("Unsupported editor visual asset type: " + extension);
        return extension;
    }

    /** Descripción: Ejecuta la operación «sha256» correspondiente a la responsabilidad de EditorVisualAssetService. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String sha256(byte[] content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
        catch(NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }

    /** Descripción: Ejecuta la operación «sha256» correspondiente a la responsabilidad de EditorVisualAssetService. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try(InputStream input = new DigestInputStream(Files.newInputStream(path), digest)) {
                input.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch(NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }

    /** Contenido validado listo para servir o empaquetar. */
    public record EditorVisualAsset(String key, String mediaType, byte[] content) {
        public EditorVisualAsset { content = content == null ? new byte[0] : content.clone(); }
        /** Descripción: Ejecuta la operación «content» correspondiente a la responsabilidad de EditorVisualAssetService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public byte[] content() { return content.clone(); }
    }
}
