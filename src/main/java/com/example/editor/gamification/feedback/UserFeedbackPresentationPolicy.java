package com.example.editor.gamification.feedback;

import java.util.Set;

public record UserFeedbackPresentationPolicy(boolean enabled, Set<UserFeedbackSurface> surfaces, int durationMillis,
        boolean persistActivity, boolean showPoints, boolean showProgress, int maxVisible, int dedupeWindowSeconds,
        int aggregateWindowMillis, boolean includeRejected) {
    public UserFeedbackPresentationPolicy {
        surfaces = surfaces == null || surfaces.isEmpty() ? Set.of(UserFeedbackSurface.TOAST, UserFeedbackSurface.ACTIVITY) : Set.copyOf(surfaces);
        durationMillis = Math.max(1000, Math.min(durationMillis <= 0 ? 4000 : durationMillis, 30000));
        maxVisible = Math.max(1, Math.min(maxVisible <= 0 ? 3 : maxVisible, 20));
        dedupeWindowSeconds = Math.max(0, Math.min(dedupeWindowSeconds, 3600));
        aggregateWindowMillis = Math.max(0, Math.min(aggregateWindowMillis, 60000));
    }
    /** Descripción: Crea la política segura por defecto para presentar retroalimentación sin saturar la interfaz. Entradas: no requiere parámetros. Salida: política portable con superficies y límites conservadores. */
    /** Descripción: Ejecuta la operación «defaults» correspondiente a la responsabilidad de UserFeedbackPresentationPolicy. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static UserFeedbackPresentationPolicy defaults() {
        return new UserFeedbackPresentationPolicy(true, Set.of(UserFeedbackSurface.TOAST, UserFeedbackSurface.ACTIVITY), 4000, true, true, true, 3, 3, 750, false);
    }
}
