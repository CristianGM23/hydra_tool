package com.example.editor.event.flow.runtime;
import java.time.Instant;
import java.util.List;
import com.example.editor.validation.contract.StableIdentifierPolicy;
/** Estado durable completo del runtime de flujos y sus trazas recientes. */
public record EventFlowRuntimeSnapshot(String schemaVersion, String id, String metamodelId, Instant updatedAt,
        List<FlowWaitingContinuation> waitingContinuations, List<FlowRetryContinuation> retryContinuations,
        List<FlowExecutionTrace> traces, EventFlowRuntimeStateDocument runtimeState) {
    public EventFlowRuntimeSnapshot {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? "1.0" : schemaVersion;
        if(!schemaVersion.equals("1.0")) throw new IllegalArgumentException("Unsupported event-flow runtime schema: " + schemaVersion);
        id = StableIdentifierPolicy.requireDocumentId(id, "eventFlowRuntimeSnapshot.id");
        metamodelId = StableIdentifierPolicy.requireDocumentId(metamodelId, "eventFlowRuntimeSnapshot.metamodelId");
        if(updatedAt == null) updatedAt = Instant.now();
        waitingContinuations = waitingContinuations == null ? List.of() : List.copyOf(waitingContinuations);
        retryContinuations = retryContinuations == null ? List.of() : List.copyOf(retryContinuations);
        traces = traces == null ? List.of() : List.copyOf(traces);
        if(runtimeState == null) runtimeState = EventFlowRuntimeStateDocument.empty();
    }
    /** Constructor de conveniencia sin reintentos persistidos. */
    /** Descripción: Inicializa una instancia de EventFlowRuntimeSnapshot con sus dependencias y datos de entrada. Entradas: schemaVersion: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; metamodelId: identificador que delimita el recurso objetivo; updatedAt: dato de entrada necesario para la operación; waitingContinuations: dato de entrada necesario para la operación; traces: dato de entrada necesario para la operación; runtimeState: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EventFlowRuntimeSnapshot(String schemaVersion, String id, String metamodelId, Instant updatedAt,
            List<FlowWaitingContinuation> waitingContinuations, List<FlowExecutionTrace> traces,
            EventFlowRuntimeStateDocument runtimeState) {
        this(schemaVersion,id,metamodelId,updatedAt,waitingContinuations,List.of(),traces,runtimeState);
    }
    /** Constructor de conveniencia con estado runtime vacío. */
    /** Descripción: Inicializa una instancia de EventFlowRuntimeSnapshot con sus dependencias y datos de entrada. Entradas: schemaVersion: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; metamodelId: identificador que delimita el recurso objetivo; updatedAt: dato de entrada necesario para la operación; waitingContinuations: dato de entrada necesario para la operación; traces: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EventFlowRuntimeSnapshot(String schemaVersion, String id, String metamodelId, Instant updatedAt,
            List<FlowWaitingContinuation> waitingContinuations, List<FlowExecutionTrace> traces) {
        this(schemaVersion,id,metamodelId,updatedAt,waitingContinuations,List.of(),traces,EventFlowRuntimeStateDocument.empty());
    }
}
