package com.example.editor.metamodel.importing.support;

import static com.example.editor.metamodel.importing.support.TextualGrammarImportParser.*;
import static com.example.editor.metamodel.importing.support.TextualGrammarSyntaxEngine.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import com.example.editor.metamodel.core.TransformationWarning;

/**
 * Detección de recursión por la izquierda entre reglas de una gramática textual importada y primera regla que invoca una alternativa sin
 * asignarla. Separada de {@link TextualGrammarImportParser} para que ese fichero respete el límite uniforme de arquitectura
 * (config/hydra-architecture-baseline.properties) tras la inferencia de tipos D-1..D-5; el comportamiento no cambia.
 */
final class TextualGrammarLeftRecursion {

    /** Descripción: Clase de utilidad. Entradas: ninguna. Salida: ninguna. */
    private TextualGrammarLeftRecursion() { }

    /** Descripción: Ejecuta la operación «detectLeftRecursion» correspondiente a la responsabilidad de Assignment. Entradas: rules: dato de entrada necesario para la operación; warnings: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ static void detectLeftRecursion(List<GrammarRule> rules, List<TransformationWarning> warnings) {
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        Set<String> known = new HashSet<>();

        for(GrammarRule rule : rules) {
            if(rule.kind() == RuleKind.PARSER || rule.kind() == RuleKind.TYPE_ALIAS) {
                known.add(rule.name());
            }
        }

        for(GrammarRule rule : rules) {
            if(rule.kind() != RuleKind.PARSER && rule.kind() != RuleKind.TYPE_ALIAS) {
                continue;
            }

            Set<String> firstRules = new LinkedHashSet<>();

            for(String alternative : splitTopLevel(rule.body(), '|')) {
                String first = firstUnassignedRuleInvocation(alternative);

                if(known.contains(first)) {
                    firstRules.add(first);
                }
            }

            graph.put(rule.name(), firstRules);
        }

        Set<String> emitted = new HashSet<>();

        for(GrammarRule rule : rules) {
            List<String> cycle = findCycle(rule.name(), rule.name(), graph, new LinkedHashSet<>());

            if(cycle.isEmpty()) {
                continue;
            }

            String canonical = canonicalCycle(cycle);

            if(!emitted.add(canonical)) {
                continue;
            }

            warnings.add(new TransformationWarning("IMPORT_LEFT_RECURSION_AUTO_RESOLVED", path(rule), "Left recursion was detected in " + String.join(" -> ", cycle) + "; parser-control invocations were ignored while deriving the metamodel, so no false containment relation was generated"));
        }
    }

    /** Descripción: Obtiene la información correspondiente a «findCycle» dentro de Assignment. Entradas: origin: dato de entrada necesario para la operación; current: dato de entrada necesario para la operación; graph: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static List<String> findCycle(String origin, String current, Map<String, Set<String>> graph, LinkedHashSet<String> path) {
        if(!path.add(current)) {
            return List.of();
        }

        for(String next : graph.getOrDefault(current, Set.of())) {
            if(next.equals(origin)) {
                List<String> cycle = new ArrayList<>(path);
                cycle.add(origin);
                return cycle;
            }

            if(!path.contains(next)) {
                List<String> cycle = findCycle(origin, next, graph, new LinkedHashSet<>(path));

                if(!cycle.isEmpty()) {
                    return cycle;
                }
            }
        }

        return List.of();
    }

    /** Descripción: Evalúa la condición «canonicalCycle» sin modificar el estado externo. Entradas: cycle: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static String canonicalCycle(List<String> cycle) {
        List<String> values = new ArrayList<>(cycle);

        if(values.size() > 1 && values.get(0).equals(values.get(values.size() - 1))) {
            values.remove(values.size() - 1);
        }

        return values.stream().sorted().reduce((left, right) -> left + "|" + right).orElse("");
    }

    /** Descripción: Ejecuta la operación «firstUnassignedRuleInvocation» correspondiente a la responsabilidad de Assignment. Entradas: alternative: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static String firstUnassignedRuleInvocation(String alternative) {
        String value = alternative.trim();

        while(value.startsWith("(")) {
            value = value.substring(1).trim();
        }

        if(value.startsWith("{") || value.startsWith("'") || value.startsWith("\"")) {
            return "";
        }

        Matcher matcher = IDENTIFIER.matcher(value);

        if(!matcher.lookingAt()) {
            return "";
        }

        String candidate = simpleTypeName(matcher.group());
        String remaining = value.substring(matcher.end()).stripLeading();

        if(remaining.startsWith("=") || remaining.startsWith("+=") || remaining.startsWith("?=")) {
            return "";
        }

        return candidate;
    }
}
