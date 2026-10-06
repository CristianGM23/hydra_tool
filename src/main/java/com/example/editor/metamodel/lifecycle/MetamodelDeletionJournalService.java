package com.example.editor.metamodel.lifecycle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.editor.config.StorageProperties;
import com.example.editor.io.AtomicFiles;
import com.example.editor.metamodel.MetamodelIdPolicy;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;

/** Persists recoverable metamodel deletion journals beside HYDRA runtime data. */
@Service
public final class MetamodelDeletionJournalService {
    private final Path root;
    private final ObjectMapper mapper;
    private final ObjectWriter writer;

    /** Descripción: Inicializa una instancia de MetamodelDeletionJournalService con sus dependencias y datos de entrada. Entradas: storage: dato de entrada necesario para la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelDeletionJournalService(StorageProperties storage, ObjectMapper mapper) {
        Path metamodelRoot = storage.metamodels().toAbsolutePath().normalize();
        Path dataRoot = metamodelRoot.getParent();
        if (dataRoot == null) throw new IllegalArgumentException("Metamodel storage must have a parent directory");
        this.root = dataRoot.resolve("recovery").resolve("metamodel-deletions").normalize();
        this.mapper = mapper;
        this.writer = mapper.writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    /** Descripción: Ejecuta la operación «begin» correspondiente a la responsabilidad de MetamodelDeletionJournalService. Entradas: metamodelId: identificador que delimita el recurso objetivo; deleteNotations: dato de entrada necesario para la operación; deleteExports: dato de entrada necesario para la operación; expectedFiles: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelDeletionJournal begin(String metamodelId, boolean deleteNotations, boolean deleteExports,
            List<String> expectedFiles) {
        MetamodelIdPolicy.require(metamodelId);
        Instant now = Instant.now();
        String transactionId = "delete-" + metamodelId + "-" + now.toEpochMilli() + "-"
                + UUID.randomUUID().toString().substring(0, 8);
        return write(new MetamodelDeletionJournal(transactionId, metamodelId, now, now, deleteNotations,
                deleteExports, expectedFiles, expectedFiles, MetamodelDeletionJournal.Status.PREPARED, ""));
    }

    /** Descripción: Ejecuta la operación «markPrimaryDeleted» correspondiente a la responsabilidad de MetamodelDeletionJournalService. Entradas: journal: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelDeletionJournal markPrimaryDeleted(MetamodelDeletionJournal journal) {
        return transition(journal, MetamodelDeletionJournal.Status.PRIMARY_DELETED, journal.expectedFiles(), "");
    }

    /** Descripción: Ejecuta la operación «markRecoveryRequired» correspondiente a la responsabilidad de MetamodelDeletionJournalService. Entradas: journal: dato de entrada necesario para la operación; pending: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelDeletionJournal markRecoveryRequired(MetamodelDeletionJournal journal, List<String> pending) {
        return transition(journal, MetamodelDeletionJournal.Status.RECOVERY_REQUIRED, pending,
                "Residual managed artifacts remain after the canonical metamodel was deleted");
    }

    /** Descripción: Ejecuta la operación «markCompleted» correspondiente a la responsabilidad de MetamodelDeletionJournalService. Entradas: journal: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelDeletionJournal markCompleted(MetamodelDeletionJournal journal) {
        return transition(journal, MetamodelDeletionJournal.Status.COMPLETED, List.of(), "");
    }

    /** Descripción: Ejecuta la operación «markAborted» correspondiente a la responsabilidad de MetamodelDeletionJournalService. Entradas: journal: dato de entrada necesario para la operación; failure: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelDeletionJournal markAborted(MetamodelDeletionJournal journal, RuntimeException failure) {
        String message = failure == null || failure.getMessage() == null ? "Primary deletion failed" : failure.getMessage();
        return transition(journal, MetamodelDeletionJournal.Status.ABORTED, journal.expectedFiles(), message);
    }

    /** Descripción: Obtiene la información correspondiente a «find» dentro de MetamodelDeletionJournalService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<MetamodelDeletionJournal> find(String metamodelId) {
        MetamodelIdPolicy.require(metamodelId);
        Path path = path(metamodelId);
        if (!Files.isRegularFile(path)) return Optional.empty();
        try {
            return Optional.of(mapper.readValue(com.example.editor.io.BoundedIo.readInternal(path), MetamodelDeletionJournal.class));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not read metamodel deletion journal: " + metamodelId, exception);
        }
    }

    /** Descripción: Ejecuta la operación «transition» correspondiente a la responsabilidad de MetamodelDeletionJournalService. Entradas: current: dato de entrada necesario para la operación; status: dato de entrada necesario para la operación; pending: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetamodelDeletionJournal transition(MetamodelDeletionJournal current,
            MetamodelDeletionJournal.Status status, List<String> pending, String message) {
        if (current == null) throw new IllegalArgumentException("Deletion journal is required");
        return write(new MetamodelDeletionJournal(current.transactionId(), current.metamodelId(), current.createdAt(),
                Instant.now(), current.deleteNotations(), current.deleteExports(), current.expectedFiles(), pending,
                status, message));
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelDeletionJournalService mediante la operación «write». Entradas: journal: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetamodelDeletionJournal write(MetamodelDeletionJournal journal) {
        try {
            AtomicFiles.writeReplacing(path(journal.metamodelId()), writer.writeValueAsBytes(journal));
            return journal;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not persist metamodel deletion journal: " + journal.metamodelId(), exception);
        }
    }

    /** Descripción: Ejecuta la operación «path» correspondiente a la responsabilidad de MetamodelDeletionJournalService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Path path(String metamodelId) {
        return root.resolve(metamodelId + ".deletion-journal.json").normalize();
    }
}
