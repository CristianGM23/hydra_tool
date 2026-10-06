package com.example.editor.gamification.runtime;

import java.util.Map;

import com.example.editor.event.CanonicalEvent;
import com.example.editor.event.expression.CanonicalEventExpressionContext;
import com.example.editor.gamification.GamificationRule;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.semantic.expression.BinaryExpressionNode;
import com.example.editor.semantic.expression.BinaryExpressionOperator;
import com.example.editor.semantic.expression.ExpressionDefinition;
import com.example.editor.semantic.expression.ExpressionEvaluator;
import com.example.editor.semantic.expression.ExpressionValueKind;
import com.example.editor.semantic.expression.ExpressionValueType;
import com.example.editor.semantic.expression.LiteralExpressionNode;
import com.example.editor.semantic.expression.VariableExpressionNode;

/** Adapta las condiciones históricas de Gamification al kernel semantic.expression. */
final class GamificationEventMatcher {
    /** Descripción: Inicializa la utilidad de matching sin exponer instancias públicas. Entradas: no requiere parámetros. Salida: no crea instancias públicas. */ private GamificationEventMatcher() { }

    /** Descripción: Evalúa una regla con la compatibilidad histórica. Entradas: rule y event. Salida: devuelve true cuando la regla coincide. */ static boolean matches(GamificationRule rule, CanonicalEvent event) {
        if(!rule.eventTypeId().equals(event.eventTypeId())) return false;
        if(rule.conditionPath().isBlank()) return true;
        Object actual = read(event, rule.conditionPath());
        Object expected = rule.expectedValue();
        return legacyCompare(rule.operator(), actual, expected);
    }

    /** Descripción: Evalúa una regla mediante semantic.expression. Entradas: rule, event, metamodel y evaluator. Salida: devuelve true cuando la condición se cumple. */ static boolean matches(GamificationRule rule, CanonicalEvent event, MetaModel metamodel, ExpressionEvaluator evaluator) {
        if(!rule.eventTypeId().equals(event.eventTypeId())) return false;
        if(rule.conditionPath().isBlank()) return true;
        if(metamodel == null || evaluator == null) return matches(rule, event);
        ExpressionDefinition expression = compile(rule);
        Object value = evaluator.evaluate(expression, CanonicalEventExpressionContext.create(metamodel, event)).value();
        if(!(value instanceof Boolean result)) throw new IllegalStateException("Gamification condition did not evaluate to BOOLEAN: " + rule.id());
        return result;
    }

    /** Descripción: Compila la condición histórica de gamificación a AST neutral. Entradas: rule. Salida: devuelve una ExpressionDefinition booleana equivalente. */ static ExpressionDefinition compile(GamificationRule rule) {
        String variableName = normalizePath(rule.conditionPath());
        var variable = new VariableExpressionNode("node:gamification:left", variableName);
        BinaryExpressionOperator operator = switch(rule.operator()) {
            case "NOT_EQUALS" -> BinaryExpressionOperator.NOT_EQUALS;
            case "EXISTS" -> BinaryExpressionOperator.NOT_EQUALS;
            case "NOT_EXISTS" -> BinaryExpressionOperator.EQUALS;
            case "CONTAINS" -> BinaryExpressionOperator.CONTAINS;
            case "GREATER_THAN" -> BinaryExpressionOperator.GREATER_THAN;
            case "LESS_THAN" -> BinaryExpressionOperator.LESS_THAN;
            default -> BinaryExpressionOperator.EQUALS;
        };
        Object expected = ("EXISTS".equals(rule.operator()) || "NOT_EXISTS".equals(rule.operator())) ? null : rule.expectedValue();
        var right = new LiteralExpressionNode("node:gamification:right", inferType(expected), expected);
        return new ExpressionDefinition("expression:gamification:" + rule.id(), rule.name() + " condition",
                "Compiled from the backwards-compatible gamification condition", "",
                ExpressionValueType.scalar(ExpressionValueKind.BOOLEAN),
                new BinaryExpressionNode("node:gamification:condition", operator, variable, right), Map.of("origin", "gamification-legacy-condition"));
    }

    /** Descripción: Lee una ruta de evento usando las variables neutrales compartidas. Entradas: event y path. Salida: devuelve el valor resuelto o null. */ static Object read(CanonicalEvent event, String path) {
        String normalized = normalizePath(path);
        return CanonicalEventExpressionContext.variables(event).get(normalized);
    }

    /** Descripción: Normaliza una ruta histórica a nombre de variable neutral. Entradas: path. Salida: devuelve la ruta normalizada. */ private static String normalizePath(String path) {
        if(path == null) return "";
        String normalized = path.trim();
        if(normalized.startsWith("$.")) normalized = normalized.substring(2);
        if(normalized.startsWith("event.")) normalized = normalized.substring("event.".length());
        boolean explicitRoot = normalized.startsWith("payload.") || normalized.startsWith("metadata.")
                || normalized.startsWith("actor.") || normalized.startsWith("subject.")
                || normalized.startsWith("context.") || normalized.startsWith("source.")
                || normalized.equals("eventId") || normalized.equals("eventTypeId")
                || normalized.equals("correlationId") || normalized.equals("causationId");
        return explicitRoot ? normalized : "payload." + normalized;
    }

    /** Descripción: Infiere el tipo literal neutral de un valor histórico. Entradas: value. Salida: devuelve ExpressionValueType compatible. */ private static ExpressionValueType inferType(Object value) {
        ExpressionValueKind kind = value instanceof Boolean ? ExpressionValueKind.BOOLEAN
                : value instanceof Number ? ExpressionValueKind.DECIMAL
                : value instanceof String ? ExpressionValueKind.STRING : ExpressionValueKind.ANY;
        return ExpressionValueType.scalar(kind);
    }

    /** Descripción: Conserva la comparación histórica como fallback embebido. Entradas: operator, actual y expected. Salida: devuelve el resultado booleano. */ private static boolean legacyCompare(String operator, Object actual, Object expected) {
        return switch(operator) {
            case "NOT_EQUALS" -> !java.util.Objects.equals(normalize(actual), normalize(expected));
            case "EXISTS" -> actual != null;
            case "NOT_EXISTS" -> actual == null;
            case "CONTAINS" -> actual != null && String.valueOf(actual).contains(String.valueOf(expected));
            case "GREATER_THAN" -> number(actual) > number(expected);
            case "LESS_THAN" -> number(actual) < number(expected);
            default -> java.util.Objects.equals(normalize(actual), normalize(expected));
        };
    }

    /** Descripción: Normaliza valores numéricos para comparación histórica. Entradas: value. Salida: devuelve el valor normalizado. */ private static Object normalize(Object value) { return value instanceof Number number ? number.doubleValue() : value; }
    /** Descripción: Convierte un valor a número para comparación histórica. Entradas: value. Salida: devuelve el número o NaN cuando no es convertible. */ private static double number(Object value) { try { return value == null ? Double.NaN : Double.parseDouble(String.valueOf(value)); } catch(NumberFormatException exception) { return Double.NaN; } }
}
