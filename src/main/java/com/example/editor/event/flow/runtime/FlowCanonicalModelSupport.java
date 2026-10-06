package com.example.editor.event.flow.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.editor.event.CanonicalEvent;
import com.example.editor.validation.model.CanonicalModelDocument;
import tools.jackson.databind.ObjectMapper;

/** Convención portable para transportar un modelo canónico dentro del payload de un evento. */
final class FlowCanonicalModelSupport {
    static final String MODEL_KEY = "_hydraModel";
    private final ObjectMapper mapper;
    /** Descripción: Inicializa una instancia de FlowCanonicalModelSupport con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ FlowCanonicalModelSupport(ObjectMapper mapper) { this.mapper = mapper; }

    /** Descripción: Ejecuta la operación «model» correspondiente a la responsabilidad de FlowCanonicalModelSupport. Entradas: event: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ CanonicalModelDocument model(CanonicalEvent event) {
        Object value = event.payload().get(MODEL_KEY);
        if(value == null) value = event.payload().get("model");
        if(value == null) throw new IllegalArgumentException("Flow step requires a canonical model in payload._hydraModel");
        if(value instanceof CanonicalModelDocument document) return document;
        return mapper.convertValue(value, CanonicalModelDocument.class);
    }

    /** Descripción: Ejecuta la operación «target» correspondiente a la responsabilidad de FlowCanonicalModelSupport. Entradas: context: dato de entrada necesario para la operación; model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ String target(FlowExternalStepContext context, CanonicalModelDocument model) {
        String explicit = text(context.node().configuration().get("targetElementId"));
        if(!explicit.isBlank()) return explicit;
        String path = text(context.node().configuration().get("targetElementPath"));
        if(!path.isBlank()) {
            Object value = EventValueResolver.read(context.event(), path);
            if(value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
        }
        Object payloadTarget = context.event().payload().get("targetElementId");
        if(payloadTarget != null && !String.valueOf(payloadTarget).isBlank()) return String.valueOf(payloadTarget);
        return model.rootElementIds().stream().findFirst().orElse("");
    }

    /** Descripción: Ejecuta la operación «selection» correspondiente a la responsabilidad de FlowCanonicalModelSupport. Entradas: context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ List<String> selection(FlowExternalStepContext context) {
        Object raw = context.node().configuration().get("selectionElementIds");
        if(raw instanceof List<?> values) return values.stream().map(String::valueOf).filter(value -> !value.isBlank()).toList();
        Object payload = context.event().payload().get("selectionElementIds");
        if(payload instanceof List<?> values) return values.stream().map(String::valueOf).filter(value -> !value.isBlank()).toList();
        return List.of();
    }

    /** Descripción: Ejecuta la operación «arguments» correspondiente a la responsabilidad de FlowCanonicalModelSupport. Entradas: context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ Map<String,Object> arguments(FlowExternalStepContext context) {
        Map<String,Object> result = new LinkedHashMap<>();
        Object raw = context.node().configuration().get("arguments");
        if(raw instanceof Map<?,?> values) values.forEach((key, value) -> result.put(String.valueOf(key), value));
        result.putAll(context.variables());
        return result;
    }

    /** Descripción: Actualiza el estado gestionado por FlowCanonicalModelSupport mediante la operación «updates». Entradas: outcome: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ Map<String,Object> updates(com.example.editor.definition.runtime.session.tool.EditorSessionToolOutcome outcome) {
        Map<String,Object> result = new LinkedHashMap<>();
        result.put(MODEL_KEY, outcome.document());
        if(outcome.returnValue() != null) result.put("toolReturnValue", outcome.returnValue());
        if(!outcome.messages().isEmpty()) result.put("toolMessages", new ArrayList<>(outcome.messages()));
        if(outcome.validationReport() != null) result.put("validationReport", outcome.validationReport());
        return result;
    }

    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de FlowCanonicalModelSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
}
