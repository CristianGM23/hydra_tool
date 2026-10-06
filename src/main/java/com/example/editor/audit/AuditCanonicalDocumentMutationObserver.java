package com.example.editor.audit;

import java.lang.reflect.Method;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.editor.persistence.CanonicalDocumentMutation;
import com.example.editor.persistence.CanonicalDocumentMutationObserver;
import com.example.editor.persistence.CanonicalDocumentMutationKind;

/** Convierte cualquier escritura de un store canónico en una actividad legible sin conocer su dominio. */
@Component
public class AuditCanonicalDocumentMutationObserver implements CanonicalDocumentMutationObserver {
    private final AuditEventPublisher publisher;
    private final AuditCapturePolicy capturePolicy;

    /** Descripción: Inicializa una instancia de AuditCanonicalDocumentMutationObserver con sus dependencias y datos de entrada. Entradas: publisher: dato de entrada necesario para la operación; capturePolicy: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public AuditCanonicalDocumentMutationObserver(AuditEventPublisher publisher, AuditCapturePolicy capturePolicy) {
        this.publisher = publisher;
        this.capturePolicy = capturePolicy;
    }

    @Override
    /** Descripción: Ejecuta la operación «onMutation» correspondiente a la responsabilidad de AuditCanonicalDocumentMutationObserver. Entradas: mutation: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void onMutation(CanonicalDocumentMutation mutation) {
        if(mutation == null || !capturePolicy.capturesNamespace(mutation.namespace())) return;
        Object preferred = mutation.after() != null ? mutation.after() : mutation.before();
        String displayName = inferDisplayName(preferred, mutation.documentId());
        String resourceType = humanize(lastToken(mutation.namespace()));
        String moduleId = moduleId(mutation.namespace());
        String action = action(mutation.kind());
        String activityType = mutation.namespace() + "." + pastTense(mutation.kind());
        AuditRequestContext.markBusinessMutation();
        AuditResource resource = new AuditResource(mutation.namespace(), resourceType, mutation.documentId(), displayName);
        publisher.publish(AuditEvent.builder(activityType, moduleId, action, resource)
                .before(mutation.before()).after(mutation.after())
                .metadata(Map.of("namespace", mutation.namespace(), "workspaceId", mutation.scope().workspaceId()))
                .correlationId(AuditRequestContext.correlationId()).build());
    }

    /** Descripción: Ejecuta la operación «inferDisplayName» correspondiente a la responsabilidad de AuditCanonicalDocumentMutationObserver. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String inferDisplayName(Object value, String fallback) {
        if(value instanceof Map<?, ?> map) {
            for(String key : new String[] { "name", "title", "label", "id" }) {
                Object candidate = map.get(key);
                if(candidate != null && !String.valueOf(candidate).isBlank()) return String.valueOf(candidate);
            }
        }
        if(value != null) {
            for(String methodName : new String[] { "name", "title", "label", "id", "getName", "getTitle", "getLabel", "getId" }) {
                try {
                    Method method = value.getClass().getMethod(methodName);
                    if(method.getParameterCount() != 0) continue;
                    Object candidate = method.invoke(value);
                    if(candidate != null && !String.valueOf(candidate).isBlank()) return String.valueOf(candidate);
                } catch(ReflectiveOperationException | SecurityException ignored) { /* fallback estable */ }
            }
        }
        return fallback;
    }
    /** Descripción: Ejecuta la operación «moduleId» correspondiente a la responsabilidad de AuditCanonicalDocumentMutationObserver. Entradas: namespace: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String moduleId(String namespace) { int dot = namespace.indexOf('.'); return dot < 0 ? namespace : namespace.substring(0, dot); }
    /** Descripción: Ejecuta la operación «lastToken» correspondiente a la responsabilidad de AuditCanonicalDocumentMutationObserver. Entradas: namespace: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String lastToken(String namespace) { int dot = namespace.lastIndexOf('.'); return dot < 0 ? namespace : namespace.substring(dot + 1); }
    /** Descripción: Ejecuta la operación «humanize» correspondiente a la responsabilidad de AuditCanonicalDocumentMutationObserver. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String humanize(String value) { String text = value.replace('-', ' ').replace('_', ' ').trim(); return text.isEmpty() ? "resource" : text; }
    /** Descripción: Ejecuta la operación «pastTense» correspondiente a la responsabilidad de AuditCanonicalDocumentMutationObserver. Entradas: kind: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String pastTense(CanonicalDocumentMutationKind kind) { return switch(kind) { case CREATE -> "created"; case UPDATE -> "updated"; case DELETE -> "deleted"; }; }
    /** Descripción: Ejecuta la operación «action» correspondiente a la responsabilidad de AuditCanonicalDocumentMutationObserver. Entradas: kind: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String action(CanonicalDocumentMutationKind kind) { return switch(kind) { case CREATE -> AuditActions.CREATE; case UPDATE -> AuditActions.UPDATE; case DELETE -> AuditActions.DELETE; }; }
}
