package com.example.editor.transformation.sync;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.example.editor.persistence.CanonicalDocumentStore;
import com.example.editor.persistence.CanonicalDocumentStoreFactory;
import com.example.editor.persistence.CanonicalDocumentStoreSpec;
import com.example.editor.transformation.TransformationProperties;
import com.example.editor.validation.persistence.StoredCanonicalDocument;

import tools.jackson.databind.ObjectMapper;

/** Persistencia JSON atómica para sesiones y checkpoints de sincronización. */
@Repository
public class JsonTransformationSynchronizationSessionRepository implements TransformationSynchronizationSessionRepository {
    private final CanonicalDocumentStore<TransformationSynchronizationSession> sessions;
    private final CanonicalDocumentStore<TransformationSynchronizationCheckpoint> checkpoints;

    /** Descripción: Inicializa una instancia de JsonTransformationSynchronizationSessionRepository con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; mapper: componente de conversión utilizado durante el procesamiento; stores: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public JsonTransformationSynchronizationSessionRepository(TransformationProperties properties, ObjectMapper mapper,
            CanonicalDocumentStoreFactory stores) {
        sessions = stores.create(new CanonicalDocumentStoreSpec<>("transformation.synchronization-session",
                properties.getSynchronizationSessionDirectory(), properties.getSynchronizationSessionFileSuffix(),
                properties.isUseEtag(), mapper, TransformationSynchronizationSession.class, TransformationSynchronizationSession::id));
        checkpoints = stores.create(new CanonicalDocumentStoreSpec<>("transformation.synchronization-checkpoint",
                properties.getSynchronizationHistoryDirectory(), properties.getSynchronizationCheckpointFileSuffix(),
                false, mapper, TransformationSynchronizationCheckpoint.class, TransformationSynchronizationCheckpoint::id));
    }

    /** Descripción: Crea o prepara el resultado de «createSession» dentro de JsonTransformationSynchronizationSessionRepository. Entradas: session: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public StoredCanonicalDocument<TransformationSynchronizationSession> createSession(TransformationSynchronizationSession session) { return sessions.create(session); }
    /** Descripción: Actualiza el estado gestionado por JsonTransformationSynchronizationSessionRepository mediante la operación «replaceSession». Entradas: id: identificador que delimita el recurso objetivo; session: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public StoredCanonicalDocument<TransformationSynchronizationSession> replaceSession(String id, TransformationSynchronizationSession session, String etag) { return sessions.replace(id, session, etag); }
    /** Descripción: Obtiene la información correspondiente a «findSession» dentro de JsonTransformationSynchronizationSessionRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Optional<StoredCanonicalDocument<TransformationSynchronizationSession>> findSession(String id) { return sessions.find(id); }
    /** Descripción: Obtiene la información correspondiente a «listSessionIds» dentro de JsonTransformationSynchronizationSessionRepository. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public List<String> listSessionIds() { return sessions.listIds(); }
    /** Descripción: Elimina o libera el recurso gestionado por «deleteSession» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ @Override public void deleteSession(String id, String etag) { sessions.delete(id, etag); }
    /** Descripción: Crea o prepara el resultado de «createCheckpoint» dentro de JsonTransformationSynchronizationSessionRepository. Entradas: checkpoint: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public StoredCanonicalDocument<TransformationSynchronizationCheckpoint> createCheckpoint(TransformationSynchronizationCheckpoint checkpoint) { return checkpoints.create(checkpoint); }
    /** Descripción: Obtiene la información correspondiente a «findCheckpoint» dentro de JsonTransformationSynchronizationSessionRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Optional<StoredCanonicalDocument<TransformationSynchronizationCheckpoint>> findCheckpoint(String id) { return checkpoints.find(id); }
    /** Descripción: Obtiene la información correspondiente a «listCheckpointIds» dentro de JsonTransformationSynchronizationSessionRepository. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public List<String> listCheckpointIds() { return checkpoints.listIds(); }
    /** Descripción: Elimina o libera el recurso gestionado por «deleteCheckpointIfExists» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ @Override public void deleteCheckpointIfExists(String id) { checkpoints.deleteIfExists(id); }
}
