package com.example.editor.gamification.feedback;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import com.example.editor.gamification.GamificationBindingModel;
import com.example.editor.gamification.GamificationDefinitionModel;

public final class GamificationFeedbackPolicyResolver {
    /** Descripción: Evita instancias de una utilidad puramente estática. Entradas: no requiere parámetros. Salida: no devuelve valor. */
    /** Descripción: Inicializa una instancia de GamificationFeedbackPolicyResolver con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private GamificationFeedbackPolicyResolver() { }

    /** Descripción: Resuelve la política global y las sobreescrituras por regla con valores acotados y fallbacks seguros. Entradas: definitions, bindings y ruleId. Salida: política de presentación efectiva. */
    /** Descripción: Ejecuta la operación «policy» correspondiente a la responsabilidad de GamificationFeedbackPolicyResolver. Entradas: definitions: dato de entrada necesario para la operación; bindings: dato de entrada necesario para la operación; ruleId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public static UserFeedbackPresentationPolicy policy(GamificationDefinitionModel definitions, GamificationBindingModel bindings, String ruleId) {
        UserFeedbackPresentationPolicy defaults = UserFeedbackPresentationPolicy.defaults();
        Map<String,String> base = definitions == null ? Map.of() : definitions.annotations();
        Map<String,String> overrides = bindings == null ? Map.of() : bindings.annotations();
        String prefix = "hydra.feedback.rule." + (ruleId == null ? "" : ruleId) + ".";
        boolean enabled = bool(overrides.get(prefix + "enabled"), bool(base.get("hydra.feedback.enabled"), defaults.enabled()));
        Set<UserFeedbackSurface> surfaces = surfaces(first(overrides.get(prefix + "surfaces"), base.get("hydra.feedback.surfaces")), defaults.surfaces());
        int duration = boundedInteger(first(overrides.get(prefix + "durationMillis"), base.get("hydra.feedback.durationMillis")), defaults.durationMillis(), 1000, 30000);
        boolean persist = bool(first(overrides.get(prefix + "persistActivity"), base.get("hydra.feedback.persistActivity")), defaults.persistActivity());
        boolean showPoints = bool(first(overrides.get(prefix + "showPoints"), base.get("hydra.feedback.showPoints")), defaults.showPoints());
        boolean showProgress = bool(first(overrides.get(prefix + "showProgress"), base.get("hydra.feedback.showProgress")), defaults.showProgress());
        int maxVisible = boundedInteger(first(overrides.get(prefix + "maxVisible"), base.get("hydra.feedback.maxVisible")), defaults.maxVisible(), 1, 20);
        int dedupe = boundedInteger(first(overrides.get(prefix + "dedupeWindowSeconds"), base.get("hydra.feedback.dedupeWindowSeconds")), defaults.dedupeWindowSeconds(), 0, 3600);
        int aggregate = boundedInteger(first(overrides.get(prefix + "aggregateWindowMillis"), base.get("hydra.feedback.aggregateWindowMillis")), defaults.aggregateWindowMillis(), 0, 60000);
        boolean rejected = bool(first(overrides.get(prefix + "includeRejected"), base.get("hydra.feedback.includeRejected")), defaults.includeRejected());
        return new UserFeedbackPresentationPolicy(enabled, surfaces, duration, persist, showPoints, showProgress, maxVisible, dedupe, aggregate, rejected);
    }

    /** Descripción: Obtiene un texto configurable por regla y aplica un fallback cuando no existe. Entradas: bindings, ruleId, key y fallback. Salida: texto efectivo. */
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de GamificationFeedbackPolicyResolver. Entradas: bindings: dato de entrada necesario para la operación; ruleId: identificador que delimita el recurso objetivo; key: identificador que delimita el recurso objetivo; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String text(GamificationBindingModel bindings, String ruleId, String key, String fallback) {
        if (bindings == null) return fallback;
        String value = bindings.annotations().get("hydra.feedback.rule." + ruleId + "." + key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }
    /** Descripción: Elige el primer texto no vacío. Entradas: a y b. Salida: a cuando tiene contenido; en otro caso b. */
    /** Descripción: Ejecuta la operación «first» correspondiente a la responsabilidad de GamificationFeedbackPolicyResolver. Entradas: a: dato de entrada necesario para la operación; b: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String first(String a, String b) { return a == null || a.isBlank() ? b : a; }
    /** Descripción: Convierte texto opcional a booleano conservando el fallback. Entradas: value y fallback. Salida: valor booleano efectivo. */
    /** Descripción: Ejecuta la operación «bool» correspondiente a la responsabilidad de GamificationFeedbackPolicyResolver. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean bool(String value, boolean fallback) { return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value); }
    /** Descripción: Convierte un entero configurable y lo acota sin permitir que valores extremos invaliden la política. Entradas: value, fallback, minimum y maximum. Salida: entero válido dentro del rango. */
    /** Descripción: Ejecuta la operación «boundedInteger» correspondiente a la responsabilidad de GamificationFeedbackPolicyResolver. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación; minimum: dato de entrada necesario para la operación; maximum: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static int boundedInteger(String value, int fallback, int minimum, int maximum) {
        if (value == null || value.isBlank()) return fallback;
        try { return Math.max(minimum, Math.min(Integer.parseInt(value.trim()), maximum)); }
        catch (NumberFormatException exception) { return fallback; }
    }
    /** Descripción: Convierte la lista textual de superficies en un conjunto tipado ignorando valores desconocidos. Entradas: value y fallback. Salida: superficies efectivas. */
    /** Descripción: Ejecuta la operación «surfaces» correspondiente a la responsabilidad de GamificationFeedbackPolicyResolver. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static Set<UserFeedbackSurface> surfaces(String value, Set<UserFeedbackSurface> fallback) {
        if (value == null || value.isBlank()) return fallback;
        Set<UserFeedbackSurface> result = new LinkedHashSet<>();
        for (String token : value.split(",")) try { result.add(UserFeedbackSurface.valueOf(token.trim().toUpperCase())); } catch (IllegalArgumentException ignored) { continue; }
        return result.isEmpty() ? fallback : Set.copyOf(result);
    }
}
