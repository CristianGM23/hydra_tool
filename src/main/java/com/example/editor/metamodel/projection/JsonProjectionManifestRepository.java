package com.example.editor.metamodel.projection;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import com.example.editor.metamodel.MetamodelIdPolicy;
import com.example.editor.runtime.StripedKeyLockRegistry;

import org.springframework.stereotype.Repository;

import com.example.editor.config.StorageProperties;
import com.example.editor.metamodel.canonical.CanonicalMetamodelRepositoryException;
import com.example.editor.metamodel.canonical.CanonicalMetamodelRepositoryException.Reason;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;

/**
 * Guarda el manifest de proyecciones de forma atómica junto a los metamodelos.
 * Las lecturas, escrituras y eliminaciones del mismo identificador se serializan
 * para no perder estados cuando coinciden un guardado y una regeneración manual.
 */
@Repository
public class JsonProjectionManifestRepository implements ProjectionManifestRepository {
    private static final String SUFFIX = ".projections.json";

    private final StorageProperties storageProperties;
    private final ObjectMapper objectMapper;
    private final ObjectWriter writer;
    private final StripedKeyLockRegistry locks = new StripedKeyLockRegistry(128);

    /** Descripción: Inicializa una instancia de JsonProjectionManifestRepository con sus dependencias y datos de entrada. Entradas: storageProperties: configuración que gobierna el comportamiento; objectMapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public JsonProjectionManifestRepository(StorageProperties storageProperties, ObjectMapper objectMapper) {
        this.storageProperties = storageProperties;
        this.objectMapper = objectMapper;
        this.writer = objectMapper.writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    @Override
    /** Descripción: Obtiene la información correspondiente a «findByMetamodelId» dentro de JsonProjectionManifestRepository. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<ProjectionManifest> findByMetamodelId(String metamodelId) {
        validateId(metamodelId);
        ReentrantLock lock = lockFor(metamodelId);
        lock.lock();

        try {
            Path path = resolve(metamodelId);
            recoverInterruptedWrite(metamodelId, path);

            if (!Files.isRegularFile(path)) {
                return Optional.empty();
            }

            return Optional.of(read(path, metamodelId));
        } finally {
            unlock(lock);
        }
    }

    @Override
    /** Descripción: Actualiza el estado gestionado por JsonProjectionManifestRepository mediante la operación «save». Entradas: manifest: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ProjectionManifest save(ProjectionManifest manifest) {
        if (manifest == null) {
            throw new CanonicalMetamodelRepositoryException(Reason.INVALID_REQUEST, "Projection manifest must not be null");
        }

        validateId(manifest.metamodelId());
        ReentrantLock lock = lockFor(manifest.metamodelId());
        lock.lock();

        try {
            Path destination = resolve(manifest.metamodelId());
            Path temporary = temporaryFor(destination);
            Files.createDirectories(destination.getParent());
            Files.write(temporary, writer.writeValueAsBytes(manifest), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            move(temporary, destination);
            return manifest;
        } catch (JacksonException exception) {
            throw new CanonicalMetamodelRepositoryException(Reason.STORAGE_ERROR, "Could not serialize projection manifest: " + manifest.metamodelId(), exception);
        } catch (IOException exception) {
            throw new CanonicalMetamodelRepositoryException(Reason.STORAGE_ERROR, "Could not store projection manifest: " + manifest.metamodelId(), exception);
        } finally {
            unlock(lock);
        }
    }

    @Override
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void delete(String metamodelId) {
        validateId(metamodelId);
        ReentrantLock lock = lockFor(metamodelId);
        lock.lock();

        try {
            Path destination = resolve(metamodelId);
            Files.deleteIfExists(destination);
            Files.deleteIfExists(temporaryFor(destination));
        } catch (IOException exception) {
            throw new CanonicalMetamodelRepositoryException(Reason.STORAGE_ERROR, "Could not delete projection manifest: " + metamodelId, exception);
        } finally {
            unlock(lock);
        }
    }

    /** Descripción: Obtiene la información correspondiente a «read» dentro de JsonProjectionManifestRepository. Entradas: path: ruta o ubicación utilizada por la operación; expectedId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private ProjectionManifest read(Path path, String expectedId) {
        try {
            ProjectionManifest result = objectMapper.readValue(com.example.editor.io.BoundedIo.readInternal(path), ProjectionManifest.class);

            if (!expectedId.equals(result.metamodelId())) {
                throw new CanonicalMetamodelRepositoryException(Reason.STORAGE_ERROR, "Projection manifest id does not match its file name: " + expectedId);
            }

            return result;
        } catch (CanonicalMetamodelRepositoryException exception) {
            throw exception;
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new CanonicalMetamodelRepositoryException(Reason.STORAGE_ERROR, "Could not parse projection manifest: " + path, exception);
        } catch (IOException exception) {
            throw new CanonicalMetamodelRepositoryException(Reason.STORAGE_ERROR, "Could not read projection manifest: " + path, exception);
        }
    }

    /** Descripción: Ejecuta la operación «recoverInterruptedWrite» correspondiente a la responsabilidad de JsonProjectionManifestRepository. Entradas: id: identificador que delimita el recurso objetivo; destination: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void recoverInterruptedWrite(String id, Path destination) {
        Path temporary = temporaryFor(destination);

        if (!Files.isRegularFile(temporary)) {
            return;
        }

        if (Files.isRegularFile(destination)) {
            deleteTemporary(temporary);
            return;
        }

        try {
            ProjectionManifest recovered = objectMapper.readValue(com.example.editor.io.BoundedIo.readInternal(temporary), ProjectionManifest.class);

            if (!id.equals(recovered.metamodelId())) {
                deleteTemporary(temporary);
                return;
            }

            move(temporary, destination);
        } catch (JacksonException | IllegalArgumentException exception) {
            deleteTemporary(temporary);
        } catch (IOException exception) {
            throw new CanonicalMetamodelRepositoryException(Reason.STORAGE_ERROR, "Could not recover interrupted projection manifest write: " + id, exception);
        }
    }

    /** Descripción: Obtiene la información correspondiente a «resolve» dentro de JsonProjectionManifestRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Path resolve(String id) {
        Path directory = storageProperties.metamodels().toAbsolutePath().normalize();
        Path path = directory.resolve(id + SUFFIX).normalize();

        if (!path.startsWith(directory)) {
            throw new CanonicalMetamodelRepositoryException(Reason.INVALID_REQUEST, "Projection manifest path is outside the storage directory");
        }

        return path;
    }

    /** Descripción: Ejecuta la operación «temporaryFor» correspondiente a la responsabilidad de JsonProjectionManifestRepository. Entradas: destination: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Path temporaryFor(Path destination) {
        return destination.resolveSibling(destination.getFileName() + ".tmp");
    }

    /** Descripción: Valida las condiciones necesarias para «validateId» y rechaza entradas no válidas. Entradas: id: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateId(String id) {
        try { MetamodelIdPolicy.require(id); }
        catch(IllegalArgumentException exception) { throw new CanonicalMetamodelRepositoryException(Reason.INVALID_REQUEST, exception.getMessage(), exception); }
    }

    /** Descripción: Ejecuta la operación «lockFor» correspondiente a la responsabilidad de JsonProjectionManifestRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private ReentrantLock lockFor(String id) {
        return locks.lockFor(id);
    }

    /** Descripción: Ejecuta la operación «unlock» correspondiente a la responsabilidad de JsonProjectionManifestRepository. Entradas: lock: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void unlock(ReentrantLock lock) {
        lock.unlock();
    }

    /** Descripción: Ejecuta la operación «move» correspondiente a la responsabilidad de JsonProjectionManifestRepository. Entradas: source: dato de entrada necesario para la operación; destination: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void move(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Descripción: Elimina o libera el recurso gestionado por «deleteTemporary» de forma controlada. Entradas: temporary: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void deleteTemporary(Path temporary) {
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException exception) {
            // The original recovery result has priority.
        }
    }
}
