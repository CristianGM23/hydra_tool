package com.example.editor.audit;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.editor.scope.ResourceScope;
import com.example.editor.scope.WorkspaceContext;

/** Valida, limita y deduplica actividades procedentes del navegador. */
@Component
public class AuditClientActivityService {
    private final AuditEventPublisher publisher;
    private final AuditActorContext actorContext;
    private final AuditLogProperties properties;
    private final WorkspaceContext workspaceContext;
    private final Object dedupeLock = new Object();
    private final LinkedHashMap<String, Long> recent = new LinkedHashMap<>(128, 0.75f, true);

    @Autowired
    /** Descripción: Inicializa una instancia de AuditClientActivityService con sus dependencias y datos de entrada. Entradas: publisher: dato de entrada necesario para la operación; actorContext: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento; workspaceContext: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public AuditClientActivityService(AuditEventPublisher publisher, AuditActorContext actorContext, AuditLogProperties properties, WorkspaceContext workspaceContext) {
        this.publisher = publisher;
        this.actorContext = actorContext;
        this.properties = properties;
        this.workspaceContext = workspaceContext;
    }

    /** Constructor conservado para tests unitarios aislados. */
    /** Descripción: Inicializa una instancia de AuditClientActivityService con sus dependencias y datos de entrada. Entradas: publisher: dato de entrada necesario para la operación; actorContext: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public AuditClientActivityService(AuditEventPublisher publisher, AuditActorContext actorContext, AuditLogProperties properties) {
        this(publisher, actorContext, properties, null);
    }

    /** Descripción: Procesa la operación «publish» coordinando las responsabilidades de AuditClientActivityService. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean publish(AuditClientActivityRequest request) {
        if(request == null || !properties.isEnabled()) return false;
        String activityType = identifier(request.activityType(), 160, "ui.activity");
        String moduleId = identifier(request.moduleId(), 80, "general");
        String action = identifier(request.action(), 40, AuditActions.NAVIGATE).toUpperCase(Locale.ROOT);
        String resourceType = identifier(request.resourceType(), 80, "ui-resource");
        String resourceId = safeText(request.resourceId(), 160, resourceType);
        String displayName = safeText(request.displayName(), properties.getMaximumClientStringLength(), resourceId);
        String key = dedupeKey(request, activityType, moduleId, action, resourceId);
        if(duplicate(key)) return false;
        AuditImportance importance = importance(request.importance());
        publisher.publish(AuditEvent.builder(activityType, moduleId, action, new AuditResource("ui." + moduleId, resourceType, resourceId, displayName))
                .importance(importance).before(request.before()).after(request.after())
                .metadata(request.metadata() == null ? Map.of() : request.metadata()).build());
        return true;
    }

    /** Descripción: Ejecuta la operación «duplicate» correspondiente a la responsabilidad de AuditClientActivityService. Entradas: key: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean duplicate(String key) {
        if(key.isBlank() || properties.getClientDedupeWindowMillis() <= 0) return false;
        long now = System.currentTimeMillis();
        synchronized(dedupeLock) {
            Long previous = recent.get(key);
            recent.put(key, now);
            while(recent.size() > properties.getClientDedupeEntries()) {
                var iterator = recent.keySet().iterator();
                if(!iterator.hasNext()) break;
                iterator.next(); iterator.remove();
            }
            return previous != null && now - previous <= properties.getClientDedupeWindowMillis();
        }
    }

    /** Descripción: Ejecuta la operación «dedupeKey» correspondiente a la responsabilidad de AuditClientActivityService. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; activityType: dato de entrada necesario para la operación; moduleId: identificador que delimita el recurso objetivo; action: dato de entrada necesario para la operación; resourceId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private String dedupeKey(AuditClientActivityRequest request, String activityType, String moduleId, String action, String resourceId) {
        AuditActor actor = actorContext.currentActor();
        String actorId = actor == null ? "local-user" : safeText(actor.actorId(), 80, "local-user");
        ResourceScope scope = workspaceContext == null ? ResourceScope.local() : workspaceContext.currentScope();
        String workspaceId = scope == null ? ResourceScope.LOCAL_WORKSPACE_ID : scope.workspaceId();
        String supplied = safeText(request.dedupeKey(), 160, "");
        return workspaceId + '|' + actorId + '|' + activityType + '|' + moduleId + '|' + action + '|' + resourceId + '|' + supplied;
    }

    /** Descripción: Ejecuta la operación «importance» correspondiente a la responsabilidad de AuditClientActivityService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private AuditImportance importance(String value) {
        if(value == null || value.isBlank()) return AuditImportance.INFO;
        try { return AuditImportance.valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch(IllegalArgumentException ignored) { return AuditImportance.INFO; }
    }
    /** Descripción: Ejecuta la operación «identifier» correspondiente a la responsabilidad de AuditClientActivityService. Entradas: value: valor de entrada que debe procesarse; max: dato de entrada necesario para la operación; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String identifier(String value, int max, String fallback) {
        String normalized = value == null ? "" : value.trim().replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        if(normalized.isBlank()) normalized = fallback;
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }
    /** Descripción: Ejecuta la operación «safeText» correspondiente a la responsabilidad de AuditClientActivityService. Entradas: value: valor de entrada que debe procesarse; max: dato de entrada necesario para la operación; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String safeText(String value, int max, String fallback) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }
}
