package com.example.editor.metamodel.target;

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
import com.example.editor.io.BoundedIo;
import com.example.editor.metamodel.canonical.CanonicalMetamodelRepositoryException;
import com.example.editor.metamodel.canonical.CanonicalMetamodelRepositoryException.Reason;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;

/**
 * Guarda cada configuración de destinos en un archivo independiente y atómico.
 *
 * Cada identificador utiliza su propio bloqueo. Si queda un archivo temporal
 * completo tras una interrupción, la siguiente lectura lo recupera antes de
 * devolver la configuración.
 */
@Repository
public class JsonTargetConfigurationRepository implements TargetConfigurationRepository {
    private static final String SUFFIX = ".targets.json";

    private final StorageProperties storageProperties;
    private final ObjectMapper objectMapper;
    private final ObjectWriter writer;
    private final StripedKeyLockRegistry locks = new StripedKeyLockRegistry(128);

    /** Descripción: Inicializa una instancia de JsonTargetConfigurationRepository con sus dependencias y datos de entrada. Entradas: storageProperties: configuración que gobierna el comportamiento; objectMapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public JsonTargetConfigurationRepository(StorageProperties storageProperties, ObjectMapper objectMapper) {
        this.storageProperties = storageProperties;
        this.objectMapper = objectMapper;
        this.writer = objectMapper.writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    @Override
    /** Descripción: Obtiene la información correspondiente a «findByMetamodelId» dentro de JsonTargetConfigurationRepository. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<TargetConfigurationDocument> findByMetamodelId(String metamodelId) {
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
    /** Descripción: Actualiza el estado gestionado por JsonTargetConfigurationRepository mediante la operación «save». Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TargetConfigurationDocument save(TargetConfigurationDocument document) {
        if (document == null) {
            throw new CanonicalMetamodelRepositoryException(Reason.INVALID_REQUEST, "Target configuration must not be null");
        }

        validateId(document.metamodelId());
        ReentrantLock lock = lockFor(document.metamodelId());
        lock.lock();

        try {
            Path destination = resolve(document.metamodelId());
            Path temporary = temporaryFor(destination);
            Files.createDirectories(destination.getParent());
            Files.write(temporary, writer.writeValueAsBytes(document), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            move(temporary, destination);
            return document;
        } catch (JacksonException exception) {
            throw new CanonicalMetamodelRepositoryException(Reason.STORAGE_ERROR, "Could not serialize target configuration: " + document.metamodelId(), exception);
        } catch (IOException exception) {
            throw new CanonicalMetamodelRepositoryException(Reason.STORAGE_ERROR, "Could not store target configuration: " + document.metamodelId(), exception);
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
            throw new CanonicalMetamodelRepositoryException(Reason.STORAGE_ERROR, "Could not delete target configuration: " + metamodelId, exception);
        } finally {
            unlock(lock);
        }
    }

    /** Descripción: Obtiene la información correspondiente a «read» dentro de JsonTargetConfigurationRepository. Entradas: path: ruta o ubicación utilizada por la operación; expectedId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private TargetConfigurationDocument read(Path path, String expectedId) {
        try {
            TargetConfigurationDocument result = objectMapper.readValue(BoundedIo.readInternal(path), TargetConfigurationDocument.class);

            if (!expectedId.equals(result.metamodelId())) {
                throw new CanonicalMetamodelRepositoryException(Reason.STORAGE_ERROR, "Target configuration id does not match its file name: " + expectedId);
            }

            return result;
        } catch (CanonicalMetamodelRepositoryException exception) {
            throw exception;
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new CanonicalMetamodelRepositoryException(Reason.STORAGE_ERROR, "Could not parse target configuration: " + path, exception);
        } catch (IOException exception) {
            throw new CanonicalMetamodelRepositoryException(Reason.STORAGE_ERROR, "Could not read target configuration: " + path, exception);
        }
    }

    /** Descripción: Ejecuta la operación «recoverInterruptedWrite» correspondiente a la responsabilidad de JsonTargetConfigurationRepository. Entradas: id: identificador que delimita el recurso objetivo; destination: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void recoverInterruptedWrite(String id, Path destination) {
        Path temporary = temporaryFor(destination);

        if (!Files.isRegularFile(temporary)) {
            return;
        }

        if (Files.isRegularFile(destination)) {
            deleteTemporary(temporary);
            return;
        }

        try {
            TargetConfigurationDocument recovered = objectMapper.readValue(BoundedIo.readInternal(temporary), TargetConfigurationDocument.class);

            if (!id.equals(recovered.metamodelId())) {
                deleteTemporary(temporary);
                return;
            }

            move(temporary, destination);
        } catch (JacksonException | IllegalArgumentException exception) {
            deleteTemporary(temporary);
        } catch (IOException exception) {
            throw new CanonicalMetamodelRepositoryException(Reason.STORAGE_ERROR, "Could not recover interrupted target configuration write: " + id, exception);
        }
    }

    /** Descripción: Obtiene la información correspondiente a «resolve» dentro de JsonTargetConfigurationRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Path resolve(String id) {
        Path directory = storageProperties.metamodels().toAbsolutePath().normalize();
        Path result = directory.resolve(id + SUFFIX).normalize();

        if (!result.startsWith(directory)) {
            throw new CanonicalMetamodelRepositoryException(Reason.INVALID_REQUEST, "Target configuration path is outside the storage directory");
        }

        return result;
    }

    /** Descripción: Ejecuta la operación «temporaryFor» correspondiente a la responsabilidad de JsonTargetConfigurationRepository. Entradas: destination: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Path temporaryFor(Path destination) {
        return destination.resolveSibling(destination.getFileName() + ".tmp");
    }

    /** Descripción: Valida las condiciones necesarias para «validateId» y rechaza entradas no válidas. Entradas: id: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateId(String id) {
        try { MetamodelIdPolicy.require(id); }
        catch(IllegalArgumentException exception) { throw new CanonicalMetamodelRepositoryException(Reason.INVALID_REQUEST, exception.getMessage(), exception); }
    }

    /** Descripción: Ejecuta la operación «lockFor» correspondiente a la responsabilidad de JsonTargetConfigurationRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private ReentrantLock lockFor(String id) {
        return locks.lockFor(id);
    }

    /** Descripción: Ejecuta la operación «unlock» correspondiente a la responsabilidad de JsonTargetConfigurationRepository. Entradas: lock: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void unlock(ReentrantLock lock) {
        lock.unlock();
    }

    /** Descripción: Ejecuta la operación «move» correspondiente a la responsabilidad de JsonTargetConfigurationRepository. Entradas: source: dato de entrada necesario para la operación; destination: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void move(Path source, Path destination) throws IOException {
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
