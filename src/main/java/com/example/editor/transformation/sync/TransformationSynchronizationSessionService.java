package com.example.editor.transformation.sync;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.editor.observability.HydraBestEffortDiagnostics;
import com.example.editor.transformation.TransformationApplicationException;
import com.example.editor.transformation.TransformationApplicationService;
import com.example.editor.transformation.TransformationProperties;
import com.example.editor.transformation.core.TransformationLoadResult;
import com.example.editor.transformation.runtime.TransformationModelDelta;
import com.example.editor.transformation.runtime.TransformationRuntimeService;
import com.example.editor.transformation.runtime.TransformationSynchronizationMode;
import com.example.editor.transformation.runtime.TransformationSynchronizationRequest;
import com.example.editor.transformation.runtime.TransformationSynchronizationResult;
import com.example.editor.transformation.runtime.TransformationSynchronizationSide;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.persistence.CanonicalDocumentStoreException;
import com.example.editor.validation.persistence.StoredCanonicalDocument;

/** Gestiona baselines persistentes e historial acotado para la sincronización incremental bidireccional. */
@Service
public class TransformationSynchronizationSessionService {
    private static final String LEGACY_TEST_ETAG = "legacy-test-etag";

    private final TransformationSynchronizationSessionRepository repository;
    private final TransformationRuntimeService runtime;
    private final TransformationApplicationService transformations;
    private final int maximumHistory;
    private final Clock clock;

    @Autowired
    /** Descripción: Inicializa una instancia de TransformationSynchronizationSessionService con sus dependencias y datos de entrada. Entradas: repository: dato de entrada necesario para la operación; runtime: dato de entrada necesario para la operación; transformations: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationSynchronizationSessionService(TransformationSynchronizationSessionRepository repository,
            TransformationRuntimeService runtime, TransformationApplicationService transformations, TransformationProperties properties) {
        this(repository, runtime, transformations, properties == null ? 25 : properties.getMaximumSynchronizationHistory(), Clock.systemUTC());
    }

/** Descripción: Inicializa una instancia de TransformationSynchronizationSessionService con sus dependencias y datos de entrada. Entradas: repository: dato de entrada necesario para la operación; runtime: dato de entrada necesario para la operación; maximumHistory: dato de entrada necesario para la operación; clock: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ TransformationSynchronizationSessionService(TransformationSynchronizationSessionRepository repository,
            TransformationRuntimeService runtime, int maximumHistory, Clock clock) {
        this(repository, runtime, null, maximumHistory, clock);
    }

    /** Descripción: Inicializa una instancia de TransformationSynchronizationSessionService con sus dependencias y datos de entrada. Entradas: repository: dato de entrada necesario para la operación; runtime: dato de entrada necesario para la operación; transformations: dato de entrada necesario para la operación; maximumHistory: dato de entrada necesario para la operación; clock: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ TransformationSynchronizationSessionService(TransformationSynchronizationSessionRepository repository,
            TransformationRuntimeService runtime, TransformationApplicationService transformations, int maximumHistory, Clock clock) {
        this.repository = repository;
        this.runtime = runtime;
        this.transformations = transformations;
        this.maximumHistory = Math.max(2, Math.min(maximumHistory, 200));
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

/** Descripción: Crea o prepara el resultado de «create» dentro de TransformationSynchronizationSessionService. Entradas: transformationId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationSynchronizationSessionMutationResult create(String transformationId,
            TransformationSynchronizationSessionCreateRequest request) {
        requireCreateRequest(request);
        TransformationLoadResult transformation = loadTransformation(transformationId);
        TransformationSynchronizationSide authoritative = request.initializeFrom();
        TransformationSynchronizationResult synchronizedState = synchronize(transformationId, transformation,
                new TransformationSynchronizationRequest(request.sourceModel(), request.targetModel(), authoritative));
        requireVerifiedBaseline(synchronizedState);

        long now = clock.millis();
        String id = "sync-" + UUID.randomUUID();
        TransformationSynchronizationSession session = new TransformationSynchronizationSession(
                TransformationSynchronizationSession.CURRENT_SCHEMA_VERSION, id, transformationId, etagOf(transformation), 1,
                synchronizedState.sourceModel(), synchronizedState.targetModel(), synchronizedState.trace(), now, now);
        StoredCanonicalDocument<TransformationSynchronizationSession> stored = storage(() -> repository.createSession(session));
        try {
            storage(() -> repository.createCheckpoint(checkpoint(session, synchronizedState, "INITIALIZED_" + authoritative.name(), now)));
        } catch(RuntimeException failure) {
            try { repository.deleteSession(id, stored.etag()); }
            catch(RuntimeException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            throw failure;
        }
        return new TransformationSynchronizationSessionMutationResult(session, synchronizedState, true, null, stored.etag());
    }

/** Descripción: Obtiene la información correspondiente a «list» dentro de TransformationSynchronizationSessionService. Entradas: transformationId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public List<TransformationSynchronizationSessionSummary> list(String transformationId) {
        String currentTransformationEtag = etagOf(loadTransformation(transformationId));
        List<TransformationSynchronizationSessionSummary> result = new ArrayList<>();
        for(String id : storage(repository::listSessionIds)) {
            storage(() -> repository.findSession(id)).map(StoredCanonicalDocument::document)
                    .filter(session -> session.transformationId().equals(transformationId))
                    .map(session -> TransformationSynchronizationSessionSummary.from(session, currentTransformationEtag)).ifPresent(result::add);
        }
        result.sort(Comparator.comparingLong(TransformationSynchronizationSessionSummary::updatedAtEpochMillis).reversed()
                .thenComparing(TransformationSynchronizationSessionSummary::id));
        return List.copyOf(result);
    }

/** Descripción: Obtiene la información correspondiente a «find» dentro de TransformationSynchronizationSessionService. Entradas: transformationId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationSynchronizationSessionLoadResult find(String transformationId, String sessionId) {
        StoredCanonicalDocument<TransformationSynchronizationSession> stored = requireSession(transformationId, sessionId);
        return new TransformationSynchronizationSessionLoadResult(stored.document(), stored.etag());
    }

/** Descripción: Actualiza el estado gestionado por TransformationSynchronizationSessionService mediante la operación «synchronize». Entradas: transformationId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación; expectedEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationSynchronizationSessionMutationResult synchronize(String transformationId, String sessionId,
            TransformationSynchronizationSessionUpdateRequest request, String expectedEtag) {
        if(request == null || request.sourceModel() == null || request.targetModel() == null)
            throw new IllegalArgumentException("Both current source-side and target-side models are required");
        StoredCanonicalDocument<TransformationSynchronizationSession> stored = requireSession(transformationId, sessionId);
        TransformationSynchronizationSession current = stored.document();
        String etag = requireMatchingEtag(expectedEtag, stored.etag());
        requireSameModelIdentity(current.sourceBaseline(), request.sourceModel(), "source");
        requireSameModelIdentity(current.targetBaseline(), request.targetModel(), "target");
        TransformationLoadResult transformation = loadTransformation(transformationId);
        requireTransformationEtag(current, etagOf(transformation));

        TransformationSynchronizationResult result = synchronize(transformationId, transformation,
                new TransformationSynchronizationRequest(request.sourceModel(), request.targetModel(), TransformationSynchronizationSide.BOTH,
                        TransformationSynchronizationMode.INCREMENTAL, current.sourceBaseline(), current.targetBaseline(), current.baselineTrace()));
        if(result.blocked() || result.noOp())
            return new TransformationSynchronizationSessionMutationResult(current, result, false, null, stored.etag());

        long now = clock.millis();
        long nextRevision = current.revision() + 1;
        TransformationSynchronizationSession advanced = new TransformationSynchronizationSession(
                current.schemaVersion(), current.id(), current.transformationId(), current.transformationEtag(), nextRevision,
                result.sourceModel(), result.targetModel(), result.trace(), current.createdAtEpochMillis(), now);
        TransformationSynchronizationCheckpoint checkpoint = checkpoint(advanced, result, "SYNCHRONIZED", now);
        return commitRevision(sessionId, advanced, checkpoint, result, null, etag);
    }

/** Descripción: Ejecuta la operación «rebase» correspondiente a la responsabilidad de TransformationSynchronizationSessionService. Entradas: transformationId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación; expectedEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationSynchronizationSessionMutationResult rebase(String transformationId, String sessionId,
            TransformationSynchronizationSessionCreateRequest request, String expectedEtag) {
        requireCreateRequest(request);
        StoredCanonicalDocument<TransformationSynchronizationSession> stored = requireSession(transformationId, sessionId);
        TransformationSynchronizationSession current = stored.document();
        String etag = requireMatchingEtag(expectedEtag, stored.etag());
        requireSameModelIdentity(current.sourceBaseline(), request.sourceModel(), "source");
        requireSameModelIdentity(current.targetBaseline(), request.targetModel(), "target");
        TransformationLoadResult transformation = loadTransformation(transformationId);
        TransformationSynchronizationResult result = synchronize(transformationId, transformation,
                new TransformationSynchronizationRequest(request.sourceModel(), request.targetModel(), request.initializeFrom()));
        requireVerifiedBaseline(result);

        long now = clock.millis();
        long nextRevision = current.revision() + 1;
        TransformationSynchronizationSession rebased = new TransformationSynchronizationSession(
                current.schemaVersion(), current.id(), current.transformationId(), etagOf(transformation), nextRevision,
                result.sourceModel(), result.targetModel(), result.trace(), current.createdAtEpochMillis(), now);
        TransformationSynchronizationCheckpoint checkpoint = checkpoint(rebased, result, "REBASED_" + request.initializeFrom().name(), now);
        return commitRevision(sessionId, rebased, checkpoint, result, null, etag);
    }

/** Descripción: Ejecuta la operación «history» correspondiente a la responsabilidad de TransformationSynchronizationSessionService. Entradas: transformationId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public List<TransformationSynchronizationCheckpoint> history(String transformationId, String sessionId) {
        TransformationSynchronizationSession session = requireSession(transformationId, sessionId).document();
        String prefix = sessionId + ".r";
        List<TransformationSynchronizationCheckpoint> result = new ArrayList<>();
        for(String id : storage(repository::listCheckpointIds)) {
            if(!id.startsWith(prefix)) continue;
            storage(() -> repository.findCheckpoint(id)).map(StoredCanonicalDocument::document)
                    .filter(item -> item.sessionId().equals(sessionId) && item.transformationId().equals(transformationId)
                            && item.revision() <= session.revision())
                    .ifPresent(result::add);
        }
        result.sort(Comparator.comparingLong(TransformationSynchronizationCheckpoint::revision).reversed());
        return List.copyOf(result);
    }

/** Descripción: Ejecuta la operación «restore» correspondiente a la responsabilidad de TransformationSynchronizationSessionService. Entradas: transformationId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; revision: dato de entrada necesario para la operación; expectedEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationSynchronizationSessionMutationResult restore(String transformationId, String sessionId, long revision,
            String expectedEtag) {
        StoredCanonicalDocument<TransformationSynchronizationSession> stored = requireSession(transformationId, sessionId);
        TransformationSynchronizationSession current = stored.document();
        String etag = requireMatchingEtag(expectedEtag, stored.etag());
        TransformationSynchronizationCheckpoint selected = storage(() -> repository.findCheckpoint(TransformationSynchronizationCheckpoint.checkpointId(sessionId, revision)))
                .map(StoredCanonicalDocument::document)
                .filter(item -> item.transformationId().equals(transformationId) && item.sessionId().equals(sessionId))
                .orElseThrow(() -> new IllegalArgumentException("Synchronization checkpoint was not found: revision " + revision));

        long now = clock.millis();
        long nextRevision = current.revision() + 1;
        TransformationSynchronizationSession restored = new TransformationSynchronizationSession(current.schemaVersion(), current.id(),
                current.transformationId(), selected.transformationEtag(), nextRevision, selected.sourceBaseline(), selected.targetBaseline(),
                selected.baselineTrace(), current.createdAtEpochMillis(), now);
        TransformationSynchronizationCheckpoint checkpoint = new TransformationSynchronizationCheckpoint(
                TransformationSynchronizationCheckpoint.CURRENT_SCHEMA_VERSION,
                TransformationSynchronizationCheckpoint.checkpointId(sessionId, nextRevision), sessionId, transformationId,
                selected.transformationEtag(), nextRevision, selected.sourceBaseline(), selected.targetBaseline(), selected.baselineTrace(),
                "RESTORED_FROM_" + revision, "RESTORED", List.of(), TransformationModelDelta.empty(), TransformationModelDelta.empty(), List.of(), now);
        return commitRevision(sessionId, restored, checkpoint, null, revision, etag);
    }

/** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: transformationId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; expectedEtag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void delete(String transformationId, String sessionId, String expectedEtag) {
        StoredCanonicalDocument<TransformationSynchronizationSession> stored = requireSession(transformationId, sessionId);
        storageVoid(() -> repository.deleteSession(sessionId, requireMatchingEtag(expectedEtag, stored.etag())));
        String prefix = sessionId + ".r";
        for(String id : storage(repository::listCheckpointIds)) {
            if(!id.startsWith(prefix)) continue;
            try { repository.deleteCheckpointIfExists(id); }
            catch(RuntimeException ignored) { HydraBestEffortDiagnostics.record("TransformationSynchronizationSessionService.delete", ignored); }
        }
    }

    /** Descripción: Ejecuta la operación «commitRevision» correspondiente a la responsabilidad de TransformationSynchronizationSessionService. Entradas: sessionId: identificador que delimita el recurso objetivo; session: dato de entrada necesario para la operación; checkpoint: dato de entrada necesario para la operación; result: dato de entrada necesario para la operación; restoredFromRevision: dato de entrada necesario para la operación; expectedEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationSynchronizationSessionMutationResult commitRevision(String sessionId,
            TransformationSynchronizationSession session, TransformationSynchronizationCheckpoint checkpoint,
            TransformationSynchronizationResult result, Long restoredFromRevision, String expectedEtag) {
        storage(() -> repository.createCheckpoint(checkpoint));
        StoredCanonicalDocument<TransformationSynchronizationSession> replaced;
        try {
            replaced = storage(() -> repository.replaceSession(sessionId, session, expectedEtag));
        } catch(RuntimeException failure) {
            cleanupCheckpoint(checkpoint.id(), failure);
            throw failure;
        }
        trimHistory(sessionId, session.revision());
        return new TransformationSynchronizationSessionMutationResult(session, result, true, restoredFromRevision, replaced.etag());
    }

    /** Descripción: Valida las condiciones necesarias para «requireSession» y rechaza entradas no válidas. Entradas: transformationId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private StoredCanonicalDocument<TransformationSynchronizationSession> requireSession(String transformationId, String sessionId) {
        return storage(() -> repository.findSession(sessionId))
                .filter(stored -> stored.document().transformationId().equals(transformationId))
                .orElseThrow(() -> new TransformationApplicationException(TransformationApplicationException.Reason.NOT_FOUND,
                        "Synchronization session was not found: " + sessionId));
    }

    /** Descripción: Valida las condiciones necesarias para «requireMatchingEtag» y rechaza entradas no válidas. Entradas: expected: dato de entrada necesario para la operación; current: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String requireMatchingEtag(String expected, String current) {
        if(expected == null || expected.isBlank()) throw new TransformationApplicationException(TransformationApplicationException.Reason.PRECONDITION_FAILED,
                "If-Match header is required for synchronization session updates");
        if(!"*".equals(expected.trim()) && !current.equals(expected.trim()))
            throw new TransformationApplicationException(TransformationApplicationException.Reason.PRECONDITION_FAILED,
                    "Synchronization session has changed since it was loaded");
        return expected.trim();
    }

    /** Descripción: Valida las condiciones necesarias para «checkpoint» y rechaza entradas no válidas. Entradas: session: dato de entrada necesario para la operación; result: dato de entrada necesario para la operación; operation: dato de entrada necesario para la operación; now: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationSynchronizationCheckpoint checkpoint(TransformationSynchronizationSession session,
            TransformationSynchronizationResult result, String operation, long now) {
        return new TransformationSynchronizationCheckpoint(TransformationSynchronizationCheckpoint.CURRENT_SCHEMA_VERSION,
                TransformationSynchronizationCheckpoint.checkpointId(session.id(), session.revision()), session.id(), session.transformationId(),
                session.transformationEtag(), session.revision(), session.sourceBaseline(), session.targetBaseline(), session.baselineTrace(), operation,
                result == null ? "" : result.conflictResolution(), result == null ? List.of() : result.appliedDirections(),
                result == null ? TransformationModelDelta.empty() : result.sourceDelta(),
                result == null ? TransformationModelDelta.empty() : result.targetDelta(), result == null ? List.of() : result.conflicts(), now);
    }

    /** Descripción: Valida las condiciones necesarias para «requireCreateRequest» y rechaza entradas no válidas. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireCreateRequest(TransformationSynchronizationSessionCreateRequest request) {
        if(request == null || request.sourceModel() == null || request.targetModel() == null)
            throw new IllegalArgumentException("Both source-side and target-side models are required to establish a synchronization baseline");
    }

    /** Descripción: Valida las condiciones necesarias para «requireVerifiedBaseline» y rechaza entradas no válidas. Entradas: result: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireVerifiedBaseline(TransformationSynchronizationResult result) {
        if(result == null || result.blocked() || result.trace() == null)
            throw new IllegalStateException("Could not establish a verified synchronization baseline");
    }

    /** Descripción: Valida las condiciones necesarias para «requireSameModelIdentity» y rechaza entradas no válidas. Entradas: baseline: dato de entrada necesario para la operación; current: dato de entrada necesario para la operación; side: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireSameModelIdentity(CanonicalModelDocument baseline, CanonicalModelDocument current, String side) {
        if(current == null) throw new IllegalArgumentException("Current " + side + " model is required");
        if(!baseline.id().equals(current.id()) || !baseline.metamodelId().equals(current.metamodelId()))
            throw new TransformationApplicationException(TransformationApplicationException.Reason.CONFLICT,
                    "The " + side + " model identity does not match the managed synchronization baseline; create a new session instead");
    }

    /** Descripción: Valida las condiciones necesarias para «requireTransformationEtag» y rechaza entradas no válidas. Entradas: session: dato de entrada necesario para la operación; currentEtag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireTransformationEtag(TransformationSynchronizationSession session, String currentEtag) {
        if(transformations == null) return;
        if(!session.transformationEtag().equals(currentEtag))
            throw new TransformationApplicationException(TransformationApplicationException.Reason.CONFLICT,
                    "Transformation definition changed after this synchronization baseline was established; rebase the session before synchronizing");
    }

    /** Descripción: Obtiene la información correspondiente a «loadTransformation» dentro de TransformationSynchronizationSessionService. Entradas: transformationId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationLoadResult loadTransformation(String transformationId) {
        return transformations == null ? null : transformations.find(transformationId);
    }

    /** Descripción: Ejecuta la operación «etagOf» correspondiente a la responsabilidad de TransformationSynchronizationSessionService. Entradas: transformation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String etagOf(TransformationLoadResult transformation) {
        return transformation == null ? LEGACY_TEST_ETAG : transformation.etag();
    }

    /** Descripción: Actualiza el estado gestionado por TransformationSynchronizationSessionService mediante la operación «synchronize». Entradas: transformationId: identificador que delimita el recurso objetivo; transformation: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationSynchronizationResult synchronize(String transformationId, TransformationLoadResult transformation,
            TransformationSynchronizationRequest request) {
        return transformation == null ? runtime.synchronize(transformationId, request)
                : runtime.synchronizeDraft(transformation.transformation(), request);
    }

    /** Descripción: Ejecuta la operación «trimHistory» correspondiente a la responsabilidad de TransformationSynchronizationSessionService. Entradas: sessionId: identificador que delimita el recurso objetivo; currentRevision: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void trimHistory(String sessionId, long currentRevision) {
        long expiredRevision = currentRevision - maximumHistory;
        if(expiredRevision < 1) return;
        try { repository.deleteCheckpointIfExists(TransformationSynchronizationCheckpoint.checkpointId(sessionId, expiredRevision)); }
        catch(RuntimeException ignored) { HydraBestEffortDiagnostics.record("TransformationSynchronizationSessionService.trimHistory", ignored); }
    }

    /** Descripción: Ejecuta la operación «cleanupCheckpoint» correspondiente a la responsabilidad de TransformationSynchronizationSessionService. Entradas: checkpointId: identificador que delimita el recurso objetivo; primary: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void cleanupCheckpoint(String checkpointId, RuntimeException primary) {
        try { repository.deleteCheckpointIfExists(checkpointId); }
        catch(RuntimeException cleanupFailure) { primary.addSuppressed(cleanupFailure); }
    }

    /** Descripción: Ejecuta la operación «storage» correspondiente a la responsabilidad de TransformationSynchronizationSessionService. Entradas: action: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private <T> T storage(Supplier<T> action) {
        try { return action.get(); }
        catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }

    /** Descripción: Ejecuta la operación «storageVoid» correspondiente a la responsabilidad de TransformationSynchronizationSessionService. Entradas: action: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void storageVoid(Runnable action) {
        try { action.run(); }
        catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }

    /** Descripción: Ejecuta la operación «translate» correspondiente a la responsabilidad de TransformationSynchronizationSessionService. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationApplicationException translate(CanonicalDocumentStoreException exception) {
        TransformationApplicationException.Reason reason = switch(exception.getReason()) {
            case INVALID_REQUEST -> TransformationApplicationException.Reason.INVALID_REQUEST;
            case NOT_FOUND -> TransformationApplicationException.Reason.NOT_FOUND;
            case CONFLICT -> TransformationApplicationException.Reason.CONFLICT;
            case PRECONDITION_FAILED -> TransformationApplicationException.Reason.PRECONDITION_FAILED;
            case STORAGE_ERROR -> TransformationApplicationException.Reason.STORAGE_ERROR;
        };
        return new TransformationApplicationException(reason, exception.getMessage(), exception);
    }
}
