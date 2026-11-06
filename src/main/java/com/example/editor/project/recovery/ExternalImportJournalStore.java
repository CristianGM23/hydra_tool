package com.example.editor.project.recovery;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Component;

import com.example.editor.config.StorageProperties;
import com.example.editor.io.AtomicFiles;
import com.example.editor.io.BoundedIo;

import tools.jackson.databind.ObjectMapper;

/**
 * Persistencia durable de {@link ExternalImportJournal}. Cada escritura se vuelca a un temporal, se fuerza a disco
 * ({@code force}) y se publica de forma atómica con {@link AtomicFiles}; nunca queda un diario a medio escribir.
 *
 * <p>La exclusión mutua entre importaciones combina un cerrojo en la JVM y un cerrojo de fichero ({@code import.lock}),
 * de modo que protege también frente a otra instancia del servidor sobre el mismo directorio.
 */
@Component
public class ExternalImportJournalStore {
    private final Path directory;
    private final ObjectMapper mapper;
    private final ReentrantLock jvmLock = new ReentrantLock();

    /** Crea el almacén junto al directorio de assets (fuera de él: el workspace exporta {@code assets/}). */
    /** Descripción: Inicializa una instancia de ExternalImportJournalStore con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public ExternalImportJournalStore(StorageProperties properties, ObjectMapper mapper) {
        this.directory = properties.assets().toAbsolutePath().normalize().resolveSibling("external-import-journal");
        this.mapper = mapper;
    }

    /** Directorio donde viven los diarios (útil para diagnóstico y pruebas). */
    /** Descripción: Ejecuta la operación «directory» correspondiente a la responsabilidad de Hold. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Path directory() { return directory; }

    /** Espera a tener la exclusividad de las importaciones. */
    /** Descripción: Ejecuta la operación «acquire» correspondiente a la responsabilidad de Hold. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ Hold acquire() {
        jvmLock.lock();
        try { return openFileLock(true, jvmLock); }
        catch(RuntimeException exception) { jvmLock.unlock(); throw exception; }
    }

    /** Intenta obtener la exclusividad sin esperar; {@code null} si otra importación (de esta u otra JVM) la tiene. */
    /** Descripción: Ejecuta la operación «tryAcquire» correspondiente a la responsabilidad de Hold. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ Hold tryAcquire() {
        if(!jvmLock.tryLock()) return null;
        try {
            Hold hold = openFileLock(false, jvmLock);
            if(hold == null) jvmLock.unlock();
            return hold;
        } catch(RuntimeException exception) { jvmLock.unlock(); throw exception; }
    }

    /** Descripción: Crea o prepara el resultado de «openFileLock» dentro de Hold. Entradas: blocking: dato de entrada necesario para la operación; owner: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Hold openFileLock(boolean blocking, ReentrantLock owner) {
        try {
            Files.createDirectories(directory);
            FileChannel channel = FileChannel.open(directory.resolve("import.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock;
            try { lock = blocking ? channel.lock() : channel.tryLock(); }
            catch(IOException | RuntimeException exception) { channel.close(); throw exception; }
            if(lock == null) { channel.close(); return null; }
            return new Hold(channel, lock, owner);
        } catch(IOException exception) {
            throw new ExternalImportJournalException("The external import journal directory is not available: " + directory, exception);
        }
    }

    /** Persiste el diario de forma durable. Un fallo se propaga: la operación no puede seguir sin diario. */
    /** Descripción: Actualiza el estado gestionado por Hold mediante la operación «save». Entradas: journal: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void save(ExternalImportJournal journal) {
        Path target = path(journal.operationId());
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            byte[] bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(journal);
            temporary = Files.createTempFile(directory, "." + journal.operationId() + "-", ".tmp");
            try(FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while(buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            AtomicFiles.moveReplacing(temporary, target);
        } catch(IOException | RuntimeException exception) {
            throw new ExternalImportJournalException("Could not persist the external import journal " + journal.operationId(), exception);
        } finally {
            if(temporary != null) try { Files.deleteIfExists(temporary); } catch(IOException ignored) { /* the temporary is hidden and harmless */ }
        }
    }

    /** Lee un diario; vacío si no existe. */
    /** Descripción: Obtiene la información correspondiente a «load» dentro de Hold. Entradas: operationId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ Optional<ExternalImportJournal> load(String operationId) {
        Path source = path(operationId);
        if(!Files.isRegularFile(source)) return Optional.empty();
        try { return Optional.of(mapper.readValue(BoundedIo.readInternal(source), ExternalImportJournal.class)); }
        catch(IOException | RuntimeException exception) {
            throw new ExternalImportJournalException("The external import journal " + operationId + " cannot be read", exception);
        }
    }

    /** Identificadores de los diarios presentes (sin ficheros temporales ni el cerrojo). */
    /** Descripción: Ejecuta la operación «operationIds» correspondiente a la responsabilidad de Hold. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ List<String> operationIds() {
        if(!Files.isDirectory(directory)) return List.of();
        try(var stream = Files.list(directory)) {
            List<String> ids = new ArrayList<>();
            stream.map(path -> path.getFileName().toString()).filter(name -> name.startsWith("imp-") && name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - ".json".length())).sorted().forEach(ids::add);
            return ids;
        } catch(IOException exception) {
            throw new ExternalImportJournalException("The external import journal directory cannot be listed: " + directory, exception);
        }
    }

    /** Borra el diario de una operación terminada; idempotente. */
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: operationId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void delete(String operationId) {
        try { Files.deleteIfExists(path(operationId)); }
        catch(IOException exception) { throw new ExternalImportJournalException("Could not delete the external import journal " + operationId, exception); }
    }

    /** Descripción: Ejecuta la operación «path» correspondiente a la responsabilidad de Hold. Entradas: operationId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Path path(String operationId) {
        if(operationId == null || !operationId.matches("imp-[A-Za-z0-9-]{8,64}")) throw new ExternalImportJournalException("Invalid import operation id");
        return directory.resolve(operationId + ".json");
    }

    /** Exclusividad sobre las importaciones; se libera al cerrar. */
    static final class Hold implements AutoCloseable {
        private final FileChannel channel;
        private final FileLock lock;
        private final ReentrantLock owner;
        private boolean closed;

        /** Descripción: Inicializa una instancia de Hold con sus dependencias y datos de entrada. Entradas: channel: dato de entrada necesario para la operación; lock: dato de entrada necesario para la operación; owner: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private Hold(FileChannel channel, FileLock lock, ReentrantLock owner) { this.channel = channel; this.lock = lock; this.owner = owner; }

        @Override public synchronized void close() {
            if(closed) return;
            closed = true;
            try { lock.release(); } catch(IOException ignored) { /* closing the channel releases it anyway */ }
            try { channel.close(); } catch(IOException ignored) { /* nothing else to release */ }
            owner.unlock();
        }
    }
}
