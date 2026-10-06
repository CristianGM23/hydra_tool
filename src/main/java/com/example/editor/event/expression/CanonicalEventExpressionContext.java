package com.example.editor.event.expression;

import java.util.LinkedHashMap;
import java.util.Map;

import com.example.editor.event.CanonicalEvent;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.semantic.expression.ExpressionEvaluationContext;
import com.example.editor.validation.model.CanonicalModelDocument;

/** Construye un contexto neutral de expresiones a partir de un evento canónico. */
public final class CanonicalEventExpressionContext {
    /** Descripción: Inicializa una instancia utilitaria de CanonicalEventExpressionContext. Entradas: no requiere parámetros de entrada. Salida: no crea instancias públicas. */ private CanonicalEventExpressionContext() { }

    /** Crea un contexto de evaluación sin inventar instancias de modelo: el evento se expone mediante variables neutrales. */
    /** Descripción: Crea el contexto neutral de evaluación para un evento canónico. Entradas: metamodel: metamodelo canónico; event: evento a exponer como variables. Salida: devuelve un contexto ejecutable por semantic.expression. */ public static ExpressionEvaluationContext create(MetaModel metamodel, CanonicalEvent event) {
        if(metamodel == null) throw new IllegalArgumentException("metamodel must not be null");
        if(event == null) throw new IllegalArgumentException("event must not be null");
        String scopeId = event.context() == null || event.context().metamodelId().isBlank() ? metamodel.id() : event.context().metamodelId();
        CanonicalModelDocument model = new CanonicalModelDocument(null, "event-expression-runtime", scopeId, "",
                "Event expression runtime", java.util.List.of(), java.util.List.of(), java.util.List.of(), Map.of());
        return new ExpressionEvaluationContext(metamodel, model, "", variables(event));
    }

    /** Variables portables comunes a EventBinding, EventFlow y Gamification. */
    /** Descripción: Proyecta un evento a variables neutrales reutilizables. Entradas: event: evento canónico. Salida: devuelve un mapa inmutable de variables y rutas. */ public static Map<String, Object> variables(CanonicalEvent event) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("event", event);
        values.put("payload", event.payload());
        values.put("metadata", event.metadata());
        values.put("subject", event.subject());
        values.put("actor", event.actor());
        values.put("context", event.context());
        values.put("source", event.source());
        values.put("eventId", event.id());
        values.put("eventTypeId", event.eventTypeId());
        values.put("correlationId", event.correlationId());
        values.put("causationId", event.causationId());
        if(event.actor() != null) {
            values.put("actor.id", event.actor().id());
            values.put("actor.kind", event.actor().kind().name());
            values.put("actor.displayName", event.actor().displayName());
        }
        if(event.subject() != null) {
            values.put("subject.documentId", event.subject().documentId());
            values.put("subject.elementId", event.subject().elementId());
            values.put("subject.typeId", event.subject().typeId());
            values.put("subject.featureId", event.subject().featureId());
            values.put("subject.path", event.subject().path());
        }
        if(event.context() != null) {
            values.put("context.metamodelId", event.context().metamodelId());
            values.put("context.modelId", event.context().modelId());
            values.put("context.editorId", event.context().editorId());
            values.put("context.sessionId", event.context().sessionId());
        }
        if(event.source() != null) {
            values.put("source.component", event.source().component());
            values.put("source.module", event.source().module());
            values.put("source.instanceId", event.source().instanceId());
            values.put("source.version", event.source().version());
        }
        flatten("payload", event.payload(), values, 0);
        flatten("metadata", event.metadata(), values, 0);
        return java.util.Collections.unmodifiableMap(values);
    }

    /** Descripción: Aplana mapas anidados con profundidad limitada. Entradas: prefix, value, output y depth. Salida: completa el mapa de variables sin efectos externos. */ private static void flatten(String prefix, Object value, Map<String, Object> output, int depth) {
        if(depth > 8 || !(value instanceof Map<?, ?> map)) return;
        for(var entry : map.entrySet()) {
            String key = prefix + "." + entry.getKey();
            output.put(key, entry.getValue());
            flatten(key, entry.getValue(), output, depth + 1);
        }
    }
}
