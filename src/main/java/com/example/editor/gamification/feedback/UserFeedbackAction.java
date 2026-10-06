package com.example.editor.gamification.feedback;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record UserFeedbackAction(String id, String label, String kind, Map<String, String> parameters) {
    public UserFeedbackAction {
        id = text(id); label = text(label); kind = text(kind);
        parameters = parameters == null || parameters.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    }
    /** Descripción: Normaliza texto opcional usado por una acción de retroalimentación. Entradas: value: valor que debe normalizarse. Salida: texto no nulo y sin espacios exteriores. */
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de UserFeedbackAction. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value) { return value == null ? "" : value.trim(); }
}
