package com.example.editor.runtime.instance;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.example.editor.config.StorageProperties;

/** Prevents multiple HYDRA processes from mutating the same local data root. */
@Component
@ConditionalOnProperty(prefix = "hydra.instance-lock", name = "enabled", havingValue = "true", matchIfMissing = true)
@Order(-100)
public final class HydraInstanceLock implements ApplicationRunner {
    private final HydraInstanceLockProperties properties;
    private final StorageProperties storage;
    private FileChannel channel;
    private FileLock lock;
    private Path metadataPath;

    @Autowired
    /** Descripción: Inicializa una instancia de HydraInstanceLock con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; storage: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public HydraInstanceLock(HydraInstanceLockProperties properties, StorageProperties storage) {
        this.properties = properties;
        this.storage = storage;
    }

    /** Descripción: Inicializa una instancia de HydraInstanceLock con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ HydraInstanceLock(HydraInstanceLockProperties properties) {
        this(properties, null);
    }

    @Override
    public synchronized void run(ApplicationArguments arguments) {
        acquire();
    }

    /** Descripción: Adquiere el bloqueo exclusivo de la instancia sin impedir la lectura del fichero de metadatos. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; mantiene el bloqueo hasta release o comunica si otra instancia ya lo posee. */
    public synchronized void acquire() {
        if (!properties.isEnabled() || lock != null) return;
        Path path = resolvedLockPath();
        Path guard = guardPath(path);
        try {
            Path parent = path.getParent();
            if (parent == null) throw new IllegalStateException("HYDRA instance lock requires a parent directory");
            Files.createDirectories(parent);
            rejectSymbolicLink(path);
            rejectSymbolicLink(guard);
            channel = FileChannel.open(guard, StandardOpenOption.CREATE, StandardOpenOption.READ,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            try { lock = channel.tryLock(); }
            catch (OverlappingFileLockException exception) { lock = null; }
            if (lock == null) {
                closeChannelQuietly();
                throw new IllegalStateException("Another HYDRA process is already using this data directory: " + parent);
            }
            metadataPath = path;
            writeOwnerMetadata(path);
        } catch (IOException exception) {
            release();
            throw new IllegalStateException("Could not acquire HYDRA instance lock: " + path, exception);
        }
    }

    /** Descripción: Obtiene la información correspondiente a «resolvedLockPath» dentro de HydraInstanceLock. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Path resolvedLockPath() {
        if (properties.getPath() != null) return properties.getPath().toAbsolutePath().normalize();
        if (storage != null) {
            Path metamodels = storage.metamodels().toAbsolutePath().normalize();
            Path dataRoot = metamodels.getParent();
            if (dataRoot != null) return dataRoot.resolve(".hydra.lock").normalize();
        }
        return Path.of("./data/.hydra.lock").toAbsolutePath().normalize();
    }

    /** Descripción: Deriva el fichero interno que recibe el FileLock del sistema operativo. Entradas: path: fichero legible de metadatos de la instancia. Salida: devuelve una ruta lateral estable que se usa solo como guardia exclusiva. */
    private Path guardPath(Path path) {
        return path.resolveSibling(path.getFileName().toString() + ".guard").normalize();
    }

    /** Descripción: Rechaza una ruta de bloqueo que ya sea un enlace simbólico. Entradas: path: ruta que debe validarse. Salida: no devuelve valor; lanza una excepción si la ruta es un enlace simbólico. */
    private void rejectSymbolicLink(Path path) {
        if (Files.isSymbolicLink(path)) {
            throw new IllegalStateException("HYDRA instance lock must not be a symbolic link: " + path);
        }
    }

    /** Descripción: Publica información legible sobre la instancia que mantiene el bloqueo. Entradas: path: fichero de metadatos que puede inspeccionarse incluso en Windows mientras la guardia está bloqueada. Salida: no devuelve valor. */
    private void writeOwnerMetadata(Path path) throws IOException {
        String owner = "pid=" + ProcessHandle.current().pid() + System.lineSeparator()
                + "startedAt=" + Instant.now() + System.lineSeparator();
        try (FileChannel metadata = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS)) {
            metadata.write(ByteBuffer.wrap(owner.getBytes(StandardCharsets.UTF_8)));
            metadata.force(true);
        }
    }

    @PreDestroy
    /** Descripción: Libera el bloqueo de instancia y retira sus metadatos sin abrir una ventana en la que otra instancia pueda perder su información. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor. */
    public synchronized void release() {
        // El metadato se elimina antes de liberar la guardia. Así otra instancia no puede
        // adquirir el lock, escribir su propietario y ver después borrado ese fichero por
        // la instancia anterior.
        if (lock != null && lock.isValid() && metadataPath != null) {
            try { Files.deleteIfExists(metadataPath); }
            catch (IOException ignored) { com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/runtime/instance/HydraInstanceLock.java", ignored); }
        }
        metadataPath = null;
        try { if (lock != null && lock.isValid()) lock.release(); }
        catch (IOException ignored) { com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/runtime/instance/HydraInstanceLock.java", ignored); }
        finally { lock = null; closeChannelQuietly(); }
    }

    /** Descripción: Elimina o libera el recurso gestionado por «closeChannelQuietly» de forma controlada. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void closeChannelQuietly() {
        try { if (channel != null) channel.close(); }
        catch (IOException ignored) { com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/runtime/instance/HydraInstanceLock.java", ignored); }
        finally { channel = null; }
    }
}
