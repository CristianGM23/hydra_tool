package com.example.editor.transformation.sync;

import java.util.List;
import java.util.Optional;

import com.example.editor.validation.persistence.StoredCanonicalDocument;

/** Persistencia neutral de sesiones y checkpoints de sincronización. */
public interface TransformationSynchronizationSessionRepository {
    /** Descripción: Crea o prepara el resultado de «createSession» dentro de TransformationSynchronizationSessionRepository. Entradas: session: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredCanonicalDocument<TransformationSynchronizationSession> createSession(TransformationSynchronizationSession session);
    /** Descripción: Actualiza el estado gestionado por TransformationSynchronizationSessionRepository mediante la operación «replaceSession». Entradas: id: identificador que delimita el recurso objetivo; session: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredCanonicalDocument<TransformationSynchronizationSession> replaceSession(String id, TransformationSynchronizationSession session, String etag);
    /** Descripción: Obtiene la información correspondiente a «findSession» dentro de TransformationSynchronizationSessionRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ Optional<StoredCanonicalDocument<TransformationSynchronizationSession>> findSession(String id);
    /** Descripción: Obtiene la información correspondiente a «listSessionIds» dentro de TransformationSynchronizationSessionRepository. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ List<String> listSessionIds();
    /** Descripción: Elimina o libera el recurso gestionado por «deleteSession» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void deleteSession(String id, String etag);

    /** Descripción: Crea o prepara el resultado de «createCheckpoint» dentro de TransformationSynchronizationSessionRepository. Entradas: checkpoint: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredCanonicalDocument<TransformationSynchronizationCheckpoint> createCheckpoint(TransformationSynchronizationCheckpoint checkpoint);
    /** Descripción: Obtiene la información correspondiente a «findCheckpoint» dentro de TransformationSynchronizationSessionRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ Optional<StoredCanonicalDocument<TransformationSynchronizationCheckpoint>> findCheckpoint(String id);
    /** Descripción: Obtiene la información correspondiente a «listCheckpointIds» dentro de TransformationSynchronizationSessionRepository. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ List<String> listCheckpointIds();
    /** Descripción: Elimina o libera el recurso gestionado por «deleteCheckpointIfExists» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void deleteCheckpointIfExists(String id);
}
