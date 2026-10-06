package com.example.editor.definition.runtime.session;

import java.util.Map;

/** Evento compacto retenido hasta que la copia de trabajo se confirma. */
public record PendingEditorEvent(String eventTypeId,EditorSessionEventContext context,String elementId,String typeId,Map<String,Object> payload) {
    public PendingEditorEvent {
        eventTypeId=normalize(eventTypeId);
        if(eventTypeId.isBlank()) throw new IllegalArgumentException("Event type id is required");
        if(context==null) throw new IllegalArgumentException("Event context is required");
        elementId=normalize(elementId);
        typeId=normalize(typeId);
        payload=payload==null?Map.of():Map.copyOf(payload);
    }
    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de PendingEditorEvent. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalize(String value) { return value==null?"":value.trim(); }
}
