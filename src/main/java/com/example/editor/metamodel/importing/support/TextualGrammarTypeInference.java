package com.example.editor.metamodel.importing.support;

import static com.example.editor.metamodel.importing.support.TextualGrammarScanner.*;
import static com.example.editor.metamodel.importing.support.TextualGrammarSyntaxEngine.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Type inference of Langium and Xtext parser rules for the textual grammar importer (Langium D-1..D-5, authorisation of 30/10).
 *
 * <p>An action ({@code {X}}, {@code {infer X}}, or the rewrite form {@code {X.f=current}} / {@code {infer X.f+=current}}) creates an object of
 * type X; the features assigned after it along the parse path belong to X, a subtype of the rule's type, and a rewrite action adds the
 * containment f whose value is the object built so far (the rule's type). The parse path of an action runs forward from it, skips the sibling
 * alternatives of every group it is in, continues after the group closes (so {@code (({Plus.left=current} '+' | {Minus.left=current} '-')
 * right=M)*} gives right to Plus and Minus) and ends at a top-level alternative, at the next action or at the end of the rule. What no action
 * covers stays in the rule's own type. A leading action that names the rule's own type (what HYDRA generates) is not a new object.
 */
final class TextualGrammarTypeInference {
    static final Pattern ACTION = Pattern.compile("\\{\\s*(?:infer\\s+)?([A-Za-z_][A-Za-z0-9_]*(?:::[A-Za-z_][A-Za-z0-9_]*)?)\\s*(?:\\.\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*(\\+=|=)\\s*current\\s*)?\\}");
    private static final Pattern TERMINAL_HEADER = Pattern.compile("(?m)^\\s*(?:hidden\\s+)?terminal\\s+(?:fragment\\s+)?([A-Za-z_][A-Za-z0-9_]*)(?:\\s+returns\\s+([A-Za-z_][A-Za-z0-9_.]*(?:::[A-Za-z_][A-Za-z0-9_]*)?))?\\s*:");
    private static final Pattern FRAGMENT_HEADER = Pattern.compile("(?m)^\\s*fragment\\s+([A-Za-z_][A-Za-z0-9_]*)(?:\\s+(?:returns|infers)\\s+[A-Za-z_][A-Za-z0-9_.:]*)?\\s*:(?!:)");
    private static final int MAXIMUM_FRAGMENT_DEPTH = 16;

    /** Descripción: Clase de utilidad. Entradas: ninguna. Salida: ninguna. */
    private TextualGrammarTypeInference() { }

    /** Objects created by an action: their type, the text of their parse path and the containment of a rewrite action. */
    record Segment(String type, String body, String rewriteFeature, String rewriteOperator) { }

    /** The part of a rule that no action covers, and the objects its actions create. */
    record Segmentation(String ownBody, List<Segment> segments) { }

    private record Action(int start, int end, String type, String feature, String operator) { }

    /**
     * Descripción: Separa el cuerpo de una regla en su parte propia y los objetos que crean sus acciones. Entradas: body: cuerpo de la regla;
     * ownType: tipo de la regla. Salida: segmentación; sin acciones, el cuerpo entero es propio.
     */
    static Segmentation segment(String body, String ownType) {
        String masked = maskQuotedText(body);
        List<Action> actions = new ArrayList<>();
        Matcher matcher = ACTION.matcher(masked);
        while(matcher.find()) {
            String type = simpleTypeName(matcher.group(1));
            if(matcher.group(2) == null && type.equals(ownType)) continue;
            actions.add(new Action(matcher.start(), matcher.end(), type, matcher.group(2), matcher.group(3)));
        }
        if(actions.isEmpty()) return new Segmentation(body, List.of());
        int[] depth = depths(masked);
        char[] own = body.toCharArray();
        List<Segment> segments = new ArrayList<>();
        for(Action action : actions) {
            for(int index = action.start(); index < action.end(); index++) own[index] = ' ';
            StringBuilder path = new StringBuilder();
            int start = action.end();
            int level = 0;
            int index = action.end();
            while(index < masked.length()) {
                if(startsAction(actions, index)) break;
                char current = masked.charAt(index);
                if(current == '(') {
                    level++;
                } else if(current == ')') {
                    if(level > 0) level--;
                } else if(current == '|' && level == 0) {
                    append(path, body, own, start, index);
                    if(depth[index] == 0) { start = index; break; }
                    index = closingOfEnclosingGroup(masked, index);
                    start = index;
                    continue;
                }
                index++;
            }
            if(start < index || index >= masked.length()) append(path, body, own, start, Math.min(index, masked.length()));
            segments.add(new Segment(action.type(), path.toString(), action.feature(), action.operator()));
        }
        return new Segmentation(new String(own), List.copyOf(segments));
    }

    /** Descripción: Tipos primitivos declarados por las reglas terminales («terminal X returns T»). Entradas: content: gramática sin comentarios. Salida: terminal -> palabra primitiva. */
    static Map<String, String> terminalTypes(String content) {
        Map<String, String> result = new LinkedHashMap<>();
        Matcher matcher = TERMINAL_HEADER.matcher(content);
        while(matcher.find()) result.putIfAbsent(matcher.group(1), primitiveKeyword(matcher.group(2)));
        return result;
    }

    /** Descripción: Cuerpos de las reglas fragment. Entradas: content: gramática sin comentarios. Salida: nombre -> cuerpo. */
    static Map<String, String> fragments(String content) {
        Map<String, String> result = new LinkedHashMap<>();
        Matcher matcher = FRAGMENT_HEADER.matcher(content);
        while(matcher.find()) {
            int end = findStatementEnd(content, matcher.end());
            result.putIfAbsent(matcher.group(1), content.substring(matcher.end(), end).trim());
        }
        return result;
    }

    /**
     * Descripción: Sustituye cada llamada no asignada a un fragmento por su cuerpo entre paréntesis (un fragmento aporta sus asignaciones a la
     * regla que lo llama), también de forma anidada y con un límite de profundidad. Entradas: body: cuerpo; fragments: cuerpos de fragmentos.
     * Salida: cuerpo con los fragmentos expandidos.
     */
    static String inlineFragments(String body, Map<String, String> fragments) {
        if(fragments.isEmpty()) return body;
        String result = body;
        for(int depth = 0; depth < MAXIMUM_FRAGMENT_DEPTH; depth++) {
            String masked = maskQuotedText(result);
            Matcher matcher = Pattern.compile("(?<![\\w=\\[:.])\\b([A-Za-z_][A-Za-z0-9_]*)\\b(?!\\s*(?:\\+=|\\?=|=|:))").matcher(masked);
            StringBuilder next = new StringBuilder();
            int last = 0;
            boolean changed = false;
            while(matcher.find()) {
                String fragment = fragments.get(matcher.group(1));
                if(fragment == null) continue;
                next.append(result, last, matcher.start()).append("(").append(fragment).append(")");
                last = matcher.end();
                changed = true;
            }
            if(!changed) return result;
            next.append(result.substring(last));
            result = next.toString();
        }
        return result;
    }

    /** Descripción: Palabra primitiva de un tipo de retorno de terminal o de regla de datos. Entradas: type: tipo declarado. Salida: palabra que entiende primitiveType. */
    static String primitiveKeyword(String type) {
        String value = simpleTypeName(trim(type)).toLowerCase(Locale.ROOT);
        return switch(value) {
            case "number", "double", "edouble", "edoubleobject" -> "DOUBLE";
            case "float", "efloat", "efloatobject" -> "FLOAT";
            case "bigdecimal", "ebigdecimal", "decimal" -> "DECIMAL";
            case "int", "integer", "eint", "eintegerobject" -> "INT";
            case "long", "elong", "elongobject", "bigint", "biginteger", "ebiginteger" -> "LONG";
            case "boolean", "bool", "eboolean", "ebooleanobject" -> "BOOLEAN";
            case "date", "edate" -> "DATE";
            case "char", "echar", "echaracterobject" -> "CHAR";
            default -> "STRING";
        };
    }

    /** Descripción: Profundidad de paréntesis antes de cada posición del texto enmascarado. Entradas: masked. Salida: profundidades. */
    private static int[] depths(String masked) {
        int[] result = new int[masked.length() + 1];
        int depth = 0;
        for(int index = 0; index < masked.length(); index++) {
            result[index] = depth;
            char current = masked.charAt(index);
            if(current == '(') depth++;
            else if(current == ')' && depth > 0) depth--;
        }
        result[masked.length()] = depth;
        return result;
    }

    /** Descripción: Posición del «)» que cierra el grupo que contiene index (o el final). Entradas: masked, index. Salida: posición. */
    private static int closingOfEnclosingGroup(String masked, int index) {
        int nesting = 0;
        for(int position = index + 1; position < masked.length(); position++) {
            char current = masked.charAt(position);
            if(current == '(') nesting++;
            else if(current == ')') {
                if(nesting == 0) return position;
                nesting--;
            }
        }
        return masked.length();
    }

    /** Descripción: Indica si en index empieza otra acción. Entradas: actions, index. Salida: indicador. */
    private static boolean startsAction(List<Action> actions, int index) {
        for(Action action : actions) if(action.start() == index) return true;
        return false;
    }

    /** Descripción: Añade al camino el texto original [from, to) y lo retira de la parte propia. Entradas: path, body, own, from, to. Salida: ninguna. */
    private static void append(StringBuilder path, String body, char[] own, int from, int to) {
        if(to <= from) return;
        path.append(body, from, to).append(' ');
        for(int index = from; index < to; index++) own[index] = ' ';
    }
}
