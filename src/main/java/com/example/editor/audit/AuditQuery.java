package com.example.editor.audit;

import java.time.Instant;
import java.util.Set;

/** Objeto de consulta extensible; evita firmas crecientes en controller/store. */
public record AuditQuery(String workspaceId, Set<String> actorIds, Set<String> moduleIds, Set<String> actions,
        Set<String> activityTypes, Set<String> resourceTypes, Set<String> resourceIds, Instant from, Instant to,
        String text, String correlationId, int page, int pageSize, AuditSortDirection sortDirection) {
    public AuditQuery {
        workspaceId = normalize(workspaceId);
        actorIds = copy(actorIds);
        moduleIds = copy(moduleIds);
        actions = copy(actions);
        activityTypes = copy(activityTypes);
        resourceTypes = copy(resourceTypes);
        resourceIds = copy(resourceIds);
        text = text == null ? "" : text.trim();
        correlationId = correlationId == null ? "" : correlationId.trim();
        page = Math.max(0, page);
        pageSize = Math.max(1, pageSize);
        sortDirection = sortDirection == null ? AuditSortDirection.DESC : sortDirection;
    }
    /** Descripción: Ejecuta la operación «recent» correspondiente a la responsabilidad de AuditQuery. Entradas: workspaceId: identificador que delimita el recurso objetivo; from: dato de entrada necesario para la operación; to: dato de entrada necesario para la operación; page: dato de entrada necesario para la operación; pageSize: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static AuditQuery recent(String workspaceId, Instant from, Instant to, int page, int pageSize) {
        return new AuditQuery(workspaceId, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), from, to,
                "", "", page, pageSize, AuditSortDirection.DESC);
    }
    /** Descripción: Ejecuta la operación «withoutPaging» correspondiente a la responsabilidad de AuditQuery. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public AuditQuery withoutPaging() {
        return new AuditQuery(workspaceId, actorIds, moduleIds, actions, activityTypes, resourceTypes, resourceIds,
                from, to, text, correlationId, 0, Integer.MAX_VALUE, sortDirection);
    }
    /** Descripción: Ejecuta la operación «copy» correspondiente a la responsabilidad de AuditQuery. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static Set<String> copy(Set<String> values) { return values == null ? Set.of() : Set.copyOf(values); }
    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de AuditQuery. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalize(String value) { return value == null || value.isBlank() ? "local" : value.trim(); }
}
