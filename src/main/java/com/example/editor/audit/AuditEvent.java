package com.example.editor.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Evento de entrada extensible. before/after se capturan antes de entrar en la cola asíncrona. */
public final class AuditEvent {
    private final String eventId;
    private final String activityType;
    private final String moduleId;
    private final String action;
    private final AuditImportance importance;
    private final AuditResult result;
    private final AuditResource resource;
    private final Object before;
    private final Object after;
    private final Map<String, Object> metadata;
    private final String correlationId;
    private final String parentEventId;

    /** Descripción: Inicializa una instancia de AuditEvent con sus dependencias y datos de entrada. Entradas: builder: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private AuditEvent(Builder builder) {
        eventId = builder.eventId;
        activityType = required(builder.activityType, "activityType");
        moduleId = required(builder.moduleId, "moduleId");
        action = required(builder.action, "action");
        importance = builder.importance;
        result = builder.result;
        if(builder.resource == null) throw new IllegalArgumentException("resource is required");
        resource = builder.resource;
        before = builder.before;
        after = builder.after;
        metadata = Map.copyOf(builder.metadata);
        correlationId = builder.correlationId;
        parentEventId = builder.parentEventId;
    }

    /** Descripción: Crea o prepara el resultado de «builder» dentro de Builder. Entradas: activityType: dato de entrada necesario para la operación; moduleId: identificador que delimita el recurso objetivo; action: dato de entrada necesario para la operación; resource: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static Builder builder(String activityType, String moduleId, String action, AuditResource resource) {
        return new Builder(activityType, moduleId, action, resource);
    }
    /** Descripción: Ejecuta la operación «eventId» correspondiente a la responsabilidad de Builder. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String eventId() { return eventId; }
    /** Descripción: Ejecuta la operación «activityType» correspondiente a la responsabilidad de Builder. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String activityType() { return activityType; }
    /** Descripción: Ejecuta la operación «moduleId» correspondiente a la responsabilidad de Builder. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String moduleId() { return moduleId; }
    /** Descripción: Ejecuta la operación «action» correspondiente a la responsabilidad de Builder. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String action() { return action; }
    /** Descripción: Ejecuta la operación «importance» correspondiente a la responsabilidad de Builder. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public AuditImportance importance() { return importance; }
    /** Descripción: Ejecuta la operación «result» correspondiente a la responsabilidad de Builder. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public AuditResult result() { return result; }
    /** Descripción: Ejecuta la operación «resource» correspondiente a la responsabilidad de Builder. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public AuditResource resource() { return resource; }
    /** Descripción: Ejecuta la operación «before» correspondiente a la responsabilidad de Builder. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Object before() { return before; }
    /** Descripción: Ejecuta la operación «after» correspondiente a la responsabilidad de Builder. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Object after() { return after; }
    /** Descripción: Ejecuta la operación «metadata» correspondiente a la responsabilidad de Builder. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Map<String, Object> metadata() { return metadata; }
    /** Descripción: Ejecuta la operación «correlationId» correspondiente a la responsabilidad de Builder. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String correlationId() { return correlationId; }
    /** Descripción: Ejecuta la operación «parentEventId» correspondiente a la responsabilidad de Builder. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String parentEventId() { return parentEventId; }

    public static final class Builder {
        private String eventId = UUID.randomUUID().toString();
        private final String activityType;
        private final String moduleId;
        private final String action;
        private final AuditResource resource;
        private AuditImportance importance = AuditImportance.INFO;
        private AuditResult result = AuditResult.SUCCESS;
        private Object before;
        private Object after;
        private final Map<String, Object> metadata = new LinkedHashMap<>();
        private String correlationId = "";
        private String parentEventId = "";

        /** Descripción: Inicializa una instancia de Builder con sus dependencias y datos de entrada. Entradas: activityType: dato de entrada necesario para la operación; moduleId: identificador que delimita el recurso objetivo; action: dato de entrada necesario para la operación; resource: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private Builder(String activityType, String moduleId, String action, AuditResource resource) {
            this.activityType = activityType;
            this.moduleId = moduleId;
            this.action = action;
            this.resource = resource;
        }
        /** Descripción: Ejecuta la operación «eventId» correspondiente a la responsabilidad de Builder. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public Builder eventId(String value) { eventId = required(value, "eventId"); return this; }
        /** Descripción: Ejecuta la operación «importance» correspondiente a la responsabilidad de Builder. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public Builder importance(AuditImportance value) { importance = value == null ? AuditImportance.INFO : value; return this; }
        /** Descripción: Ejecuta la operación «result» correspondiente a la responsabilidad de Builder. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public Builder result(AuditResult value) { result = value == null ? AuditResult.SUCCESS : value; return this; }
        /** Descripción: Ejecuta la operación «before» correspondiente a la responsabilidad de Builder. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public Builder before(Object value) { before = value; return this; }
        /** Descripción: Ejecuta la operación «after» correspondiente a la responsabilidad de Builder. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public Builder after(Object value) { after = value; return this; }
        /** Descripción: Ejecuta la operación «metadata» correspondiente a la responsabilidad de Builder. Entradas: key: identificador que delimita el recurso objetivo; value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public Builder metadata(String key, Object value) { if(key != null && !key.isBlank() && value != null) metadata.put(key, value); return this; }
        /** Descripción: Ejecuta la operación «metadata» correspondiente a la responsabilidad de Builder. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public Builder metadata(Map<String, ?> values) { if(values != null) values.forEach(this::metadata); return this; }
        /** Descripción: Ejecuta la operación «correlationId» correspondiente a la responsabilidad de Builder. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public Builder correlationId(String value) { correlationId = value == null ? "" : value.trim(); return this; }
        /** Descripción: Ejecuta la operación «parentEventId» correspondiente a la responsabilidad de Builder. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public Builder parentEventId(String value) { parentEventId = value == null ? "" : value.trim(); return this; }
        /** Descripción: Crea o prepara el resultado de «build» dentro de Builder. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public AuditEvent build() { return new AuditEvent(this); }
    }

    /** Descripción: Valida las condiciones necesarias para «required» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String required(String value, String field) {
        if(value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
