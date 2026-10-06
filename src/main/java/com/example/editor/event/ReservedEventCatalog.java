package com.example.editor.event;
import java.util.LinkedHashSet;
import java.util.Set;
/** Catálogo estable de eventos emitidos por el núcleo. */
public final class ReservedEventCatalog {
    private static final Set<String> EVENT_TYPES = Set.copyOf(new LinkedHashSet<>(Set.of(
        "core.metamodel.class-created", "core.metamodel.attribute-created", "core.metamodel.reference-created", "core.metamodel.operation-created",
        "core.model.element-created", "core.model.element-updated", "core.model.element-deleted", "core.model.saved", "core.model.exported",
        "core.validation.execution-started", "core.validation.execution-completed", "core.validation.violation-created", "core.validation.violation-resolved", "core.validation.violation-reopened",
        "core.quickfix.started", "core.quickfix.applied", "core.quickfix.failed", "core.quickfix.undone", "core.quickfix.redone",
        "core.operation.invoked", "core.operation.completed", "core.operation.failed", "core.import.completed", "core.export.completed",
        "core.event-flow.started", "core.event-flow.completed", "core.event-flow.failed", "core.event-flow.derived-event",
        "core.gamification.award-granted", "core.gamification.penalty-applied", "core.gamification.award-rejected", "core.gamification.award-compensated")));
    /** Descripción: Inicializa una instancia de ReservedEventCatalog con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private ReservedEventCatalog() { }
    /** Descripción: Ejecuta la operación «eventTypeIds» correspondiente a la responsabilidad de ReservedEventCatalog. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static Set<String> eventTypeIds() { return EVENT_TYPES; }
    /** Descripción: Evalúa la condición «isReserved» sin modificar el estado externo. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ public static boolean isReserved(String id) { return EVENT_TYPES.contains(id); }
}
