package com.example.editor.metamodel.importing.support;

import static com.example.editor.metamodel.importing.support.TextualGrammarImportParser.*;
import static com.example.editor.metamodel.importing.support.TextualGrammarScanner.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;

/** Expression and assignment helpers for textual Xtext/Langium import. */
final class TextualGrammarSyntaxEngine {
    /** Descripción: Inicializa una instancia de TextualGrammarSyntaxEngine con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private TextualGrammarSyntaxEngine() {
    }

    /** Descripción: Obtiene la información correspondiente a «readAssignments» dentro de TextualGrammarSyntaxEngine. Entradas: body: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static List<Assignment> readAssignments(String body) {
        List<Assignment> result = new ArrayList<>();
        List<GroupRange> groups = groupRanges(body);
        Matcher matcher = ASSIGNMENT_START.matcher(maskQuotedText(body));

        while(matcher.find()) {
            int expressionStart = matcher.end();
            Expression expression = readExpression(body, expressionStart);

            if(expression == null) {
                continue;
            }

            char immediate = readQuantifier(body, expression.end());
            List<Character> enclosing = new ArrayList<>();

            for(GroupRange group : groups) {
                if(group.open() < matcher.start() && group.close() >= expression.end() && group.quantifier() != 0) {
                    enclosing.add(group.quantifier());
                }
            }

            result.add(new Assignment(matcher.group(1), matcher.group(2), expression.text().trim(), immediate, enclosing, matcher.start()));
        }

        return result;
    }

    /** Descripción: Obtiene la información correspondiente a «readExpression» dentro de TextualGrammarSyntaxEngine. Entradas: body: dato de entrada necesario para la operación; start: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static Expression readExpression(String body, int start) {
        int index = skipWhitespace(body, start);

        if(index >= body.length()) {
            return null;
        }

        char first = body.charAt(index);

        if(first == '[') {
            int end = findMatching(body, index, '[', ']');
            return end < 0 ? new Expression(body.substring(index), body.length()) : new Expression(body.substring(index, end + 1), end + 1);
        }

        if(first == '(') {
            int end = findMatching(body, index, '(', ')');
            return end < 0 ? new Expression(body.substring(index), body.length()) : new Expression(body.substring(index, end + 1), end + 1);
        }

        if(first == '\'' || first == '"') {
            int end = findStringEnd(body, index, first);
            return new Expression(body.substring(index, Math.min(body.length(), end + 1)), Math.min(body.length(), end + 1));
        }

        Matcher matcher = IDENTIFIER.matcher(body);
        matcher.region(index, body.length());

        if(matcher.lookingAt()) {
            return new Expression(matcher.group(), matcher.end());
        }

        return null;
    }

    /** Descripción: Ejecuta la operación «groupRanges» correspondiente a la responsabilidad de TextualGrammarSyntaxEngine. Entradas: body: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static List<GroupRange> groupRanges(String body) {
        List<GroupRange> result = new ArrayList<>();
        Deque<Integer> stack = new ArrayDeque<>();
        ScanState state = new ScanState();

        for(int index = 0; index < body.length(); index += 1) {
            char current = body.charAt(index);
            state.accept(body, index);

            if(state.insideLiteral()) {
                continue;
            }

            if(current == '(') {
                stack.push(index);
            } else if(current == ')' && !stack.isEmpty()) {
                int open = stack.pop();
                result.add(new GroupRange(open, index, readQuantifier(body, index + 1)));
            }
        }

        return result;
    }

    /** Descripción: Ejecuta la operación «multiplicity» correspondiente a la responsabilidad de TextualGrammarSyntaxEngine. Entradas: assignment: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static Multiplicity multiplicity(Assignment assignment) {
        int lower = "+=".equals(assignment.operator()) ? 0 : 1;
        Integer upper = "+=".equals(assignment.operator()) ? null : 1;

        if("?=".equals(assignment.operator())) {
            lower = 0;
            upper = 1;
        }

        List<Character> quantifiers = new ArrayList<>(assignment.enclosingQuantifiers());

        if(assignment.immediateQuantifier() != 0) {
            quantifiers.add(assignment.immediateQuantifier());
        }

        for(char quantifier : quantifiers) {
            if(quantifier == '?') {
                lower = 0;
            } else if(quantifier == '*') {
                lower = 0;
                upper = null;
            } else if(quantifier == '+') {
                lower = Math.max(lower, 1);
                upper = null;
            }
        }

        return new Multiplicity(lower, upper);
    }

    /** Descripción: Ejecuta la operación «parseCrossReference» correspondiente a la responsabilidad de TextualGrammarSyntaxEngine. Entradas: expression: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static CrossReference parseCrossReference(String expression) {
        String inner = expression.length() >= 2 ? expression.substring(1, expression.length() - 1).trim() : "";
        int separator = topLevelSeparator(inner, '|', ':');
        String target = separator < 0 ? inner : inner.substring(0, separator).trim();
        String terminal = separator < 0 ? "" : inner.substring(separator + 1).trim();
        String syntax = separator >= 0 && inner.charAt(separator) == ':' ? "langium-colon" : "xtext-pipe";
        return new CrossReference(simpleTypeName(target), simpleTypeName(terminal), syntax);
    }

    /** Descripción: Ejecuta la operación «topLevelSeparator» correspondiente a la responsabilidad de TextualGrammarSyntaxEngine. Entradas: value: valor de entrada que debe procesarse; first: dato de entrada necesario para la operación; second: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static int topLevelSeparator(String value, char first, char second) {
        int doubleColon = value.lastIndexOf("::");

        for(int index = 0; index < value.length(); index += 1) {
            char current = value.charAt(index);

            if(current == first) {
                return index;
            }

            if(current == second && !(index > 0 && value.charAt(index - 1) == ':') && !(index + 1 < value.length() && value.charAt(index + 1) == ':') && index > doubleColon) {
                return index;
            }
        }

        return -1;
    }

    /** Descripción: Ejecuta la operación «simpleExpressionName» correspondiente a la responsabilidad de TextualGrammarSyntaxEngine. Entradas: expression: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static String simpleExpressionName(String expression) {
        String value = expression.trim();

        if(value.startsWith("[") || value.startsWith("(") || value.startsWith("'") || value.startsWith("\"")) {
            return "";
        }

        return simpleTypeName(value);
    }

    /** Descripción: Evalúa la condición «isPrimitiveExpression» sin modificar el estado externo. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ static boolean isPrimitiveExpression(String value) {
        return PRIMITIVE_RULES.contains(simpleTypeName(value).toUpperCase(Locale.ROOT));
    }

    /** Descripción: Ejecuta la operación «primitiveType» correspondiente a la responsabilidad de TextualGrammarSyntaxEngine. Entradas: expression: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static MetaTypeReference primitiveType(String expression) {
        String value = simpleTypeName(expression).toUpperCase(Locale.ROOT);

        return switch(value) {
            case "BOOLEAN", "BOOL" -> MetaTypeReference.primitive(MetaPrimitiveType.BOOLEAN);
            case "BYTE" -> MetaTypeReference.primitive(MetaPrimitiveType.BYTE);
            case "SHORT" -> MetaTypeReference.primitive(MetaPrimitiveType.SHORT);
            case "INT", "INTEGER" -> MetaTypeReference.primitive(MetaPrimitiveType.INTEGER);
            case "LONG", "BIGINT", "BIG_INTEGER" -> MetaTypeReference.primitive(MetaPrimitiveType.LONG);
            case "FLOAT" -> MetaTypeReference.primitive(MetaPrimitiveType.FLOAT);
            case "DOUBLE", "NUMBER" -> MetaTypeReference.primitive(MetaPrimitiveType.DOUBLE);
            case "DECIMAL" -> MetaTypeReference.primitive(MetaPrimitiveType.DECIMAL);
            case "CHAR", "CHARACTER" -> MetaTypeReference.primitive(MetaPrimitiveType.CHARACTER);
            case "DATE" -> MetaTypeReference.primitive(MetaPrimitiveType.DATE);
            default -> MetaTypeReference.primitive(MetaPrimitiveType.STRING);
        };
    }


    /** Descripción: Ejecuta la operación «alternativeNames» correspondiente a la responsabilidad de TextualGrammarSyntaxEngine. Entradas: body: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static List<String> alternativeNames(String body) {
        List<String> result = new ArrayList<>();

        for(String alternative : splitTopLevel(body, '|')) {
            String normalized = alternative.replaceAll("^[()\\s]+|[()\\s?*+]+$", "").trim();
            Matcher matcher = IDENTIFIER.matcher(normalized);

            if(matcher.matches()) {
                result.add(simpleTypeName(matcher.group()));
            }
        }

        return result;
    }

    /** Descripción: Ejecuta la operación «splitNames» correspondiente a la responsabilidad de TextualGrammarSyntaxEngine. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ static List<String> splitNames(String value) {
        if(value == null || value.isBlank()) {
            return List.of();
        }

        List<String> result = new ArrayList<>();

        for(String part : value.split("[,|&]")) {
            String name = simpleTypeName(part);

            if(!name.isBlank()) {
                result.add(name);
            }
        }

        return result;
    }

    /** Descripción: Ejecuta la operación «identifiersOutsideQuotes» correspondiente a la responsabilidad de TextualGrammarSyntaxEngine. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ static Set<String> identifiersOutsideQuotes(String value) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = IDENTIFIER.matcher(maskQuotedText(value));

        while(matcher.find()) {
            result.add(simpleTypeName(matcher.group()));
        }

        return result;
    }

    /** Descripción: Evalúa la condición «containsObjectAction» sin modificar el estado externo. Entradas: body: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ static boolean containsObjectAction(String body) {
        return Pattern.compile("\\{\\s*[A-Za-z_][A-Za-z0-9_:]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)?\\s*\\}").matcher(maskQuotedText(body)).find();
    }

    /** Descripción: Evalúa la condición «hasAssignment» sin modificar el estado externo. Entradas: body: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ static boolean hasAssignment(String body) {
        return ASSIGNMENT_START.matcher(maskQuotedText(body)).find();
    }

    /** Descripción: Evalúa la condición «containsQuotedLiteral» sin modificar el estado externo. Entradas: body: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ static boolean containsQuotedLiteral(String body) {
        return QUOTED_LITERAL.matcher(body).find();
    }

    /** Descripción: Ejecuta la operación «quotedValues» correspondiente a la responsabilidad de TextualGrammarSyntaxEngine. Entradas: body: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static List<String> quotedValues(String body) {
        List<String> result = new ArrayList<>();
        Matcher matcher = QUOTED_LITERAL.matcher(body);

        while(matcher.find()) {
            result.add(unquote(matcher.group()));
        }

        return result;
    }

    /** Descripción: Ejecuta la operación «splitTopLevel» correspondiente a la responsabilidad de TextualGrammarSyntaxEngine. Entradas: value: valor de entrada que debe procesarse; separator: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static List<String> splitTopLevel(String value, char separator) {
        List<String> result = new ArrayList<>();
        int start = 0;
        int round = 0;
        int square = 0;
        int curly = 0;
        ScanState state = new ScanState();

        for(int index = 0; index < value.length(); index += 1) {
            char current = value.charAt(index);
            state.accept(value, index);

            if(state.insideLiteral()) {
                continue;
            }

            if(current == '(') {
                round += 1;
            } else if(current == ')') {
                round = Math.max(0, round - 1);
            } else if(current == '[') {
                square += 1;
            } else if(current == ']') {
                square = Math.max(0, square - 1);
            } else if(current == '{') {
                curly += 1;
            } else if(current == '}') {
                curly = Math.max(0, curly - 1);
            } else if(current == separator && round == 0 && square == 0 && curly == 0) {
                result.add(value.substring(start, index).trim());
                start = index + 1;
            }
        }

        result.add(value.substring(start).trim());
        return result;
    }
    /** Descripción: Ejecuta la operación «simpleTypeName» correspondiente a la responsabilidad de TextualGrammarSyntaxEngine. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ static String simpleTypeName(String value) {
        String result = trim(value).replaceAll("[?*+]+$", "").trim();

        if(result.contains("::")) {
            result = result.substring(result.lastIndexOf("::") + 2);
        } else if(result.contains(".")) {
            result = result.substring(result.lastIndexOf('.') + 1);
        }

        return result.replaceAll("[^A-Za-z0-9_]", "");
    }

    /** Descripción: Ejecuta la operación «simpleGrammarName» correspondiente a la responsabilidad de TextualGrammarSyntaxEngine. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ static String simpleGrammarName(String value) {
        String result = value == null || value.isBlank() ? "ImportedGrammar" : value;
        return result.contains(".") ? result.substring(result.lastIndexOf('.') + 1) : result;
    }

    /** Descripción: Ejecuta la operación «trim» correspondiente a la responsabilidad de TextualGrammarSyntaxEngine. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /** Descripción: Ejecuta la operación «unquote» correspondiente a la responsabilidad de TextualGrammarSyntaxEngine. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ static String unquote(String value) {
        if(value == null || value.length() < 2) {
            return trim(value);
        }

        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);

        if((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
            return value.substring(1, value.length() - 1).replace("\\'", "'").replace("\\\"", "\"").replace("\\\\", "\\");
        }

        return value;
    }

    /** Descripción: Ejecuta la operación «path» correspondiente a la responsabilidad de TextualGrammarSyntaxEngine. Entradas: rule: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static String path(GrammarRule rule) {
        return "$.rules[" + rule.name() + "]@line" + rule.line();
    }

}
