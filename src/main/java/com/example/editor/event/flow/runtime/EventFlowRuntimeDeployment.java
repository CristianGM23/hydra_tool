package com.example.editor.event.flow.runtime;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/** Despliegue local de un plan compilado; el servicio comparte un dispatcher canónico único. */
final class EventFlowRuntimeDeployment {
    final String metamodelId;
    final ExecutableEventFlowPlan plan;
    final String expressionModelId;
    final EventFlowRuntimeState state = new EventFlowRuntimeState();
    final AtomicLong processedEvents = new AtomicLong();
    final AtomicLong emittedEvents = new AtomicLong();
    volatile boolean active;
    volatile String lastFailure = "";
    volatile Instant updatedAt = Instant.now();

    /** Constructor compatible para pruebas y runtimes embebidos anteriores a las condiciones neutrales. */
    /** Descripción: Inicializa un despliegue compatible sin modelo de expresiones explícito. Entradas: metamodelId y plan. Salida: deja el despliegue preparado. */ EventFlowRuntimeDeployment(String metamodelId, ExecutableEventFlowPlan plan) { this(metamodelId, plan, ""); }

    /** Descripción: Inicializa una instancia de EventFlowRuntimeDeployment con sus dependencias y datos de entrada. Entradas: metamodelId: identificador que delimita el recurso objetivo; plan: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ EventFlowRuntimeDeployment(String metamodelId, ExecutableEventFlowPlan plan, String expressionModelId) {
        this.metamodelId = metamodelId;
        this.plan = plan;
        this.expressionModelId = expressionModelId == null ? "" : expressionModelId;
    }

    /** Descripción: Elimina o libera el recurso gestionado por «close» de forma controlada. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void close() {
        active = false;
        updatedAt = Instant.now();
    }
}
