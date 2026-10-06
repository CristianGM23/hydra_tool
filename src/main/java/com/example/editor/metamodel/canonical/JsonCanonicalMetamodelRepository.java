package com.example.editor.metamodel.canonical;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import com.example.editor.metamodel.MetamodelIdPolicy;
import com.example.editor.runtime.StripedKeyLockRegistry;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.example.editor.config.StorageProperties;
import com.example.editor.audit.AuditActions;
import com.example.editor.audit.AuditEvent;
import com.example.editor.audit.AuditEventPublisher;
import com.example.editor.audit.AuditResource;
import com.example.editor.audit.AuditRequestContext;
import com.example.editor.audit.NoopAuditEventPublisher;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.canonical.CanonicalMetamodelRepositoryException.Reason;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.example.editor.metamodel.target.JsonTargetConfigurationRepository;
import com.example.editor.metamodel.target.TargetConfigurationRepository;

/**
 * Guarda MetaModel como JSON y calcula un ETag determinista a partir del contenido.
 *
 * Cada identificador utiliza su propio bloqueo. Dos metamodelos distintos pueden
 * guardarse al mismo tiempo sin depender de un synchronized global.
 */
@Repository
public class JsonCanonicalMetamodelRepository implements CanonicalMetamodelRepository {

    private final StorageProperties storageProperties;
    private final CanonicalMetamodelProperties properties;
    private final ObjectMapper objectMapper;
    private final CanonicalMetamodelJsonCodec codec;
    private final TargetConfigurationRepository targetConfigurationRepository;
    private final AuditEventPublisher auditPublisher;
    private final StripedKeyLockRegistry locks = new StripedKeyLockRegistry(128);

    @Autowired
    /** Descripción: Inicializa una instancia de JsonCanonicalMetamodelRepository con sus dependencias y datos de entrada. Entradas: storageProperties: configuración que gobierna el comportamiento; properties: configuración que gobierna el comportamiento; objectMapper: componente de conversión utilizado durante el procesamiento; codec: dato de entrada necesario para la operación; targetConfigurationRepository: configuración que gobierna el comportamiento; auditPublisher: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public JsonCanonicalMetamodelRepository(StorageProperties storageProperties, CanonicalMetamodelProperties properties, ObjectMapper objectMapper, CanonicalMetamodelJsonCodec codec, TargetConfigurationRepository targetConfigurationRepository, AuditEventPublisher auditPublisher) {
        this.storageProperties = storageProperties;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.codec = codec;
        this.targetConfigurationRepository = targetConfigurationRepository;
        this.auditPublisher = auditPublisher == null ? NoopAuditEventPublisher.INSTANCE : auditPublisher;
    }

    /** Descripción: Inicializa una instancia de JsonCanonicalMetamodelRepository con sus dependencias y datos de entrada. Entradas: storageProperties: configuración que gobierna el comportamiento; properties: configuración que gobierna el comportamiento; objectMapper: componente de conversión utilizado durante el procesamiento; codec: dato de entrada necesario para la operación; targetConfigurationRepository: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public JsonCanonicalMetamodelRepository(StorageProperties storageProperties, CanonicalMetamodelProperties properties, ObjectMapper objectMapper, CanonicalMetamodelJsonCodec codec, TargetConfigurationRepository targetConfigurationRepository) {
        this(storageProperties, properties, objectMapper, codec, targetConfigurationRepository, NoopAuditEventPublisher.INSTANCE);
    }

    /** Constructor abreviado para pruebas y embebido local con el contrato actual. */
    /** Descripción: Inicializa una instancia de JsonCanonicalMetamodelRepository con sus dependencias y datos de entrada. Entradas: storageProperties: configuración que gobierna el comportamiento; properties: configuración que gobierna el comportamiento; objectMapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public JsonCanonicalMetamodelRepository(StorageProperties storageProperties, CanonicalMetamodelProperties properties, ObjectMapper objectMapper) {
        this(storageProperties, properties, objectMapper, new CanonicalMetamodelJsonCodec(objectMapper), new JsonTargetConfigurationRepository(storageProperties, objectMapper));
    }

    @Override
    /** Descripción: Crea o prepara el resultado de «create» dentro de JsonCanonicalMetamodelRepository. Entradas: metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public StoredMetamodel create(MetaModel metamodel) {
        requireEnabled();
        requireMetamodel(metamodel);
        String id = metamodel.id();
        ReentrantLock lock = lockFor(id);
        lock.lock();

        try {
            Path destination = resolve(id);
            recoverInterruptedWrite(id, destination);

            if (Files.isRegularFile(destination)) {
                throw new CanonicalMetamodelRepositoryException(Reason.CONFLICT, "Canonical metamodel already exists: " + id);
            }

            StoredMetamodel stored = write(destination, metamodel);
            audit(AuditActions.CREATE, "metamodel.created", null, stored.metamodel());
            return stored;
        } finally {
            unlock(lock);
        }
    }

    @Override
    /** Descripción: Actualiza el estado gestionado por JsonCanonicalMetamodelRepository mediante la operación «replace». Entradas: id: identificador que delimita el recurso objetivo; metamodel: dato de entrada necesario para la operación; expectedEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public StoredMetamodel replace(String id, MetaModel metamodel, String expectedEtag) {
        requireEnabled();
        validateId(id);
        requireMetamodel(metamodel);

        if (!id.equals(metamodel.id())) {
            throw new CanonicalMetamodelRepositoryException(Reason.INVALID_REQUEST, "Path id and metamodel id must match");
        }

        ReentrantLock lock = lockFor(id);
        lock.lock();

        try {
            Path destination = resolve(id);
            recoverInterruptedWrite(id, destination);

            if (!Files.isRegularFile(destination)) {
                throw new CanonicalMetamodelRepositoryException(Reason.NOT_FOUND, "Canonical metamodel was not found: " + id);
            }

            StoredMetamodel current = read(destination, id);
            verifyEtag(expectedEtag, current.etag());
            StoredMetamodel stored = write(destination, metamodel);
            audit(AuditActions.UPDATE, "metamodel.updated", current.metamodel(), stored.metamodel());
            return stored;
        } finally {
            unlock(lock);
        }
    }

    @Override
    /** Descripción: Obtiene la información correspondiente a «findById» dentro de JsonCanonicalMetamodelRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<StoredMetamodel> findById(String id) {
        requireEnabled();
        validateId(id);
        ReentrantLock lock = lockFor(id);
        lock.lock();

        try {
            Path destination = resolve(id);
            recoverInterruptedWrite(id, destination);

            if (!Files.isRegularFile(destination)) {
                return Optional.empty();
            }

            return Optional.of(read(destination, id));
        } finally {
            unlock(lock);
        }
    }

    @Override
    /** Descripción: Obtiene la información correspondiente a «findAll» dentro de JsonCanonicalMetamodelRepository. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<CanonicalMetamodelSummary> findAll() {
        requireEnabled();
        Path directory = storageProperties.metamodels().toAbsolutePath().normalize();

        if (!Files.isDirectory(directory)) {
            return List.of();
        }

        String suffix = validatedSuffix();

        try (var stream = Files.list(directory)) {
            return stream
                .filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .filter(name -> name.endsWith(suffix))
                .filter(name -> !name.endsWith(suffix + ".tmp"))
                .map(name -> name.substring(0, name.length() - suffix.length()))
                .sorted()
                .map(this::requiredStored)
                .map(stored -> new CanonicalMetamodelSummary(stored.metamodel().id(), stored.metamodel().name(), stored.metamodel().schemaVersion(), stored.etag()))
                .toList();
        } catch (IOException exception) {
            throw new CanonicalMetamodelRepositoryException(Reason.STORAGE_ERROR, "Could not list canonical metamodels: " + directory, exception);
        }
    }

    @Override
    /** Descripción: Evalúa la condición «exists» sin modificar el estado externo. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean exists(String id) {
        return findById(id).isPresent();
    }

    @Override
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo; expectedEtag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void delete(String id, String expectedEtag) {
        requireEnabled();
        validateId(id);
        ReentrantLock lock = lockFor(id);
        lock.lock();

        try {
            Path destination = resolve(id);
            recoverInterruptedWrite(id, destination);

            if (!Files.isRegularFile(destination)) {
                throw new CanonicalMetamodelRepositoryException(Reason.NOT_FOUND, "Canonical metamodel was not found: " + id);
            }

            StoredMetamodel current = read(destination, id);
            verifyEtag(expectedEtag, current.etag());
            Files.delete(destination);
            Files.deleteIfExists(temporaryFor(destination));
            audit(AuditActions.DELETE, "metamodel.deleted", current.metamodel(), null);
        } catch (IOException exception) {
            throw new CanonicalMetamodelRepositoryException(Reason.STORAGE_ERROR, "Could not delete canonical metamodel: " + id, exception);
        } finally {
            unlock(lock);
        }
    }

    /** Persiste únicamente el contrato canónico actual. */
    /** Descripción: Actualiza el estado gestionado por JsonCanonicalMetamodelRepository mediante la operación «write». Entradas: destination: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private StoredMetamodel write(Path destination, MetaModel metamodel) {
        if (!MetaModel.CURRENT_SCHEMA_VERSION.equals(metamodel.schemaVersion())) {
            throw new CanonicalMetamodelRepositoryException(Reason.INVALID_REQUEST,
                    "Unsupported MetaModel schema version: " + metamodel.schemaVersion());
        }
        return writeCanonical(destination, metamodel);
    }

    /** Descripción: Actualiza el estado gestionado por JsonCanonicalMetamodelRepository mediante la operación «writeCanonical». Entradas: destination: dato de entrada necesario para la operación; canonical: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private StoredMetamodel writeCanonical(Path destination, MetaModel canonical) {
        Path temporary = temporaryFor(destination);

        try {
            Files.createDirectories(destination.getParent());
            byte[] content = canonicalBytes(canonical);
            Files.write(temporary, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            moveAtomically(temporary, destination);
            return new StoredMetamodel(canonical, etag(content), destination);
        } catch (JacksonException exception) {
            deleteTemporary(temporary);
            throw new CanonicalMetamodelRepositoryException(Reason.STORAGE_ERROR, "Could not serialize canonical metamodel: " + canonical.id(), exception);
        } catch (IOException exception) {
            deleteTemporary(temporary);
            throw new CanonicalMetamodelRepositoryException(Reason.STORAGE_ERROR, "Could not store canonical metamodel: " + destination, exception);
        }
    }

    /** Lee un documento canónico sin migraciones implícitas. */
    /** Descripción: Obtiene la información correspondiente a «read» dentro de JsonCanonicalMetamodelRepository. Entradas: path: ruta o ubicación utilizada por la operación; expectedId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private StoredMetamodel read(Path path, String expectedId) {
        try {
            byte[] content = com.example.editor.io.BoundedIo.readInternal(path);
            MetaModel metamodel = codec.read(content);
            if (!expectedId.equals(metamodel.id())) {
                throw new CanonicalMetamodelRepositoryException(Reason.STORAGE_ERROR,
                        "Canonical metamodel id does not match its file name: " + expectedId);
            }
            byte[] canonicalContent = canonicalBytes(metamodel);
            return new StoredMetamodel(metamodel, etag(canonicalContent), path);
        } catch (CanonicalMetamodelRepositoryException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new CanonicalMetamodelRepositoryException(Reason.INVALID_REQUEST, exception.getMessage(), exception);
        } catch (JacksonException exception) {
            throw new CanonicalMetamodelRepositoryException(Reason.STORAGE_ERROR,
                    "Could not parse canonical metamodel: " + path, exception);
        } catch (IOException exception) {
            throw new CanonicalMetamodelRepositoryException(Reason.STORAGE_ERROR,
                    "Could not read canonical metamodel: " + path, exception);
        }
    }

    /** Recupera solo escrituras temporales que ya cumplen el contrato actual. */
    /** Descripción: Ejecuta la operación «recoverInterruptedWrite» correspondiente a la responsabilidad de JsonCanonicalMetamodelRepository. Entradas: id: identificador que delimita el recurso objetivo; destination: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void recoverInterruptedWrite(String id, Path destination) {
        Path temporary = temporaryFor(destination);
        if (!Files.isRegularFile(temporary)) return;
        if (Files.isRegularFile(destination)) {
            deleteTemporary(temporary);
            return;
        }
        try {
            byte[] content = com.example.editor.io.BoundedIo.readInternal(temporary);
            MetaModel recovered = codec.read(content);
            if (!id.equals(recovered.id())) {
                deleteTemporary(temporary);
                return;
            }
            moveAtomically(temporary, destination);
        } catch (JacksonException | IllegalArgumentException exception) {
            deleteTemporary(temporary);
        } catch (IOException exception) {
            throw new CanonicalMetamodelRepositoryException(Reason.STORAGE_ERROR,
                    "Could not recover interrupted canonical write: " + id, exception);
        }
    }

    /** Descripción: Evalúa la condición «canonicalBytes» sin modificar el estado externo. Entradas: metamodel: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private byte[] canonicalBytes(MetaModel metamodel) throws JacksonException {
        return codec.write(metamodel);
    }

    /** Descripción: Ejecuta la operación «etag» correspondiente a la responsabilidad de JsonCanonicalMetamodelRepository. Entradas: canonicalContent: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String etag(byte[] canonicalContent) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonicalContent);
            return "\"" + HexFormat.of().formatHex(digest) + "\"";
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    /** Descripción: Valida las condiciones necesarias para «verifyEtag» y rechaza entradas no válidas. Entradas: expectedEtag: dato de entrada necesario para la operación; currentEtag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void verifyEtag(String expectedEtag, String currentEtag) {
        if (!properties.isUseEtag()) {
            return;
        }

        if (expectedEtag == null || expectedEtag.isBlank()) {
            throw new CanonicalMetamodelRepositoryException(Reason.PRECONDITION_FAILED, "If-Match header is required");
        }

        if (!"*".equals(expectedEtag.trim()) && !currentEtag.equals(expectedEtag.trim())) {
            throw new CanonicalMetamodelRepositoryException(Reason.PRECONDITION_FAILED, "Canonical metamodel has changed since it was loaded");
        }
    }

    /** Descripción: Valida las condiciones necesarias para «requiredStored» y rechaza entradas no válidas. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private StoredMetamodel requiredStored(String id) {
        return findById(id).orElseThrow(() -> new CanonicalMetamodelRepositoryException(Reason.NOT_FOUND, "Canonical metamodel was not found: " + id));
    }

    /** Descripción: Obtiene la información correspondiente a «resolve» dentro de JsonCanonicalMetamodelRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Path resolve(String id) {
        validateId(id);
        Path directory = storageProperties.metamodels().toAbsolutePath().normalize();
        Path result = directory.resolve(id + validatedSuffix()).normalize();

        if (!result.startsWith(directory)) {
            throw new CanonicalMetamodelRepositoryException(Reason.INVALID_REQUEST, "Canonical metamodel path is outside the storage directory");
        }

        return result;
    }

    /** Descripción: Valida las condiciones necesarias para «validatedSuffix» y rechaza entradas no válidas. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private String validatedSuffix() {
        String suffix = properties.getFileSuffix();

        if (suffix == null || suffix.isBlank() || suffix.contains("/") || suffix.contains("\\") || suffix.contains("..")) {
            throw new CanonicalMetamodelRepositoryException(Reason.INVALID_REQUEST, "Canonical metamodel file suffix is invalid");
        }

        return suffix;
    }

    /** Descripción: Ejecuta la operación «audit» correspondiente a la responsabilidad de JsonCanonicalMetamodelRepository. Entradas: action: dato de entrada necesario para la operación; activityType: dato de entrada necesario para la operación; before: dato de entrada necesario para la operación; after: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void audit(String action, String activityType, MetaModel before, MetaModel after) {
        MetaModel effective = after != null ? after : before;
        if(effective == null) return;
        try {
            AuditRequestContext.markBusinessMutation();
            auditPublisher.publish(AuditEvent.builder(activityType, "metamodel", action,
                    new AuditResource("metamodel", "metamodel", effective.id(), effective.name() == null || effective.name().isBlank() ? effective.id() : effective.name()))
                    .before(before).after(after).correlationId(AuditRequestContext.correlationId()).build());
        } catch(RuntimeException ignored) {
            // La auditoría nunca debe modificar el resultado de una operación canónica completada.
        }
    }

    /** Descripción: Valida las condiciones necesarias para «requireMetamodel» y rechaza entradas no válidas. Entradas: metamodel: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireMetamodel(MetaModel metamodel) {
        if (metamodel == null) {
            throw new CanonicalMetamodelRepositoryException(Reason.INVALID_REQUEST, "Canonical metamodel must not be null");
        }

        validateId(metamodel.id());
    }

    /** Descripción: Valida las condiciones necesarias para «validateId» y rechaza entradas no válidas. Entradas: id: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateId(String id) {
        try { MetamodelIdPolicy.require(id); }
        catch(IllegalArgumentException exception) { throw new CanonicalMetamodelRepositoryException(CanonicalMetamodelRepositoryException.Reason.INVALID_REQUEST, exception.getMessage(), exception); }
    }

    /** Descripción: Valida las condiciones necesarias para «requireEnabled» y rechaza entradas no válidas. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new CanonicalMetamodelRepositoryException(Reason.INVALID_REQUEST, "Canonical metamodel storage is disabled");
        }
    }

    /** Descripción: Ejecuta la operación «temporaryFor» correspondiente a la responsabilidad de JsonCanonicalMetamodelRepository. Entradas: destination: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Path temporaryFor(Path destination) {
        return destination.resolveSibling(destination.getFileName() + ".tmp");
    }

    /** Descripción: Ejecuta la operación «moveAtomically» correspondiente a la responsabilidad de JsonCanonicalMetamodelRepository. Entradas: source: dato de entrada necesario para la operación; destination: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void moveAtomically(Path source, Path destination) throws IOException {
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
            // El error principal tiene prioridad.
        }
    }

    /** Descripción: Ejecuta la operación «lockFor» correspondiente a la responsabilidad de JsonCanonicalMetamodelRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private ReentrantLock lockFor(String id) {
        return locks.lockFor(id);
    }

    /** Descripción: Ejecuta la operación «unlock» correspondiente a la responsabilidad de JsonCanonicalMetamodelRepository. Entradas: lock: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void unlock(ReentrantLock lock) {
        lock.unlock();
    }
}
