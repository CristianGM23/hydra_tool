package com.example.editor.validation.persistence;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.FileAlreadyExistsException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import com.example.editor.runtime.StripedKeyLockRegistry;
import com.example.editor.io.AtomicFiles;
import com.example.editor.persistence.evolution.HydraSchemaEvolutionService;
import java.util.function.Function;

import com.example.editor.persistence.CanonicalDocumentMutation;
import com.example.editor.persistence.CanonicalDocumentMutationKind;
import com.example.editor.persistence.CanonicalDocumentMutationObserver;
import com.example.editor.persistence.CanonicalDocumentStore;
import com.example.editor.scope.ResourceScope;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.example.editor.validation.persistence.CanonicalDocumentStoreException.Reason;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;

/** Persistencia JSON atómica y con ETag para documentos canónicos auxiliares. */
public final class AtomicJsonDocumentStore<T> implements CanonicalDocumentStore<T> {
    private final Path directory;
    private final String suffix;
    private final boolean useEtag;
    private final ObjectMapper objectMapper;
    private final ObjectWriter writer;
    private final Class<T> type;
    private final Function<T, String> idExtractor;
    private final String namespace;
    private final ResourceScope scope;
    private final CanonicalDocumentMutationObserver mutationObserver;
    private final HydraSchemaEvolutionService schemaEvolution;
    private final StripedKeyLockRegistry locks = new StripedKeyLockRegistry(128);

    /** Descripción: Inicializa una instancia de AtomicJsonDocumentStore con sus dependencias y datos de entrada. Entradas: directory: ruta o ubicación utilizada por la operación; suffix: dato de entrada necesario para la operación; useEtag: dato de entrada necesario para la operación; objectMapper: componente de conversión utilizado durante el procesamiento; type: dato de entrada necesario para la operación; idExtractor: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public AtomicJsonDocumentStore(Path directory, String suffix, boolean useEtag, ObjectMapper objectMapper, Class<T> type, Function<T, String> idExtractor) {
        this(directory, suffix, useEtag, objectMapper, type, idExtractor, "canonical.document", ResourceScope.local(), CanonicalDocumentMutationObserver.NOOP, HydraSchemaEvolutionService.none());
    }

    /** Constructor de compatibilidad sin evolución de esquema registrada. */ /** Descripción: Inicializa una instancia de AtomicJsonDocumentStore con sus dependencias y datos de entrada. Entradas: directory: ruta o ubicación utilizada por la operación; suffix: dato de entrada necesario para la operación; useEtag: dato de entrada necesario para la operación; objectMapper: componente de conversión utilizado durante el procesamiento; type: dato de entrada necesario para la operación; idExtractor: dato de entrada necesario para la operación; namespace: dato de entrada necesario para la operación; scope: dato de entrada necesario para la operación; mutationObserver: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public AtomicJsonDocumentStore(Path directory, String suffix, boolean useEtag, ObjectMapper objectMapper, Class<T> type, Function<T, String> idExtractor,
            String namespace, ResourceScope scope, CanonicalDocumentMutationObserver mutationObserver) {
        this(directory, suffix, useEtag, objectMapper, type, idExtractor, namespace, scope, mutationObserver, HydraSchemaEvolutionService.none());
    }

    /** Descripción: Inicializa una instancia de AtomicJsonDocumentStore con sus dependencias y datos de entrada. Entradas: directory: ruta o ubicación utilizada por la operación; suffix: dato de entrada necesario para la operación; useEtag: dato de entrada necesario para la operación; objectMapper: componente de conversión utilizado durante el procesamiento; type: dato de entrada necesario para la operación; idExtractor: dato de entrada necesario para la operación; namespace: dato de entrada necesario para la operación; scope: dato de entrada necesario para la operación; mutationObserver: dato de entrada necesario para la operación; schemaEvolution: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public AtomicJsonDocumentStore(Path directory, String suffix, boolean useEtag, ObjectMapper objectMapper, Class<T> type, Function<T, String> idExtractor,
            String namespace, ResourceScope scope, CanonicalDocumentMutationObserver mutationObserver, HydraSchemaEvolutionService schemaEvolution) {
        this.directory = directory.toAbsolutePath().normalize();
        this.suffix = validateSuffix(suffix);
        this.useEtag = useEtag;
        this.objectMapper = objectMapper;
        this.writer = objectMapper.writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        this.type = type;
        this.idExtractor = idExtractor;
        this.namespace = namespace == null || namespace.isBlank() ? "canonical.document" : namespace.trim();
        this.scope = scope == null ? ResourceScope.local() : scope;
        this.mutationObserver = mutationObserver == null ? CanonicalDocumentMutationObserver.NOOP : mutationObserver;
        this.schemaEvolution = schemaEvolution == null ? HydraSchemaEvolutionService.none() : schemaEvolution;
    }

    /** Descripción: Crea o prepara el resultado de «create» dentro de AtomicJsonDocumentStore. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public StoredCanonicalDocument<T> create(T document) {
        String id = requireDocument(document);
        ReentrantLock lock = lock(id);
        lock.lock();
        try {
            Path destination = resolve(id);
            recover(id, destination);
            if(Files.isRegularFile(destination)) throw new CanonicalDocumentStoreException(Reason.CONFLICT, "Document already exists: " + id);
            StoredCanonicalDocument<T> stored = write(destination, document);
            notifyMutation(id, CanonicalDocumentMutationKind.CREATE, null, document);
            return stored;
        } finally { lock.unlock(); }
    }

    /** Descripción: Actualiza el estado gestionado por AtomicJsonDocumentStore mediante la operación «replace». Entradas: id: identificador que delimita el recurso objetivo; document: dato de entrada necesario para la operación; expectedEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public StoredCanonicalDocument<T> replace(String id, T document, String expectedEtag) {
        validateId(id);
        String documentId = requireDocument(document);
        if(!id.equals(documentId)) throw new CanonicalDocumentStoreException(Reason.INVALID_REQUEST, "Path id and document id must match");
        ReentrantLock lock = lock(id);
        lock.lock();
        try {
            Path destination = resolve(id);
            recover(id, destination);
            if(!Files.isRegularFile(destination)) throw new CanonicalDocumentStoreException(Reason.NOT_FOUND, "Document was not found: " + id);
            StoredCanonicalDocument<T> current = read(destination, id);
            verifyEtag(expectedEtag, current.etag());
            StoredCanonicalDocument<T> stored = write(destination, document);
            notifyMutation(id, CanonicalDocumentMutationKind.UPDATE, current.document(), document);
            return stored;
        } finally { lock.unlock(); }
    }

    /** Descripción: Obtiene la información correspondiente a «find» dentro de AtomicJsonDocumentStore. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<StoredCanonicalDocument<T>> find(String id) {
        validateId(id);
        ReentrantLock lock = lock(id);
        lock.lock();
        try {
            Path destination = resolve(id);
            recover(id, destination);
            return Files.isRegularFile(destination) ? Optional.of(read(destination, id)) : Optional.empty();
        } finally { lock.unlock(); }
    }

    /** Descripción: Obtiene la información correspondiente a «listIds» dentro de AtomicJsonDocumentStore. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<String> listIds() {
        if(!Files.isDirectory(directory)) return List.of();
        try(var stream = Files.list(directory)) {
            return stream.filter(Files::isRegularFile).map(path -> path.getFileName().toString()).filter(name -> name.endsWith(suffix))
                    .map(name -> name.substring(0, name.length() - suffix.length())).sorted().toList();
        } catch(IOException exception) {
            throw new CanonicalDocumentStoreException(Reason.STORAGE_ERROR, "Could not list documents: " + directory, exception);
        }
    }

    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo; expectedEtag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void delete(String id, String expectedEtag) {
        validateId(id);
        ReentrantLock lock = lock(id);
        lock.lock();
        try {
            Path destination = resolve(id);
            recover(id, destination);
            if(!Files.isRegularFile(destination)) throw new CanonicalDocumentStoreException(Reason.NOT_FOUND, "Document was not found: " + id);
            StoredCanonicalDocument<T> current = read(destination, id);
            verifyEtag(expectedEtag, current.etag());
            Files.delete(destination);
            Files.deleteIfExists(temporary(destination));
            notifyMutation(id, CanonicalDocumentMutationKind.DELETE, current.document(), null);
        } catch(IOException exception) {
            throw new CanonicalDocumentStoreException(Reason.STORAGE_ERROR, "Could not delete document: " + id, exception);
        } finally { lock.unlock(); }
    }

    /** Descripción: Elimina o libera el recurso gestionado por «deleteIfExists» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void deleteIfExists(String id) {
        validateId(id);
        ReentrantLock lock = lock(id);
        lock.lock();
        try {
            Path destination = resolve(id);
            Files.deleteIfExists(destination);
            Files.deleteIfExists(temporary(destination));
        } catch(IOException exception) {
            throw new CanonicalDocumentStoreException(Reason.STORAGE_ERROR, "Could not delete document: " + id, exception);
        } finally { lock.unlock(); }
    }

    /** Descripción: Evalúa la condición «exists» sin modificar el estado externo. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean exists(String id) { validateId(id); return Files.isRegularFile(resolve(id)); }
    /** Descripción: Ejecuta la operación «fileName» correspondiente a la responsabilidad de AtomicJsonDocumentStore. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public String fileName(String id) { validateId(id); return id + suffix; }

    /** Descripción: Actualiza el estado gestionado por AtomicJsonDocumentStore mediante la operación «write». Entradas: destination: dato de entrada necesario para la operación; document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private StoredCanonicalDocument<T> write(Path destination, T document) {
        Path temporary = temporary(destination);
        try {
            Files.createDirectories(directory);
            byte[] content = writer.writeValueAsBytes(document);
            Files.write(temporary, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            move(temporary, destination);
            // The ETag must represent the exact bytes that are now stored. Reading the
            // destination after the atomic move also protects the contract against any
            // external file-system filter that changes the persisted representation.
            byte[] persistedContent = com.example.editor.io.BoundedIo.readInternal(destination);
            return new StoredCanonicalDocument<>(document, etag(persistedContent), destination);
        } catch(JacksonException exception) {
            deleteTemporary(temporary);
            throw new CanonicalDocumentStoreException(Reason.STORAGE_ERROR, "Could not serialize document: " + idExtractor.apply(document), exception);
        } catch(IOException exception) {
            deleteTemporary(temporary);
            throw new CanonicalDocumentStoreException(Reason.STORAGE_ERROR, "Could not store document: " + destination, exception);
        }
    }

    /** Descripción: Obtiene la información correspondiente a «read» dentro de AtomicJsonDocumentStore. Entradas: path: ruta o ubicación utilizada por la operación; expectedId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private StoredCanonicalDocument<T> read(Path path, String expectedId) {
        try {
            byte[] content = com.example.editor.io.BoundedIo.readInternal(path);
            content = evolvePersistedDocument(path, content, true);
            T document = objectMapper.readValue(content, type);
            if(!expectedId.equals(idExtractor.apply(document))) throw new CanonicalDocumentStoreException(Reason.STORAGE_ERROR, "Document id does not match its file name: " + expectedId);
            return new StoredCanonicalDocument<>(document, etag(content), path);
        } catch(CanonicalDocumentStoreException exception) {
            throw exception;
        } catch(JacksonException | IllegalArgumentException exception) {
            throw new CanonicalDocumentStoreException(Reason.STORAGE_ERROR, "Could not parse document: " + path, exception);
        } catch(IOException exception) {
            throw new CanonicalDocumentStoreException(Reason.STORAGE_ERROR, "Could not read document: " + path, exception);
        }
    }

    /** Descripción: Ejecuta la operación «recover» correspondiente a la responsabilidad de AtomicJsonDocumentStore. Entradas: id: identificador que delimita el recurso objetivo; destination: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void recover(String id, Path destination) {
        Path temporary = temporary(destination);
        if(!Files.isRegularFile(temporary)) return;
        if(Files.isRegularFile(destination)) { deleteTemporary(temporary); return; }
        try {
            byte[] recoveredBytes = com.example.editor.io.BoundedIo.readInternal(temporary);
            recoveredBytes = evolvePersistedDocument(temporary, recoveredBytes, false);
            T recovered = objectMapper.readValue(recoveredBytes, type);
            if(!id.equals(idExtractor.apply(recovered))) { deleteTemporary(temporary); return; }
            move(temporary, destination);
        } catch(JacksonException | IllegalArgumentException exception) {
            deleteTemporary(temporary);
        } catch(IOException exception) {
            throw new CanonicalDocumentStoreException(Reason.STORAGE_ERROR, "Could not recover interrupted write: " + id, exception);
        }
    }

    /** Evoluciona documentos JSON de forma determinista y conserva una copia previa antes de publicar el nuevo esquema. */
    /** Descripción: Ejecuta la operación «evolvePersistedDocument» correspondiente a la responsabilidad de AtomicJsonDocumentStore. Entradas: path: ruta o ubicación utilizada por la operación; content: valor de entrada que debe procesarse; keepBackup: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] evolvePersistedDocument(Path path, byte[] content, boolean keepBackup) throws IOException {
        try {
            tools.jackson.databind.JsonNode source = objectMapper.readTree(content);
            String from = source != null && source.get("schemaVersion") != null && source.get("schemaVersion").isTextual()
                    ? source.get("schemaVersion").asString() : "unknown";
            HydraSchemaEvolutionService.EvolutionResult result = schemaEvolution.evolve(namespace, source);
            if (!result.changed()) return content;
            byte[] evolved = writer.writeValueAsBytes(result.document());
            if (keepBackup) writeSchemaEvolutionBackup(path, from, content);
            AtomicFiles.writeReplacing(path, evolved);
            return evolved;
        } catch (JacksonException exception) {
            throw new CanonicalDocumentStoreException(Reason.STORAGE_ERROR, "Could not evolve document schema: " + path, exception);
        }
    }

    /** Descripción: Actualiza el estado gestionado por AtomicJsonDocumentStore mediante la operación «writeSchemaEvolutionBackup». Entradas: path: ruta o ubicación utilizada por la operación; version: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void writeSchemaEvolutionBackup(Path path, String version, byte[] content) throws IOException {
        String safeVersion = version == null ? "unknown" : version.replaceAll("[^A-Za-z0-9._-]", "_");
        Path backup = path.resolveSibling(path.getFileName() + ".schema-" + safeVersion + ".bak");
        try { Files.write(backup, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE); }
        catch (FileAlreadyExistsException ignored) { /* The first pre-schema evolution snapshot remains authoritative. */ }
    }

    /** Descripción: Valida las condiciones necesarias para «verifyEtag» y rechaza entradas no válidas. Entradas: expected: dato de entrada necesario para la operación; current: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void verifyEtag(String expected, String current) {
        if(!useEtag) return;
        if(expected == null || expected.isBlank()) throw new CanonicalDocumentStoreException(Reason.PRECONDITION_FAILED, "If-Match header is required");
        if(!"*".equals(expected.trim()) && !current.equals(expected.trim())) throw new CanonicalDocumentStoreException(Reason.PRECONDITION_FAILED, "Document has changed since it was loaded");
    }


    /** Descripción: Ejecuta la operación «notifyMutation» correspondiente a la responsabilidad de AtomicJsonDocumentStore. Entradas: id: identificador que delimita el recurso objetivo; kind: dato de entrada necesario para la operación; before: dato de entrada necesario para la operación; after: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void notifyMutation(String id, CanonicalDocumentMutationKind kind, Object before, Object after) {
        try { mutationObserver.onMutation(new CanonicalDocumentMutation(namespace, scope, id, kind, before, after)); }
        catch(RuntimeException ignored) { /* Audit/observers must never invalidate a successful canonical write. */ }
    }

    /** Descripción: Valida las condiciones necesarias para «requireDocument» y rechaza entradas no válidas. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String requireDocument(T document) {
        if(document == null) throw new CanonicalDocumentStoreException(Reason.INVALID_REQUEST, "Document must not be null");
        String id = idExtractor.apply(document);
        validateId(id);
        return id;
    }
    /** Descripción: Ejecuta la operación «lock» correspondiente a la responsabilidad de AtomicJsonDocumentStore. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private ReentrantLock lock(String id) { return locks.lockFor(id); }
    /** Descripción: Obtiene la información correspondiente a «resolve» dentro de AtomicJsonDocumentStore. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Path resolve(String id) {
        validateId(id);
        Path path = directory.resolve(id + suffix).normalize();
        if(!path.startsWith(directory)) throw new CanonicalDocumentStoreException(Reason.INVALID_REQUEST, "Document path is outside the configured directory");
        return path;
    }
    /** Descripción: Ejecuta la operación «temporary» correspondiente a la responsabilidad de AtomicJsonDocumentStore. Entradas: destination: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Path temporary(Path destination) { return destination.resolveSibling(destination.getFileName() + ".tmp"); }
    /** Descripción: Ejecuta la operación «move» correspondiente a la responsabilidad de AtomicJsonDocumentStore. Entradas: source: dato de entrada necesario para la operación; destination: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void move(Path source, Path destination) throws IOException {
        try { Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch(AtomicMoveNotSupportedException exception) { Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING); }
    }
    /** Descripción: Ejecuta la operación «etag» correspondiente a la responsabilidad de AtomicJsonDocumentStore. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String etag(byte[] content) {
        try { return "\"" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)) + "\""; }
        catch(NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 must be available", exception); }
    }
    /** Descripción: Elimina o libera el recurso gestionado por «deleteTemporary» de forma controlada. Entradas: path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void deleteTemporary(Path path) { try { Files.deleteIfExists(path); } catch(IOException ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/validation/persistence/AtomicJsonDocumentStore.java",ignored);} }
    /** Descripción: Valida las condiciones necesarias para «validateSuffix» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String validateSuffix(String value) {
        if(value == null || value.isBlank() || value.contains("/") || value.contains("\\") || value.contains("..")) throw new CanonicalDocumentStoreException(Reason.INVALID_REQUEST, "File suffix is invalid");
        return value;
    }
    /** Descripción: Valida las condiciones necesarias para «validateId» y rechaza entradas no válidas. Entradas: id: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void validateId(String id) {
        try { StableIdentifierPolicy.requireDocumentId(id, "id"); }
        catch(IllegalArgumentException exception) { throw new CanonicalDocumentStoreException(Reason.INVALID_REQUEST, exception.getMessage(), exception); }
    }
}
