package com.example.editor.event;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.example.editor.validation.expression.ExpressionModel;

/** Comprueba referencias, duplicados y compatibilidad de los contratos de eventos. */
@Component
public class EventModelChecker {
    /** Descripción: Valida las condiciones necesarias para «check» y rechaza entradas no válidas. Entradas: definitions: dato de entrada necesario para la operación; bindings: dato de entrada necesario para la operación; expressions: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EventModelCheckReport check(EventDefinitionModel definitions, EventBindingModel bindings, ExpressionModel expressions) {
        Set<EventDiagnostic> diagnostics = new LinkedHashSet<>();
        Set<String> typeIds = new HashSet<>();
        for(EventTypeDefinition type : definitions.eventTypes()) {
            if(!typeIds.add(type.id())) diagnostics.add(error("DUPLICATE_EVENT_TYPE", type.id(), "eventTypes", "Duplicate event type id"));
            if(ReservedEventCatalog.isReserved(type.id())) diagnostics.add(error("RESERVED_EVENT_TYPE", type.id(), "eventTypes", "Reserved core event types cannot be redefined"));
            if(!type.namespace().equals(definitions.namespace()) && !type.namespace().startsWith(definitions.namespace() + ".")) diagnostics.add(warning("FOREIGN_NAMESPACE", type.id(), "namespace", "Event type uses a namespace outside the model namespace"));
            Set<String> fieldIds = new HashSet<>();
            Set<String> fieldNames = new HashSet<>();
            for(EventFieldDefinition field : type.fields()) {
                if(!fieldIds.add(field.id())) diagnostics.add(error("DUPLICATE_FIELD_ID", type.id(), "fields", "Duplicate field id: " + field.id()));
                if(!fieldNames.add(field.name())) diagnostics.add(error("DUPLICATE_FIELD_NAME", type.id(), "fields", "Duplicate field name: " + field.name()));
            }
        }
        if(bindings != null) {
            if(!definitions.id().equals(bindings.eventDefinitionModelId())) diagnostics.add(error("DEFINITION_MODEL_MISMATCH", bindings.id(), "eventDefinitionModelId", "Binding model must reference the supplied definition model"));
            Set<String> bindingIds = new HashSet<>();
            Set<String> knownTypes = new HashSet<>(ReservedEventCatalog.eventTypeIds());
            knownTypes.addAll(typeIds);
            Set<String> expressionIds = expressions == null ? Set.of() : expressions.expressions().stream().map(expression -> expression.id()).collect(java.util.stream.Collectors.toSet());
            for(EventBinding binding : bindings.bindings()) {
                if(!bindingIds.add(binding.id())) diagnostics.add(error("DUPLICATE_BINDING", binding.id(), "bindings", "Duplicate binding id"));
                if(!knownTypes.contains(binding.sourceEventTypeId())) diagnostics.add(error("UNKNOWN_SOURCE_EVENT", binding.id(), "sourceEventTypeId", "Source event type was not found"));
                if(!typeIds.contains(binding.targetEventTypeId())) diagnostics.add(error("UNKNOWN_TARGET_EVENT", binding.id(), "targetEventTypeId", "Target event must be a custom event type"));
                if(!binding.conditionExpressionId().isBlank() && !expressionIds.contains(binding.conditionExpressionId())) diagnostics.add(error("UNKNOWN_CONDITION_EXPRESSION", binding.id(), "conditionExpressionId", "Condition expression was not found"));
                EventTypeDefinition target = definitions.findType(binding.targetEventTypeId()).orElse(null);
                if(target != null) checkMappings(target, binding, diagnostics);
            }
        }
        return new EventModelCheckReport(definitions.id(), bindings == null ? "" : bindings.id(), List.copyOf(diagnostics));
    }
    /** Descripción: Valida las condiciones necesarias para «checkMappings» y rechaza entradas no válidas. Entradas: target: dato de entrada necesario para la operación; binding: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkMappings(EventTypeDefinition target, EventBinding binding, Set<EventDiagnostic> diagnostics) {
        Set<String> targetFields = target.fields().stream().map(EventFieldDefinition::id).collect(java.util.stream.Collectors.toSet());
        Set<String> mapped = new HashSet<>();
        for(EventPayloadMapping mapping : binding.payloadMappings()) {
            if(!targetFields.contains(mapping.targetFieldId())) diagnostics.add(error("UNKNOWN_TARGET_FIELD", binding.id(), "payloadMappings", "Mapped target field does not exist: " + mapping.targetFieldId()));
            if(!mapped.add(mapping.targetFieldId())) diagnostics.add(error("DUPLICATE_TARGET_MAPPING", binding.id(), "payloadMappings", "Target field is mapped more than once: " + mapping.targetFieldId()));
        }
        for(EventFieldDefinition field : target.fields()) if(field.required() && !mapped.contains(field.id())) diagnostics.add(error("REQUIRED_FIELD_NOT_MAPPED", binding.id(), "payloadMappings", "Required target field is not mapped: " + field.id()));
    }
    /** Descripción: Ejecuta la operación «error» correspondiente a la responsabilidad de EventModelChecker. Entradas: code: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; path: ruta o ubicación utilizada por la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EventDiagnostic error(String code, String id, String path, String message) { return new EventDiagnostic(EventDiagnosticSeverity.ERROR, code, id, path, message); }
    /** Descripción: Ejecuta la operación «warning» correspondiente a la responsabilidad de EventModelChecker. Entradas: code: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; path: ruta o ubicación utilizada por la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EventDiagnostic warning(String code, String id, String path, String message) { return new EventDiagnostic(EventDiagnosticSeverity.WARNING, code, id, path, message); }
}
