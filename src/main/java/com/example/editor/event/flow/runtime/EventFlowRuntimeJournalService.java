package com.example.editor.event.flow.runtime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import com.example.editor.event.flow.EventFlowProperties;
import com.example.editor.event.flow.runtime.persistence.EventFlowRuntimeSnapshotRepository;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
/** Persiste esperas, reintentos, estado CEP y trazas recientes con escritura atómica y ETag. */
@Service
public class EventFlowRuntimeJournalService {
    private final EventFlowRuntimeSnapshotRepository repository;
    private final EventFlowProperties properties;
    /** Descripción: Inicializa una instancia de EventFlowRuntimeJournalService con sus dependencias y datos de entrada. Entradas: repository: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public EventFlowRuntimeJournalService(EventFlowRuntimeSnapshotRepository repository, EventFlowProperties properties) {
        this.repository = repository; this.properties = properties;
    }
    /** Descripción: Obtiene la información correspondiente a «load» dentro de EventFlowRuntimeJournalService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public EventFlowRuntimeSnapshot load(String metamodelId) {
        return repository.find(metamodelId).map(StoredCanonicalDocument::document)
                .orElseGet(() -> empty(metamodelId));
    }
    public synchronized EventFlowRuntimeSnapshot save(String metamodelId, List<FlowWaitingContinuation> waits,
            List<FlowRetryContinuation> retries, List<FlowExecutionTrace> traces,
            EventFlowRuntimeStateDocument runtimeState) {
        List<FlowExecutionTrace> limited = new ArrayList<>(traces == null ? List.of() : traces);
        int max = properties.getMaximumExecutionTraces();
        if(limited.size() > max) limited = new ArrayList<>(limited.subList(limited.size() - max, limited.size()));
        EventFlowRuntimeSnapshot snapshot = new EventFlowRuntimeSnapshot("1.0", metamodelId, metamodelId, Instant.now(),
                waits, retries, limited, runtimeState);
        var existing = repository.find(metamodelId);
        return repository.save(snapshot, existing.map(StoredCanonicalDocument::etag).orElse("")).document();
    }
    /** Sobrecarga de conveniencia cuando no hay reintentos persistidos. */
    public synchronized EventFlowRuntimeSnapshot save(String metamodelId, List<FlowWaitingContinuation> waits,
            List<FlowExecutionTrace> traces, EventFlowRuntimeStateDocument runtimeState) {
        return save(metamodelId, waits, List.of(), traces, runtimeState);
    }
    /** Sobrecarga de conveniencia con estado runtime vacío. */
    /** Descripción: Actualiza el estado gestionado por EventFlowRuntimeJournalService mediante la operación «save». Entradas: metamodelId: identificador que delimita el recurso objetivo; waits: dato de entrada necesario para la operación; traces: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public synchronized EventFlowRuntimeSnapshot save(String metamodelId, List<FlowWaitingContinuation> waits,
            List<FlowExecutionTrace> traces) {
        return save(metamodelId, waits, List.of(), traces, EventFlowRuntimeStateDocument.empty());
    }
    /** Descripción: Ejecuta la operación «traces» correspondiente a la responsabilidad de EventFlowRuntimeJournalService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public List<FlowExecutionTrace> traces(String metamodelId) { return load(metamodelId).traces(); }
    /** Descripción: Elimina o libera el recurso gestionado por «clear» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void clear(String metamodelId) { repository.deleteIfExists(metamodelId); }
    /** Descripción: Ejecuta la operación «empty» correspondiente a la responsabilidad de EventFlowRuntimeJournalService. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private EventFlowRuntimeSnapshot empty(String id) {
        return new EventFlowRuntimeSnapshot("1.0", id, id, Instant.now(), List.of(), List.of(), List.of(), EventFlowRuntimeStateDocument.empty());
    }
}
