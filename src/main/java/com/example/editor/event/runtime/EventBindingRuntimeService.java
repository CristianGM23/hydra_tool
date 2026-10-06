package com.example.editor.event.runtime;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.example.editor.event.CanonicalEvent;
import com.example.editor.event.EventBinding;
import com.example.editor.event.EventBindingErrorPolicy;
import com.example.editor.event.EventModelApplicationException;
import com.example.editor.event.EventModelApplicationService;
import com.example.editor.event.EventPayloadMapping;
import com.example.editor.event.EventSource;
import com.example.editor.event.bus.CanonicalEventBus;
import com.example.editor.event.bus.EventSubscription;
import com.example.editor.event.expression.CanonicalEventExpressionContext;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.semantic.expression.ExpressionDefinition;
import com.example.editor.semantic.expression.ExpressionEvaluationContext;
import com.example.editor.semantic.expression.ExpressionEvaluator;
import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.validation.expression.persistence.ExpressionModelRepository;
import com.example.editor.validation.model.CanonicalModelDocument;
import jakarta.annotation.PreDestroy;

/**
 * Ejecuta EventBinding sobre cualquier ámbito semántico canónico, incluidos
 * {@code grammar.<id>}. Los eventos derivados vuelven al bus canónico y por
 * tanto participan en EventFlow y gamificación exactamente igual que un evento
 * emitido por el editor.
 */
@Service
public class EventBindingRuntimeService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventBindingRuntimeService.class);
    private static final int MAX_BINDING_DEPTH = 32;
    private static final int MAX_DISABLED_BINDINGS = 1024;
    private static final String DEPTH = "hydra.binding.depth";
    private static final String BINDING_ID = "hydra.binding.id";
    private static final String ENABLED = "hydra.eventBindingsEnabled";

    private final CanonicalEventBus eventBus;
    private final EventModelApplicationService eventModels;
    private final ExpressionModelRepository expressionModels;
    private final CanonicalMetamodelApplicationService metamodels;
    private final ExpressionEvaluator evaluator;
    private final Set<String> disabledBindings = new CopyOnWriteArraySet<>();
    private final EventSubscription subscription;

    /** Descripción: Inicializa una instancia de EventBindingRuntimeService con sus dependencias y datos de entrada. Entradas: eventBus: dato de entrada necesario para la operación; eventModels: dato de entrada necesario para la operación; expressionModels: dato de entrada necesario para la operación; metamodels: dato de entrada necesario para la operación; evaluator: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EventBindingRuntimeService(CanonicalEventBus eventBus, EventModelApplicationService eventModels,
            ExpressionModelRepository expressionModels, CanonicalMetamodelApplicationService metamodels,
            ExpressionEvaluator evaluator) {
        this.eventBus = eventBus;
        this.eventModels = eventModels;
        this.expressionModels = expressionModels;
        this.metamodels = metamodels;
        this.evaluator = evaluator;
        this.subscription = eventBus.subscribe(event -> true, this::process);
    }

    /** Procesa un evento; es público para pruebas y para runtimes embebidos. */
    /** Descripción: Procesa la operación «process» coordinando las responsabilidades de EventBindingRuntimeService. Entradas: event: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void process(CanonicalEvent event) {
        if(event == null || "false".equalsIgnoreCase(event.metadata().get(ENABLED))) return;
        String scopeId = event.context().metamodelId();
        if(scopeId == null || scopeId.isBlank()) return;
        int depth = integer(event.metadata().get(DEPTH), 0);
        if(depth >= MAX_BINDING_DEPTH) {
            LOGGER.warn("Event binding depth limit reached for scope {} and event {}", scopeId, event.id());
            return;
        }

        var loaded = loadEventModel(scopeId);
        if(loaded == null) return;
        ExpressionModel expressions = loadExpressions(scopeId, loaded.bindings().expressionModelId());
        CanonicalModelDocument evaluationModel = new CanonicalModelDocument(null, "event-binding-runtime", scopeId, "",
                "Event binding runtime", java.util.List.of(), java.util.List.of(), java.util.List.of(), Map.of());
        var metamodel = metamodels.findById(scopeId).metamodel();

        for(EventBinding binding : loaded.bindings().bindings()) {
            String key = scopeId + "|" + binding.id();
            if(!binding.enabled() || disabledBindings.contains(key) || !binding.sourceEventTypeId().equals(event.eventTypeId())) continue;
            try {
                if(!conditionMatches(binding, expressions, metamodel, evaluationModel, event)) continue;
                Map<String, Object> payload = new LinkedHashMap<>();
                for(EventPayloadMapping mapping : binding.payloadMappings()) {
                    payload.put(mapping.targetFieldId(), mappingValue(mapping, expressions, metamodel, evaluationModel, event));
                }
                Map<String, String> metadata = new LinkedHashMap<>(event.metadata());
                metadata.put(DEPTH, String.valueOf(depth + 1));
                metadata.put(BINDING_ID, binding.id());
                String targetVersion = loaded.definitions().findType(binding.targetEventTypeId())
                        .map(type -> type.version()).orElse("1.0");
                CanonicalEvent derived = new CanonicalEvent(null, "binding:" + UUID.randomUUID(), binding.targetEventTypeId(),
                        targetVersion, Instant.now(), new EventSource("EVENT_BINDING", "event", binding.id(), "1.0"),
                        event.context(), event.actor(), event.subject(), event.transactionId(),
                        event.correlationId().isBlank() ? event.id() : event.correlationId(), event.id(), payload, metadata);
                eventBus.publish(derived);
            } catch(RuntimeException exception) {
                handleFailure(key, binding, event, exception);
            }
        }
    }

    /** Permite reactivar bindings deshabilitados en memoria tras corregir su configuración. */
    /** Descripción: Ejecuta la operación «refresh» correspondiente a la responsabilidad de EventBindingRuntimeService. Entradas: semanticScopeId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void refresh(String semanticScopeId) {
        if(semanticScopeId == null || semanticScopeId.isBlank()) return;
        String prefix = semanticScopeId + "|";
        disabledBindings.removeIf(value -> value.startsWith(prefix));
    }

    /** Descripción: Obtiene la información correspondiente a «loadEventModel» dentro de EventBindingRuntimeService. Entradas: scopeId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private com.example.editor.event.EventModelLoadResult loadEventModel(String scopeId) {
        try { return eventModels.find(scopeId); }
        catch(EventModelApplicationException exception) {
            if(exception.getReason() == EventModelApplicationException.Reason.NOT_FOUND) return null;
            throw exception;
        }
    }

    /** Descripción: Obtiene la información correspondiente a «loadExpressions» dentro de EventBindingRuntimeService. Entradas: scopeId: identificador que delimita el recurso objetivo; configuredId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionModel loadExpressions(String scopeId, String configuredId) {
        if(configuredId == null || configuredId.isBlank()) return null;
        return expressionModels.findById(scopeId)
                .map(stored -> stored.expressionModel())
                .filter(model -> configuredId.equals(model.id()))
                .orElseThrow(() -> new IllegalStateException("Configured expression model was not found for " + scopeId));
    }

    /** Descripción: Ejecuta la operación «conditionMatches» correspondiente a la responsabilidad de EventBindingRuntimeService. Entradas: binding: dato de entrada necesario para la operación; expressions: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; model: dato de entrada necesario para la operación; event: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean conditionMatches(EventBinding binding, ExpressionModel expressions,
            com.example.editor.metamodel.core.MetaModel metamodel, CanonicalModelDocument model, CanonicalEvent event) {
        if(binding.conditionExpressionId().isBlank()) return true;
        Object value = evaluate(binding.conditionExpressionId(), expressions, metamodel, model, event);
        return value instanceof Boolean booleanValue && booleanValue;
    }

    /** Descripción: Ejecuta la operación «mappingValue» correspondiente a la responsabilidad de EventBindingRuntimeService. Entradas: mapping: dato de entrada necesario para la operación; expressions: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; model: dato de entrada necesario para la operación; event: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Object mappingValue(EventPayloadMapping mapping, ExpressionModel expressions,
            com.example.editor.metamodel.core.MetaModel metamodel, CanonicalModelDocument model, CanonicalEvent event) {
        if(!mapping.sourcePath().isBlank()) return EventBindingValueResolver.read(event, mapping.sourcePath());
        if(!mapping.expressionId().isBlank()) return evaluate(mapping.expressionId(), expressions, metamodel, model, event);
        return mapping.fixedValue();
    }

    /** Descripción: Ejecuta la operación «evaluate» correspondiente a la responsabilidad de EventBindingRuntimeService. Entradas: expressionId: identificador que delimita el recurso objetivo; expressions: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; model: dato de entrada necesario para la operación; event: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Object evaluate(String expressionId, ExpressionModel expressions,
            com.example.editor.metamodel.core.MetaModel metamodel, CanonicalModelDocument model, CanonicalEvent event) {
        if(expressions == null) throw new IllegalStateException("Binding references expressions but no expression model is configured");
        ExpressionDefinition expression = expressions.findExpression(expressionId)
                .orElseThrow(() -> new IllegalStateException("Binding expression was not found: " + expressionId));
        ExpressionEvaluationContext context = CanonicalEventExpressionContext.create(metamodel, event);
        return evaluator.evaluate(expression, context).value();
    }

    /** Descripción: Procesa la operación «handleFailure» coordinando las responsabilidades de EventBindingRuntimeService. Entradas: key: identificador que delimita el recurso objetivo; binding: dato de entrada necesario para la operación; event: dato de entrada necesario para la operación; exception: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void handleFailure(String key, EventBinding binding, CanonicalEvent event, RuntimeException exception) {
        if(binding.errorPolicy() == EventBindingErrorPolicy.DISABLE_BINDING) {
            if(disabledBindings.size() >= MAX_DISABLED_BINDINGS) {
                disabledBindings.stream().findFirst().ifPresent(disabledBindings::remove);
            }
            disabledBindings.add(key);
        }
        if(binding.errorPolicy() != EventBindingErrorPolicy.SKIP) {
            LOGGER.warn("Event binding {} failed for event {} ({})", binding.id(), event.id(), binding.errorPolicy(), exception);
        }
    }

    /** Descripción: Ejecuta la operación «integer» correspondiente a la responsabilidad de EventBindingRuntimeService. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int integer(String value, int fallback) {
        try { return value == null || value.isBlank() ? fallback : Integer.parseInt(value); }
        catch(NumberFormatException exception) { return fallback; }
    }

    @PreDestroy
    /** Descripción: Elimina o libera el recurso gestionado por «close» de forma controlada. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void close() { subscription.close(); }
}
