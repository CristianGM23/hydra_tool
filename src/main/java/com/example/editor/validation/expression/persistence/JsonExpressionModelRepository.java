package com.example.editor.validation.expression.persistence;

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

import com.example.editor.runtime.StripedKeyLockRegistry;
import java.util.regex.Pattern;

import org.springframework.stereotype.Repository;

import com.example.editor.config.StorageProperties;
import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.validation.expression.ExpressionModelProperties;
import com.example.editor.validation.expression.ExpressionModelSummary;
import com.example.editor.validation.expression.persistence.ExpressionModelRepositoryException.Reason;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;

/** Repositorio JSON atómico y concurrente para ExpressionModel 1.0. */
@Repository
public class JsonExpressionModelRepository implements ExpressionModelRepository {

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,119}");

    private final StorageProperties storageProperties;
    private final ExpressionModelProperties properties;
    private final ObjectMapper objectMapper;
    private final ObjectWriter writer;
    private final StripedKeyLockRegistry locks = new StripedKeyLockRegistry(128);

    /** Descripción: Inicializa una instancia de JsonExpressionModelRepository con sus dependencias y datos de entrada. Entradas: storageProperties: configuración que gobierna el comportamiento; properties: configuración que gobierna el comportamiento; objectMapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public JsonExpressionModelRepository(StorageProperties storageProperties, ExpressionModelProperties properties, ObjectMapper objectMapper) {
        this.storageProperties = storageProperties;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.writer = objectMapper.writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    @Override
    /** Descripción: Crea o prepara el resultado de «create» dentro de JsonExpressionModelRepository. Entradas: expressionModel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public StoredExpressionModel create(ExpressionModel expressionModel) {
        requireEnabled();
        requireModel(expressionModel);
        ReentrantLock lock = lockFor(expressionModel.id());
        lock.lock();
        try {
            Path destination = resolve(expressionModel.id());
            recoverInterruptedWrite(expressionModel.id(), destination);
            if(Files.isRegularFile(destination)) throw new ExpressionModelRepositoryException(Reason.CONFLICT, "Expression model already exists: " + expressionModel.id());
            return write(destination, expressionModel);
        } finally {
            unlock(lock);
        }
    }

    @Override
    /** Descripción: Actualiza el estado gestionado por JsonExpressionModelRepository mediante la operación «replace». Entradas: id: identificador que delimita el recurso objetivo; expressionModel: dato de entrada necesario para la operación; expectedEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public StoredExpressionModel replace(String id, ExpressionModel expressionModel, String expectedEtag) {
        requireEnabled();
        validateId(id);
        requireModel(expressionModel);
        if(!id.equals(expressionModel.id())) throw new ExpressionModelRepositoryException(Reason.INVALID_REQUEST, "Path id and expression model id must match");
        ReentrantLock lock = lockFor(id);
        lock.lock();
        try {
            Path destination = resolve(id);
            recoverInterruptedWrite(id, destination);
            if(!Files.isRegularFile(destination)) throw new ExpressionModelRepositoryException(Reason.NOT_FOUND, "Expression model was not found: " + id);
            StoredExpressionModel current = read(destination, id);
            verifyEtag(expectedEtag, current.etag());
            return write(destination, expressionModel);
        } finally {
            unlock(lock);
        }
    }

    @Override
    /** Descripción: Obtiene la información correspondiente a «findById» dentro de JsonExpressionModelRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<StoredExpressionModel> findById(String id) {
        requireEnabled();
        validateId(id);
        ReentrantLock lock = lockFor(id);
        lock.lock();
        try {
            Path destination = resolve(id);
            recoverInterruptedWrite(id, destination);
            return Files.isRegularFile(destination) ? Optional.of(read(destination, id)) : Optional.empty();
        } finally {
            unlock(lock);
        }
    }

    @Override
    /** Descripción: Obtiene la información correspondiente a «findAll» dentro de JsonExpressionModelRepository. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<ExpressionModelSummary> findAll() {
        requireEnabled();
        Path directory = directory();
        if(!Files.isDirectory(directory)) return List.of();
        String suffix = validatedSuffix();
        try(var stream = Files.list(directory)) {
            return stream.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(suffix))
                    .map(name -> name.substring(0, name.length() - suffix.length()))
                    .sorted()
                    .map(id -> findById(id).orElseThrow(() -> new ExpressionModelRepositoryException(Reason.NOT_FOUND, "Expression model was not found: " + id)))
                    .map(stored -> new ExpressionModelSummary(stored.expressionModel().id(), stored.expressionModel().metamodelId(), stored.expressionModel().schemaVersion(), stored.expressionModel().expressions().size(), stored.etag()))
                    .toList();
        } catch(IOException exception) {
            throw new ExpressionModelRepositoryException(Reason.STORAGE_ERROR, "Could not list expression models: " + directory, exception);
        }
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
            if(!Files.isRegularFile(destination)) throw new ExpressionModelRepositoryException(Reason.NOT_FOUND, "Expression model was not found: " + id);
            verifyEtag(expectedEtag, read(destination, id).etag());
            Files.delete(destination);
            Files.deleteIfExists(temporaryFor(destination));
        } catch(IOException exception) {
            throw new ExpressionModelRepositoryException(Reason.STORAGE_ERROR, "Could not delete expression model: " + id, exception);
        } finally {
            unlock(lock);
        }
    }


    @Override
    /** Descripción: Evalúa la condición «exists» sin modificar el estado externo. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean exists(String id) {
        validateId(id);
        return Files.isRegularFile(resolve(id));
    }

    @Override
    /** Descripción: Elimina o libera el recurso gestionado por «deleteIfExists» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void deleteIfExists(String id) {
        validateId(id);
        ReentrantLock lock = lockFor(id);
        lock.lock();
        try {
            Path destination = resolve(id);
            Files.deleteIfExists(destination);
            Files.deleteIfExists(temporaryFor(destination));
        } catch(IOException exception) {
            throw new ExpressionModelRepositoryException(Reason.STORAGE_ERROR, "Could not delete expression model: " + id, exception);
        } finally {
            unlock(lock);
        }
    }

    @Override
    /** Descripción: Ejecuta la operación «fileName» correspondiente a la responsabilidad de JsonExpressionModelRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public String fileName(String id) {
        validateId(id);
        return id + validatedSuffix();
    }

    /** Descripción: Actualiza el estado gestionado por JsonExpressionModelRepository mediante la operación «write». Entradas: destination: dato de entrada necesario para la operación; model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private StoredExpressionModel write(Path destination, ExpressionModel model) {
        Path temporary = temporaryFor(destination);
        try {
            Files.createDirectories(destination.getParent());
            byte[] content = writer.writeValueAsBytes(model);
            Files.write(temporary, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            move(temporary, destination);
            return new StoredExpressionModel(model, etag(content), destination);
        } catch(JacksonException exception) {
            deleteTemporary(temporary);
            throw new ExpressionModelRepositoryException(Reason.STORAGE_ERROR, "Could not serialize expression model: " + model.id(), exception);
        } catch(IOException exception) {
            deleteTemporary(temporary);
            throw new ExpressionModelRepositoryException(Reason.STORAGE_ERROR, "Could not store expression model: " + destination, exception);
        }
    }

    /** Descripción: Obtiene la información correspondiente a «read» dentro de JsonExpressionModelRepository. Entradas: path: ruta o ubicación utilizada por la operación; expectedId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private StoredExpressionModel read(Path path, String expectedId) {
        try {
            byte[] content = com.example.editor.io.BoundedIo.readInternal(path);
            ExpressionModel model = objectMapper.readValue(content, ExpressionModel.class);
            if(!expectedId.equals(model.id())) throw new ExpressionModelRepositoryException(Reason.STORAGE_ERROR, "Expression model id does not match its file name: " + expectedId);
            byte[] canonical = writer.writeValueAsBytes(model);
            return new StoredExpressionModel(model, etag(canonical), path);
        } catch(ExpressionModelRepositoryException exception) {
            throw exception;
        } catch(JacksonException | IllegalArgumentException exception) {
            throw new ExpressionModelRepositoryException(Reason.STORAGE_ERROR, "Could not parse expression model: " + path, exception);
        } catch(IOException exception) {
            throw new ExpressionModelRepositoryException(Reason.STORAGE_ERROR, "Could not read expression model: " + path, exception);
        }
    }

    /** Descripción: Ejecuta la operación «recoverInterruptedWrite» correspondiente a la responsabilidad de JsonExpressionModelRepository. Entradas: id: identificador que delimita el recurso objetivo; destination: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void recoverInterruptedWrite(String id, Path destination) {
        Path temporary = temporaryFor(destination);
        if(!Files.isRegularFile(temporary)) return;
        if(Files.isRegularFile(destination)) {
            deleteTemporary(temporary);
            return;
        }
        try {
            ExpressionModel recovered = objectMapper.readValue(com.example.editor.io.BoundedIo.readInternal(temporary), ExpressionModel.class);
            if(!id.equals(recovered.id())) {
                deleteTemporary(temporary);
                return;
            }
            move(temporary, destination);
        } catch(JacksonException | IllegalArgumentException exception) {
            deleteTemporary(temporary);
        } catch(IOException exception) {
            throw new ExpressionModelRepositoryException(Reason.STORAGE_ERROR, "Could not recover interrupted expression write: " + id, exception);
        }
    }

    /** Descripción: Valida las condiciones necesarias para «verifyEtag» y rechaza entradas no válidas. Entradas: expected: dato de entrada necesario para la operación; current: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void verifyEtag(String expected, String current) {
        if(!properties.isUseEtag()) return;
        if(expected == null || expected.isBlank()) throw new ExpressionModelRepositoryException(Reason.PRECONDITION_FAILED, "If-Match header is required");
        if(!"*".equals(expected.trim()) && !current.equals(expected.trim())) throw new ExpressionModelRepositoryException(Reason.PRECONDITION_FAILED, "Expression model has changed since it was loaded");
    }

    /** Descripción: Ejecuta la operación «etag» correspondiente a la responsabilidad de JsonExpressionModelRepository. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String etag(byte[] content) {
        try {
            return "\"" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)) + "\"";
        } catch(NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    /** Descripción: Obtiene la información correspondiente a «resolve» dentro de JsonExpressionModelRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Path resolve(String id) {
        validateId(id);
        Path directory = directory();
        Path path = directory.resolve(id + validatedSuffix()).normalize();
        if(!path.startsWith(directory)) throw new ExpressionModelRepositoryException(Reason.INVALID_REQUEST, "Expression path is outside the configured directory");
        return path;
    }

    /** Descripción: Ejecuta la operación «directory» correspondiente a la responsabilidad de JsonExpressionModelRepository. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Path directory() {
        return storageProperties.validations().toAbsolutePath().normalize();
    }

    /** Descripción: Valida las condiciones necesarias para «validatedSuffix» y rechaza entradas no válidas. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private String validatedSuffix() {
        String suffix = properties.getFileSuffix();
        if(suffix == null || suffix.isBlank() || suffix.contains("/") || suffix.contains("\\") || suffix.contains("..")) throw new ExpressionModelRepositoryException(Reason.INVALID_REQUEST, "Expression file suffix is invalid");
        return suffix;
    }

    /** Descripción: Ejecuta la operación «temporaryFor» correspondiente a la responsabilidad de JsonExpressionModelRepository. Entradas: destination: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Path temporaryFor(Path destination) { return destination.resolveSibling(destination.getFileName() + ".tmp"); }

    /** Descripción: Ejecuta la operación «move» correspondiente a la responsabilidad de JsonExpressionModelRepository. Entradas: source: dato de entrada necesario para la operación; destination: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void move(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch(AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Descripción: Elimina o libera el recurso gestionado por «deleteTemporary» de forma controlada. Entradas: path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void deleteTemporary(Path path) {
        try { Files.deleteIfExists(path); } catch(IOException ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/validation/expression/persistence/JsonExpressionModelRepository.java",ignored);}
    }

    /** Descripción: Valida las condiciones necesarias para «requireEnabled» y rechaza entradas no válidas. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireEnabled() {
        if(!properties.isEnabled()) throw new ExpressionModelRepositoryException(Reason.INVALID_REQUEST, "Canonical expression persistence is disabled");
    }

    /** Descripción: Valida las condiciones necesarias para «requireModel» y rechaza entradas no válidas. Entradas: model: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireModel(ExpressionModel model) {
        if(model == null) throw new ExpressionModelRepositoryException(Reason.INVALID_REQUEST, "Expression model must not be null");
        validateId(model.id());
        validateId(model.metamodelId());
    }

    /** Descripción: Valida las condiciones necesarias para «validateId» y rechaza entradas no válidas. Entradas: id: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateId(String id) {
        if(id == null || !SAFE_ID.matcher(id).matches()) throw new ExpressionModelRepositoryException(Reason.INVALID_REQUEST, "Expression model id is invalid: " + id);
    }

    /** Descripción: Ejecuta la operación «lockFor» correspondiente a la responsabilidad de JsonExpressionModelRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private ReentrantLock lockFor(String id) { return locks.lockFor(id); }
    /** Descripción: Ejecuta la operación «unlock» correspondiente a la responsabilidad de JsonExpressionModelRepository. Entradas: lock: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void unlock(ReentrantLock lock) { lock.unlock(); }
}
