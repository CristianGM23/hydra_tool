package com.example.editor.portability;

import java.util.ArrayList;
import java.util.List;

/** Comprueba rangos de versiones sencillos y deterministas sin depender de un gestor tecnológico. */
public final class CapabilityVersionRange {
    /** Descripción: Inicializa una instancia de CapabilityVersionRange con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private CapabilityVersionRange() { }

    /** Descripción: Evalúa la condición «supports» sin modificar el estado externo. Entradas: version: dato de entrada necesario para la operación; range: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public static boolean supports(String version, String range) {
        String actual = safe(version);
        String expression = safe(range);
        if(expression.isBlank() || "*".equals(expression)) return true;
        if(actual.isBlank()) return false;
        if(expression.startsWith("[") || expression.startsWith("(")) return interval(actual, expression);
        if(expression.startsWith("^")) {
            Version base = Version.parse(expression.substring(1));
            Version value = Version.parse(actual);
            Version upper = base.major > 0 ? new Version(base.major + 1, 0, 0, List.of())
                    : base.minor > 0 ? new Version(0, base.minor + 1, 0, List.of())
                    : new Version(0, 0, base.patch + 1, List.of());
            return value.compareTo(base) >= 0 && value.compareTo(upper) < 0;
        }
        String[] clauses = expression.split("\\s*,\\s*|\\s+");
        for(String clause : clauses) if(!clause.isBlank() && !clause(actual, clause)) return false;
        return true;
    }

    /** Descripción: Ejecuta la operación «valid» correspondiente a la responsabilidad de Version. Entradas: range: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public static boolean valid(String range) {
        String expression = safe(range);
        if(expression.isBlank() || "*".equals(expression)) return true;
        try {
            if(expression.startsWith("[") || expression.startsWith("(")) {
                interval("0.0.0", expression);
                return expression.endsWith("]") || expression.endsWith(")");
            }
            if(expression.startsWith("^")) { Version.parse(expression.substring(1)); return true; }
            for(String clause : expression.split("\\s*,\\s*|\\s+")) if(!clause.isBlank()) parseClause(clause);
            return true;
        } catch(IllegalArgumentException exception) { return false; }
    }

    /** Descripción: Ejecuta la operación «interval» correspondiente a la responsabilidad de Version. Entradas: version: dato de entrada necesario para la operación; range: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean interval(String version, String range) {
        if(range.length() < 3 || !(range.endsWith("]") || range.endsWith(")"))) throw new IllegalArgumentException("Invalid interval");
        String[] bounds = range.substring(1, range.length() - 1).split(",", -1);
        if(bounds.length != 2) throw new IllegalArgumentException("Invalid interval");
        Version value = Version.parse(version);
        if(!bounds[0].isBlank()) {
            int comparison = value.compareTo(Version.parse(bounds[0]));
            if(range.startsWith("[") ? comparison < 0 : comparison <= 0) return false;
        }
        if(!bounds[1].isBlank()) {
            int comparison = value.compareTo(Version.parse(bounds[1]));
            if(range.endsWith("]") ? comparison > 0 : comparison >= 0) return false;
        }
        return true;
    }

    /** Descripción: Ejecuta la operación «clause» correspondiente a la responsabilidad de Version. Entradas: version: dato de entrada necesario para la operación; clause: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean clause(String version, String clause) {
        ParsedClause parsed = parseClause(clause);
        int comparison = Version.parse(version).compareTo(parsed.version);
        return switch(parsed.operator) {
            case ">=" -> comparison >= 0;
            case "<=" -> comparison <= 0;
            case ">" -> comparison > 0;
            case "<" -> comparison < 0;
            case "=", "==" -> comparison == 0;
            default -> comparison == 0;
        };
    }

    /** Descripción: Ejecuta la operación «parseClause» correspondiente a la responsabilidad de Version. Entradas: clause: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static ParsedClause parseClause(String clause) {
        for(String operator : List.of(">=", "<=", "==", ">", "<", "=")) {
            if(clause.startsWith(operator)) return new ParsedClause(operator, Version.parse(clause.substring(operator.length())));
        }
        return new ParsedClause("=", Version.parse(clause));
    }

    /** Descripción: Ejecuta la operación «safe» correspondiente a la responsabilidad de Version. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String safe(String value) { return value == null ? "" : value.trim(); }

    private record ParsedClause(String operator, Version version) { }

    private record Version(int major, int minor, int patch, List<String> qualifier) implements Comparable<Version> {
        /** Descripción: Ejecuta la operación «parse» correspondiente a la responsabilidad de Version. Entradas: text: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ static Version parse(String text) {
            String normalized = safe(text);
            if(normalized.isBlank()) throw new IllegalArgumentException("Version is blank");
            String[] mainAndQualifier = normalized.split("[-+]", 2);
            String[] parts = mainAndQualifier[0].split("\\.");
            if(parts.length > 3) throw new IllegalArgumentException("Too many version components");
            int major = number(parts, 0);
            int minor = number(parts, 1);
            int patch = number(parts, 2);
            List<String> qualifier = new ArrayList<>();
            if(mainAndQualifier.length > 1) for(String item : mainAndQualifier[1].split("[.-]")) if(!item.isBlank()) qualifier.add(item);
            return new Version(major, minor, patch, List.copyOf(qualifier));
        }
        /** Descripción: Ejecuta la operación «number» correspondiente a la responsabilidad de Version. Entradas: parts: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static int number(String[] parts, int index) {
            if(index >= parts.length || parts[index].isBlank()) return 0;
            try { return Integer.parseInt(parts[index]); }
            catch(NumberFormatException exception) { throw new IllegalArgumentException("Invalid version component", exception); }
        }
        /** Descripción: Ejecuta la operación «compareTo» correspondiente a la responsabilidad de Version. Entradas: other: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public int compareTo(Version other) {
            int comparison = Integer.compare(major, other.major);
            if(comparison == 0) comparison = Integer.compare(minor, other.minor);
            if(comparison == 0) comparison = Integer.compare(patch, other.patch);
            if(comparison != 0) return comparison;
            if(qualifier.isEmpty() && other.qualifier.isEmpty()) return 0;
            if(qualifier.isEmpty()) return 1;
            if(other.qualifier.isEmpty()) return -1;
            int size = Math.max(qualifier.size(), other.qualifier.size());
            for(int index = 0; index < size; index++) {
                if(index >= qualifier.size()) return -1;
                if(index >= other.qualifier.size()) return 1;
                comparison = qualifier.get(index).compareToIgnoreCase(other.qualifier.get(index));
                if(comparison != 0) return comparison;
            }
            return 0;
        }
    }
}
