package com.example.editor.event.flow.runtime.persistence;
import java.util.Optional;
import com.example.editor.event.flow.runtime.EventFlowRuntimeSnapshot;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
public interface EventFlowRuntimeSnapshotRepository {
    /** Descripción: Obtiene la información correspondiente a «find» dentro de EventFlowRuntimeSnapshotRepository. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ Optional<StoredCanonicalDocument<EventFlowRuntimeSnapshot>> find(String metamodelId);
    /** Descripción: Actualiza el estado gestionado por EventFlowRuntimeSnapshotRepository mediante la operación «save». Entradas: snapshot: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredCanonicalDocument<EventFlowRuntimeSnapshot> save(EventFlowRuntimeSnapshot snapshot, String etag);
    /** Descripción: Elimina o libera el recurso gestionado por «deleteIfExists» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void deleteIfExists(String metamodelId);
}
