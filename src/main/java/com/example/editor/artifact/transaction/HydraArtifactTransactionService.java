package com.example.editor.artifact.transaction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.editor.io.AtomicFiles;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.example.editor.testing.fault.HydraFaultInjectionProperties;
import com.example.editor.testing.fault.HydraFaultInjectionService;

import tools.jackson.databind.json.JsonMapper;

/** Journal transversal para operaciones de artefactos que deben poder detectarse y recuperarse tras un fallo. */
@Service
public class HydraArtifactTransactionService {
    private final Path directory;
    private final JsonMapper mapper;
    private final Clock clock;
    private final HydraFaultInjectionService faults;

    @Autowired
    /** Descripción: Inicializa el journal transversal en el directorio configurado. Entradas: directory: ruta de persistencia. Salida: deja el servicio listo para crear journals. */
    public HydraArtifactTransactionService(@Value("${hydra.artifact-transactions.directory:data/artifact-transactions}") String directory,
            HydraFaultInjectionService faults) {
        this(Path.of(directory), Clock.systemUTC(), faults);
    }

    /** Descripción: Constructor focal para pruebas con directorio y reloj controlados. Entradas: directory: ruta; clock: reloj. Salida: deja el servicio preparado. */
    HydraArtifactTransactionService(Path directory, Clock clock) {
        this(directory, clock, new HydraFaultInjectionService(new HydraFaultInjectionProperties()));
    }

    /** Descripción: Constructor interno que permite fault injection controlada. Entradas: directory: ruta; clock: reloj; faults: servicio de fallos. Salida: deja el servicio listo. */
    HydraArtifactTransactionService(Path directory, Clock clock, HydraFaultInjectionService faults) {
        this.directory = directory.toAbsolutePath().normalize();
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.faults = faults;
        this.mapper = JsonMapper.builder().build();
    }

    /** Descripción: Ejecuta una operación dentro de un journal persistente y conserva RECOVERY_REQUIRED si falla. Entradas: operation: tipo; subjectId: artefacto principal; resources: recursos afectados; action: operación real. Salida: no devuelve valor si la acción termina correctamente. */
    public void execute(HydraArtifactTransactionOperation operation, String subjectId, List<String> resources, Runnable action) {
        if (action == null) throw new IllegalArgumentException("Artifact transaction action is required");
        execute(operation, subjectId, resources, () -> { action.run(); return null; });
    }

    /** Descripción: Ejecuta una operación con resultado dentro de un journal persistente y conserva RECOVERY_REQUIRED si falla. Entradas: operation: tipo; subjectId: sujeto; resources: recursos; action: cálculo real. Salida: resultado de la acción cuando completa. */
    public <T> T execute(HydraArtifactTransactionOperation operation, String subjectId, List<String> resources, Supplier<T> action) {
        if (action == null) throw new IllegalArgumentException("Artifact transaction action is required");
        HydraArtifactTransactionJournal prepared = prepare(operation, subjectId, resources);
        faults.check(HydraFaultInjectionService.Point.ARTIFACT_TRANSACTION_AFTER_PREPARE);
        write(withStatus(prepared, HydraArtifactTransactionStatus.RUNNING, "Operation started"));
        try {
            T result = action.get();
            complete(prepared.id());
            return result;
        } catch (RuntimeException exception) {
            write(withStatus(prepared, HydraArtifactTransactionStatus.RECOVERY_REQUIRED, safeMessage(exception)));
            throw exception;
        }
    }

    /** Descripción: Crea y persiste un journal PREPARED antes de iniciar una mutación. Entradas: operation: tipo; subjectId: sujeto; resources: recursos. Salida: journal persistido. */
    public HydraArtifactTransactionJournal prepare(HydraArtifactTransactionOperation operation, String subjectId, List<String> resources) {
        String subject = StableIdentifierPolicy.requireDocumentId(subjectId, "subjectId");
        Instant now = clock.instant();
        HydraArtifactTransactionJournal journal = new HydraArtifactTransactionJournal(HydraArtifactTransactionJournal.CURRENT_SCHEMA_VERSION,
                "tx-" + UUID.randomUUID(), operation, subject, resources, HydraArtifactTransactionStatus.PREPARED, "Prepared", now, now);
        write(journal);
        return journal;
    }

    /** Descripción: Marca la transacción como completada y elimina su journal porque ya no requiere recuperación. Entradas: id: identificador de transacción. Salida: no devuelve valor. */
    public void complete(String id) {
        try { Files.deleteIfExists(resolve(id)); }
        catch (IOException exception) { throw new IllegalStateException("Could not remove completed artifact transaction journal: " + id, exception); }
    }

    /** Descripción: Devuelve únicamente journals que requieren decisión o recuperación. Entradas: no requiere parámetros. Salida: lista ordenada por actualización descendente. */
    public List<HydraArtifactTransactionJournal> pending() {
        if (!Files.isDirectory(directory)) return List.of();
        List<HydraArtifactTransactionJournal> result = new ArrayList<>();
        try (var stream = Files.list(directory)) {
            for (Path path : stream.filter(Files::isRegularFile).filter(item -> item.getFileName().toString().endsWith(".json")).toList()) {
                try {
                    HydraArtifactTransactionJournal journal = mapper.readValue(com.example.editor.io.BoundedIo.readInternal(path), HydraArtifactTransactionJournal.class);
                    if (journal.status() != HydraArtifactTransactionStatus.COMPLETED && journal.status() != HydraArtifactTransactionStatus.ABORTED) result.add(journal);
                } catch (Exception exception) {
                    throw new IllegalStateException("Could not read artifact transaction journal: " + path, exception);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not list artifact transaction journals", exception);
        }
        result.sort(Comparator.comparing(HydraArtifactTransactionJournal::updatedAt).reversed());
        return List.copyOf(result);
    }

    /** Descripción: Descarta explícitamente un journal pendiente sin ejecutar ninguna mutación adicional. Entradas: id: identificador del journal. Salida: no devuelve valor. */
    public void abort(String id) {
        Path path = resolve(id);
        if (!Files.isRegularFile(path)) return;
        try {
            HydraArtifactTransactionJournal current = mapper.readValue(com.example.editor.io.BoundedIo.readInternal(path), HydraArtifactTransactionJournal.class);
            write(withStatus(current, HydraArtifactTransactionStatus.ABORTED, "Aborted by operator"));
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not abort artifact transaction: " + id, exception);
        }
    }

    /** Descripción: Persiste atómicamente un journal, creando el directorio solo cuando es necesario. Entradas: journal: estado a publicar. Salida: no devuelve valor. */
    private void write(HydraArtifactTransactionJournal journal) {
        try {
            Files.createDirectories(directory);
            AtomicFiles.writeReplacing(resolve(journal.id()), mapper.writeValueAsBytes(journal));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not persist artifact transaction journal: " + journal.id(), exception);
        }
    }

    /** Descripción: Crea una nueva instantánea del journal con estado y mensaje actualizados. Entradas: source: journal base; status: estado; message: detalle. Salida: journal actualizado. */
    private HydraArtifactTransactionJournal withStatus(HydraArtifactTransactionJournal source, HydraArtifactTransactionStatus status, String message) {
        return new HydraArtifactTransactionJournal(source.schemaVersion(), source.id(), source.operation(), source.subjectId(), source.resources(),
                status, message, source.createdAt(), clock.instant());
    }

    /** Descripción: Resuelve de forma segura la ruta de un journal dentro del directorio configurado. Entradas: id: identificador del journal. Salida: ruta confinada. */
    private Path resolve(String id) {
        String safe = StableIdentifierPolicy.requireDocumentId(id, "transactionId");
        Path path = directory.resolve(safe + ".json").normalize();
        if (!path.startsWith(directory)) throw new IllegalArgumentException("Artifact transaction path escapes configured directory");
        return path;
    }

    /** Descripción: Obtiene un mensaje breve para el journal de fallo. Entradas: exception: excepción capturada. Salida: mensaje no vacío. */
    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
