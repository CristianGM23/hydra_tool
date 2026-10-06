package com.example.editor.metamodel.importing.support;

import static com.example.editor.metamodel.importing.support.TextualGrammarScanner.*;
import static com.example.editor.metamodel.importing.support.TextualGrammarSyntaxEngine.*;
import static com.example.editor.metamodel.importing.support.TextualGrammarModelEngine.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.editor.metamodel.importing.canonicalization.EcoreImportMetadata;
import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaDataType;
import com.example.editor.metamodel.core.MetaEnum;
import com.example.editor.metamodel.core.MetaEnumLiteral;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.core.TransformationWarning;

/**
 * Analiza gramáticas textuales Xtext y Langium sin depender del runtime de esos lenguajes.
 */
final class TextualGrammarImportParser {
    private static final Class<?>[] FOCUSED_COMPONENTS = { TextualGrammarScanner.class, TextualGrammarSyntaxSupport.class, TextualGrammarModelSupport.class };
    private static final Pattern ENUM_BRACE_HEADER = Pattern.compile("(?m)^\\s*enum\\s+([A-Za-z_][A-Za-z0-9_]*)(?:\\s+returns\\s+([^\\{]+))?\\s*\\{");
    private static final Pattern ENUM_COLON_HEADER = Pattern.compile("(?m)^\\s*enum\\s+([A-Za-z_][A-Za-z0-9_]*)(?:\\s+returns\\s+([^:]+))?\\s*:");
    private static final Pattern TYPE_ALIAS_HEADER = Pattern.compile("(?m)^\\s*type\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=");
    private static final Pattern INTERFACE_HEADER = Pattern.compile("(?m)^\\s*interface\\s+([A-Za-z_][A-Za-z0-9_]*)(?:\\s+extends\\s+([^\\{]+))?\\s*\\{");
    private static final Pattern PARSER_RULE_HEADER = Pattern.compile("(?m)^\\s*(entry\\s+)?([A-Za-z_][A-Za-z0-9_]*)(?:\\s+returns\\s+([^:]+))?\\s*:");
    static final Pattern ASSIGNMENT_START = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*(\\+=|\\?=|=)\\s*");
    static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(?:(?:::|\\.)[A-Za-z_][A-Za-z0-9_]*)*");
    static final Pattern EXPLICIT_ENUM_LITERAL = Pattern.compile("(?m)([^\\s,|={}]+)\\s*=\\s*('(?:\\\\.|[^'])*'|\"(?:\\\\.|[^\"])*\")");
    static final Pattern QUOTED_LITERAL = Pattern.compile("'(?:\\\\.|[^'])*'|\"(?:\\\\.|[^\"])*\"");
    static final Set<String> PRIMITIVE_RULES = Set.of("ID", "STRING", "INT", "NUMBER", "BOOLEAN", "BOOL", "FLOAT", "DOUBLE", "DECIMAL", "DATE", "CHAR", "CHARACTER", "BYTE", "SHORT", "LONG", "BIGINT", "BIG_INTEGER");

    /** Descripción: Inicializa una instancia de TextualGrammarImportParser con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private TextualGrammarImportParser() {
    }

    /** Descripción: Ejecuta la operación «parse» correspondiente a la responsabilidad de Assignment. Entradas: content: valor de entrada que debe procesarse; grammarName: dato de entrada necesario para la operación; namespaceUri: dato de entrada necesario para la operación; sourceFormat: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static GeneratedGrammarImportSupport.ParsedGrammar parse(String content, String grammarName, String namespaceUri, String sourceFormat) {
        String source = content == null ? "" : content;
        String cleaned = stripComments(source);
        List<TransformationWarning> warnings = new ArrayList<>();
        GrammarDeclarations declarations = readDeclarations(cleaned, warnings);
        NameAllocator classifierNames = new NameAllocator(warnings);
        EnumRegistry enumRegistry = new EnumRegistry(classifierNames, warnings);
        Map<String, MetaDataType> dataTypesByRule = new LinkedHashMap<>();
        Map<String, ClassBuilder> classesByRule = new LinkedHashMap<>();
        Map<String, List<String>> alternativesByAbstractRule = new LinkedHashMap<>();
        Map<String, List<String>> declaredParentsByRule = new LinkedHashMap<>();
        Map<String, String> forwardingRules = forwardingRules(declarations, warnings);

        for(GrammarRule rule : declarations.rules()) {
            if(rule.kind() == RuleKind.ENUM || isQuotedEnumRule(rule)) {
                enumRegistry.register(rule);
            }
        }

        for(GrammarRule rule : declarations.rules()) {
            if(rule.kind() == RuleKind.ENUM || isQuotedEnumRule(rule)) {
                continue;
            }

            if(rule.kind() == RuleKind.TYPE_ALIAS || isAlternativeRule(rule)) {
                String classifierName = classifierNames.allocate(rule.name(), "AbstractClass", path(rule), "abstract class");
                ClassBuilder builder = new ClassBuilder(rule.name(), classifierId(classifierName), classifierName, MetaClassKind.ABSTRACT_CLASS, rule.body(), rule.line(), rule.entry(), sourceFormat);
                builder.annotations.put("abstractionOrigin", rule.kind() == RuleKind.TYPE_ALIAS ? "langium-type-alias" : "parser-alternatives");
                classesByRule.put(rule.name(), builder);
                alternativesByAbstractRule.put(rule.name(), alternativeNames(rule.body()));
                warnings.add(new TransformationWarning("IMPORT_ABSTRACT_RULE_INFERRED", path(rule), "Rule " + rule.name() + " was imported as an abstract class because it only groups alternative object rules"));
                continue;
            }

            if(rule.kind() == RuleKind.INTERFACE) {
                String classifierName = classifierNames.allocate(rule.name(), "Interface", path(rule), "interface");
                ClassBuilder builder = new ClassBuilder(rule.name(), classifierId(classifierName), classifierName, MetaClassKind.INTERFACE, rule.body(), rule.line(), rule.entry(), sourceFormat);
                builder.annotations.put("abstractionOrigin", "langium-interface");
                classesByRule.put(rule.name(), builder);
                declaredParentsByRule.put(rule.name(), splitNames(rule.returnsType()));
                continue;
            }

            if(isDataTypeRule(rule, declarations)) {
                String desired = classifierName(rule);
                String classifierName = classifierNames.allocate(desired, "DataType", path(rule), "data type");
                String id = "datatype:" + ImportParsingSupport.safeId(classifierName, "datatype");
                dataTypesByRule.put(rule.name(), new MetaDataType(id, classifierName, EcoreImportMetadata.dataType(Map.of("sourceRule", rule.name(), "sourceLine", Integer.toString(rule.line())), inferImplementationType(rule), true)));
                continue;
            }

            if(isClassRule(rule)) {
                String desired = classifierName(rule);
                String classifierName = classifierNames.allocate(desired, "Class", path(rule), "class");
                ClassBuilder builder = new ClassBuilder(rule.name(), classifierId(classifierName), classifierName, MetaClassKind.CLASS, rule.body(), rule.line(), rule.entry(), sourceFormat);
                classesByRule.put(rule.name(), builder);
            }
        }

        String entryRule = determineEntryRule(declarations.rules(), classesByRule);

        if(entryRule != null) {
            ClassBuilder root = classesByRule.get(entryRule);

            if(root != null) {
                root.annotations.put("root", "true");
                root.annotations.put("entryRule", "true");
            }
        } else {
            warnings.add(new TransformationWarning("IMPORT_ROOT_RULE_NOT_FOUND", "$.grammar", "No parser entry rule could be identified, so the imported metamodel has no explicit root class"));
        }

        detectLeftRecursion(declarations.rules(), warnings);
        ensureReferencedClassDeclarations(declarations, classesByRule, enumRegistry, dataTypesByRule, classifierNames, warnings, sourceFormat, forwardingRules);

        for(ClassBuilder builder : classesByRule.values()) {
            builder.readFeatureSpecs(enumRegistry, dataTypesByRule, classesByRule, warnings);
        }

        List<MetaRelation> relations = new ArrayList<>();

        for(ClassBuilder builder : classesByRule.values()) {
            builder.materializeFeatures(enumRegistry, dataTypesByRule, classesByRule, relations, warnings, forwardingRules);
        }

        createInheritanceRelations(alternativesByAbstractRule, classesByRule, relations, warnings, forwardingRules);
        createDeclaredSupertypeRelations(declaredParentsByRule, classesByRule, relations, warnings);
        List<MetaClass> classes = classesByRule.values().stream().map(ClassBuilder::build).toList();
        String id = ImportParsingSupport.safeId(simpleGrammarName(grammarName), "imported-grammar");
        String name = ImportParsingSupport.pascal(simpleGrammarName(grammarName), "ImportedGrammar");
        Map<String, String> annotations = new LinkedHashMap<>();
        annotations.put("sourceFormat", sourceFormat);

        if(entryRule != null && classesByRule.containsKey(entryRule)) {
            annotations.put("entryRule", entryRule);
            annotations.put("rootClassId", classesByRule.get(entryRule).id);
        }

        MetaModel metamodel = new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, id, name, classes, enumRegistry.values(), List.copyOf(dataTypesByRule.values()), relations, EcoreImportMetadata.model(annotations, id + ".ecore", namespaceUri == null || namespaceUri.isBlank() ? "urn:imported:" + id : namespaceUri, id.replace('-', '_')));
        warnings.add(new TransformationWarning("IMPORT_LOSSY_GRAMMAR_FEATURE_FLAGS", "$.classes", "Xtext and Langium grammars do not encode every Ecore feature flag, so ordered, unique and changeable defaults were applied"));
        return new GeneratedGrammarImportSupport.ParsedGrammar(metamodel, warnings);
    }

    /** Descripción: Obtiene la información correspondiente a «readDeclarations» dentro de Assignment. Entradas: content: valor de entrada que debe procesarse; warnings: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static GrammarDeclarations readDeclarations(String content, List<TransformationWarning> warnings) {
        List<GrammarRule> rules = new ArrayList<>();
        boolean[] occupied = new boolean[content.length()];
        readBraceEnums(content, occupied, rules);
        readColonEnums(content, occupied, rules);
        readTypeAliases(content, occupied, rules);
        readInterfaces(content, occupied, rules);
        readParserRules(content, occupied, rules);
        rules.sort(java.util.Comparator.comparingInt(GrammarRule::start));
        Map<String, GrammarRule> unique = new LinkedHashMap<>();

        for(GrammarRule rule : rules) {
            GrammarRule previous = unique.putIfAbsent(rule.name(), rule);

            if(previous != null) {
                warnings.add(new TransformationWarning("IMPORT_RULE_NAME_CONFLICT_IGNORED", path(rule), "Rule name " + rule.name() + " is declared more than once; the declaration at line " + previous.line() + " was kept and the declaration at line " + rule.line() + " was ignored"));
            }
        }

        return new GrammarDeclarations(List.copyOf(unique.values()));
    }

    /** Descripción: Obtiene la información correspondiente a «readBraceEnums» dentro de Assignment. Entradas: content: valor de entrada que debe procesarse; occupied: dato de entrada necesario para la operación; result: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void readBraceEnums(String content, boolean[] occupied, List<GrammarRule> result) {
        Matcher matcher = ENUM_BRACE_HEADER.matcher(content);

        while(matcher.find()) {
            int open = content.indexOf('{', matcher.start());
            int close = findMatching(content, open, '{', '}');

            if(close < 0) {
                close = content.length() - 1;
            }

            mark(occupied, matcher.start(), close + 1);
            result.add(new GrammarRule(matcher.group(1), trim(matcher.group(2)), content.substring(Math.min(open + 1, content.length()), Math.max(open + 1, close)), true, RuleKind.ENUM, line(content, matcher.start()), matcher.start()));
        }
    }

    /** Descripción: Obtiene la información correspondiente a «readColonEnums» dentro de Assignment. Entradas: content: valor de entrada que debe procesarse; occupied: dato de entrada necesario para la operación; result: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void readColonEnums(String content, boolean[] occupied, List<GrammarRule> result) {
        Matcher matcher = ENUM_COLON_HEADER.matcher(content);

        while(matcher.find()) {
            if(isOccupied(occupied, matcher.start())) {
                continue;
            }

            int end = findStatementEnd(content, matcher.end());
            mark(occupied, matcher.start(), end + 1);
            result.add(new GrammarRule(matcher.group(1), trim(matcher.group(2)), content.substring(matcher.end(), end).trim(), false, RuleKind.ENUM, line(content, matcher.start()), matcher.start()));
        }
    }

    /** Descripción: Obtiene la información correspondiente a «readTypeAliases» dentro de Assignment. Entradas: content: valor de entrada que debe procesarse; occupied: dato de entrada necesario para la operación; result: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void readTypeAliases(String content, boolean[] occupied, List<GrammarRule> result) {
        Matcher matcher = TYPE_ALIAS_HEADER.matcher(content);

        while(matcher.find()) {
            if(isOccupied(occupied, matcher.start())) {
                continue;
            }

            int end = findStatementEnd(content, matcher.end());
            mark(occupied, matcher.start(), end + 1);
            result.add(new GrammarRule(matcher.group(1), "", content.substring(matcher.end(), end).trim(), false, RuleKind.TYPE_ALIAS, line(content, matcher.start()), matcher.start()));
        }
    }

    /** Descripción: Obtiene la información correspondiente a «readInterfaces» dentro de Assignment. Entradas: content: valor de entrada que debe procesarse; occupied: dato de entrada necesario para la operación; result: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void readInterfaces(String content, boolean[] occupied, List<GrammarRule> result) {
        Matcher matcher = INTERFACE_HEADER.matcher(content);

        while(matcher.find()) {
            if(isOccupied(occupied, matcher.start())) {
                continue;
            }

            int open = content.indexOf('{', matcher.start());
            int close = findMatching(content, open, '{', '}');

            if(close < 0) {
                close = content.length() - 1;
            }

            mark(occupied, matcher.start(), close + 1);
            result.add(new GrammarRule(matcher.group(1), trim(matcher.group(2)), content.substring(Math.min(open + 1, content.length()), Math.max(open + 1, close)), false, RuleKind.INTERFACE, line(content, matcher.start()), matcher.start()));
        }
    }

    /** Descripción: Obtiene la información correspondiente a «readParserRules» dentro de Assignment. Entradas: content: valor de entrada que debe procesarse; occupied: dato de entrada necesario para la operación; result: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void readParserRules(String content, boolean[] occupied, List<GrammarRule> result) {
        Matcher matcher = PARSER_RULE_HEADER.matcher(content);

        while(matcher.find()) {
            if(isOccupied(occupied, matcher.start()) || hasDeclarationPrefix(content, matcher.start())) {
                continue;
            }

            int end = findStatementEnd(content, matcher.end());
            mark(occupied, matcher.start(), end + 1);
            result.add(new GrammarRule(matcher.group(2), trim(matcher.group(3)), content.substring(matcher.end(), end).trim(), matcher.group(1) != null, RuleKind.PARSER, line(content, matcher.start()), matcher.start()));
        }
    }

    /** Descripción: Evalúa la condición «hasDeclarationPrefix» sin modificar el estado externo. Entradas: content: valor de entrada que debe procesarse; start: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean hasDeclarationPrefix(String content, int start) {
        int lineStart = content.lastIndexOf('\n', Math.max(0, start - 1)) + 1;
        String prefix = content.substring(lineStart, start).trim().toLowerCase(Locale.ROOT);
        return prefix.endsWith("terminal") || prefix.endsWith("hidden terminal") || prefix.endsWith("fragment") || prefix.endsWith("enum") || prefix.endsWith("type") || prefix.endsWith("interface");
    }

    /** Descripción: Evalúa la condición «isQuotedEnumRule» sin modificar el estado externo. Entradas: rule: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean isQuotedEnumRule(GrammarRule rule) {
        if(rule.kind() != RuleKind.PARSER || hasAssignment(rule.body())) {
            return false;
        }

        List<String> literals = quotedValues(rule.body());

        if(literals.size() < 2) {
            return false;
        }

        String residue = QUOTED_LITERAL.matcher(rule.body()).replaceAll("").replace("|", "").replace(",", "").replace("(", "").replace(")", "").replace("?", "").replace("*", "").replace("+", "").replaceAll("\\s+", "");
        return residue.isBlank();
    }

    /** Descripción: Evalúa la condición «isAlternativeRule» sin modificar el estado externo. Entradas: rule: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean isAlternativeRule(GrammarRule rule) {
        if(rule.kind() != RuleKind.PARSER || rule.entry() || hasAssignment(rule.body()) || containsQuotedLiteral(rule.body())) {
            return false;
        }

        List<String> alternatives = alternativeNames(rule.body());
        return alternatives.size() > 1 && alternatives.stream().noneMatch(TextualGrammarSyntaxEngine::isPrimitiveExpression);
    }

    /** Descripción: Ejecuta la operación «forwardingRules» correspondiente a la responsabilidad de Assignment. Entradas: declarations: dato de entrada necesario para la operación; warnings: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String, String> forwardingRules(GrammarDeclarations declarations, List<TransformationWarning> warnings) {
        Map<String, String> result = new LinkedHashMap<>();

        for(GrammarRule rule : declarations.rules()) {
            if(!isForwardingRule(rule)) {
                continue;
            }

            String target = alternativeNames(rule.body()).getFirst();
            result.put(rule.name(), target);
            warnings.add(new TransformationWarning("IMPORT_INFO_FORWARDING_RULE_RESOLVED", path(rule), "Forwarding parser rule " + rule.name() + " was resolved to " + target + " and will not create an additional metaclass"));
        }

        return result;
    }

    /** Descripción: Evalúa la condición «isForwardingRule» sin modificar el estado externo. Entradas: rule: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean isForwardingRule(GrammarRule rule) {
        if(rule.kind() != RuleKind.PARSER || rule.entry() || hasAssignment(rule.body()) || containsQuotedLiteral(rule.body()) || containsObjectAction(rule.body())) {
            return false;
        }

        List<String> alternatives = alternativeNames(rule.body());
        return alternatives.size() == 1 && !isPrimitiveExpression(alternatives.getFirst());
    }

    /** Descripción: Obtiene la información correspondiente a «resolveForwardingRule» dentro de Assignment. Entradas: ruleName: dato de entrada necesario para la operación; forwardingRules: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static String resolveForwardingRule(String ruleName, Map<String, String> forwardingRules) {
        String original = simpleTypeName(ruleName);
        String current = original;
        Set<String> visited = new LinkedHashSet<>();

        while(forwardingRules.containsKey(current)) {
            if(!visited.add(current)) {
                return original;
            }

            current = simpleTypeName(forwardingRules.get(current));
        }

        return current;
    }

    /** Descripción: Evalúa la condición «isDataTypeRule» sin modificar el estado externo. Entradas: rule: dato de entrada necesario para la operación; declarations: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean isDataTypeRule(GrammarRule rule, GrammarDeclarations declarations) {
        if(rule.kind() != RuleKind.PARSER || rule.entry() || hasAssignment(rule.body()) || containsObjectAction(rule.body())) {
            return false;
        }

        String returnsType = simpleTypeName(rule.returnsType()).toUpperCase(Locale.ROOT);

        if(Set.of("STRING", "NUMBER", "BOOLEAN", "INT", "INTEGER", "FLOAT", "DOUBLE", "DECIMAL", "DATE").contains(returnsType)) {
            return true;
        }

        if(isQuotedEnumRule(rule) || isAlternativeRule(rule)) {
            return false;
        }

        Set<String> referenced = identifiersOutsideQuotes(rule.body());
        referenced.removeAll(PRIMITIVE_RULES);
        referenced.removeIf(value -> declarations.rule(value) == null);
        return referenced.stream().noneMatch(value -> {
            GrammarRule target = declarations.rule(value);
            return target != null && (isClassRule(target) || isAlternativeRule(target) || target.kind() == RuleKind.TYPE_ALIAS || target.kind() == RuleKind.INTERFACE);
        });
    }

    /** Descripción: Evalúa la condición «isClassRule» sin modificar el estado externo. Entradas: rule: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean isClassRule(GrammarRule rule) {
        return rule.kind() == RuleKind.PARSER && (rule.entry() || hasAssignment(rule.body()) || containsObjectAction(rule.body()) || isQualifiedClassifierReturn(rule.returnsType()));
    }

    /** Descripción: Evalúa la condición «isQualifiedClassifierReturn» sin modificar el estado externo. Entradas: returnsType: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ static boolean isQualifiedClassifierReturn(String returnsType) {
        String value = trim(returnsType);
        return value.contains("::") || value.contains(".");
    }

    /** Descripción: Ejecuta la operación «classifierName» correspondiente a la responsabilidad de Assignment. Entradas: rule: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String classifierName(GrammarRule rule) {
        if(isQualifiedClassifierReturn(rule.returnsType())) {
            return simpleTypeName(rule.returnsType());
        }

        return rule.name();
    }

    /** Descripción: Ejecuta la operación «inferImplementationType» correspondiente a la responsabilidad de Assignment. Entradas: rule: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String inferImplementationType(GrammarRule rule) {
        String compactBody = maskQuotedText(rule.body()).replaceAll("\\s+", "").toUpperCase(Locale.ROOT);

        if("INT".equals(compactBody) || "INTEGER".equals(compactBody)) {
            return "java.lang.Integer";
        }

        String type = simpleTypeName(rule.returnsType()).toLowerCase(Locale.ROOT);

        return switch(type) {
            case "number", "double", "float", "decimal" -> "java.lang.Double";
            case "int", "integer", "long", "short", "byte" -> "java.lang.Integer";
            case "boolean", "bool" -> "java.lang.Boolean";
            default -> "java.lang.String";
        };
    }

    /** Descripción: Ejecuta la operación «determineEntryRule» correspondiente a la responsabilidad de Assignment. Entradas: rules: dato de entrada necesario para la operación; classes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String determineEntryRule(List<GrammarRule> rules, Map<String, ClassBuilder> classes) {
        for(GrammarRule rule : rules) {
            if(rule.entry() && classes.containsKey(rule.name())) {
                return rule.name();
            }
        }

        for(GrammarRule rule : rules) {
            if(rule.kind() == RuleKind.PARSER && classes.containsKey(rule.name())) {
                return rule.name();
            }
        }

        return null;
    }

    /** Descripción: Valida las condiciones necesarias para «ensureReferencedClassDeclarations» y rechaza entradas no válidas. Entradas: declarations: dato de entrada necesario para la operación; classes: dato de entrada necesario para la operación; enums: dato de entrada necesario para la operación; dataTypes: dato de entrada necesario para la operación; names: dato de entrada necesario para la operación; warnings: dato de entrada necesario para la operación; sourceFormat: dato de entrada necesario para la operación; forwardingRules: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void ensureReferencedClassDeclarations(GrammarDeclarations declarations, Map<String, ClassBuilder> classes, EnumRegistry enums, Map<String, MetaDataType> dataTypes, NameAllocator names, List<TransformationWarning> warnings, String sourceFormat, Map<String, String> forwardingRules) {
        Set<String> referenced = new LinkedHashSet<>();

        for(ClassBuilder builder : new ArrayList<>(classes.values())) {
            for(Assignment assignment : readAssignments(builder.body)) {
                String expression = assignment.expression();

                if(expression.startsWith("[")) {
                    referenced.add(resolveForwardingRule(parseCrossReference(expression).targetRule(), forwardingRules));
                } else {
                    String simple = resolveForwardingRule(simpleExpressionName(expression), forwardingRules);

                    if(!simple.isBlank() && !isPrimitiveExpression(simple) && !enums.containsRule(simple) && !dataTypes.containsKey(simple)) {
                        GrammarRule target = declarations.rule(simple);

                        if(target == null) {
                            if("+=".equals(assignment.operator()) || Character.isUpperCase(simple.charAt(0))) {
                                referenced.add(simple);
                            }
                        } else if(!isDataTypeRule(target, declarations) && !isQuotedEnumRule(target)) {
                            referenced.add(simple);
                        }
                    }
                }
            }
        }

        for(String ruleName : referenced) {
            if(ruleName.isBlank() || classes.containsKey(ruleName)) {
                continue;
            }

            String classifierName = names.allocate(ruleName, "Class", "$.rules[" + ruleName + "]", "referenced class");
            ClassBuilder placeholder = new ClassBuilder(ruleName, classifierId(classifierName), classifierName, MetaClassKind.CLASS, "", 0, false, sourceFormat);
            placeholder.annotations.put("placeholder", "true");
            classes.put(ruleName, placeholder);
            warnings.add(new TransformationWarning("IMPORT_UNRESOLVED_GRAMMAR_TARGET", "$.rules[" + ruleName + "]", "Referenced rule " + ruleName + " could not be reconstructed as a declared class, so an editable placeholder class was created"));
        }
    }

    /** Descripción: Crea o prepara el resultado de «createInheritanceRelations» dentro de Assignment. Entradas: supertypesByRule: dato de entrada necesario para la operación; classes: dato de entrada necesario para la operación; relations: dato de entrada necesario para la operación; warnings: dato de entrada necesario para la operación; forwardingRules: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void createInheritanceRelations(Map<String, List<String>> supertypesByRule, Map<String, ClassBuilder> classes, List<MetaRelation> relations, List<TransformationWarning> warnings, Map<String, String> forwardingRules) {
        Set<String> relationIds = new HashSet<>();

        for(Map.Entry<String, List<String>> entry : supertypesByRule.entrySet()) {
            ClassBuilder supertype = classes.get(entry.getKey());

            if(supertype == null) {
                continue;
            }

            for(String alternative : entry.getValue()) {
                ClassBuilder subtype = classes.get(resolveForwardingRule(alternative, forwardingRules));

                if(subtype == null) {
                    warnings.add(new TransformationWarning("IMPORT_UNRESOLVED_ABSTRACT_ALTERNATIVE", "$.rules[" + entry.getKey() + "]", "Alternative " + alternative + " could not be linked to abstract rule " + entry.getKey()));
                    continue;
                }

                MetaRelationKind kind = supertype.kind == MetaClassKind.INTERFACE ? MetaRelationKind.IMPLEMENTATION : MetaRelationKind.INHERITANCE;
                String baseId = (kind == MetaRelationKind.IMPLEMENTATION ? "implementation:" : "supertype:") + subtype.id + ":" + supertype.id;
                String relationId = uniqueId(baseId, relationIds);
                relations.add(new MetaRelation(relationId, "", subtype.id, supertype.id, kind, null, null, "", true, true, Map.of("sourceRule", entry.getKey(), "inferred", "true")));
            }
        }
    }


    /** Descripción: Crea o prepara el resultado de «createDeclaredSupertypeRelations» dentro de Assignment. Entradas: declaredParentsByRule: dato de entrada necesario para la operación; classes: dato de entrada necesario para la operación; relations: dato de entrada necesario para la operación; warnings: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void createDeclaredSupertypeRelations(Map<String, List<String>> declaredParentsByRule, Map<String, ClassBuilder> classes, List<MetaRelation> relations, List<TransformationWarning> warnings) {
        Set<String> relationIds = relations.stream().map(MetaRelation::id).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        for(Map.Entry<String, List<String>> entry : declaredParentsByRule.entrySet()) {
            ClassBuilder subtype = classes.get(entry.getKey());

            if(subtype == null) {
                continue;
            }

            for(String parentName : entry.getValue()) {
                ClassBuilder parent = classes.get(simpleTypeName(parentName));

                if(parent == null) {
                    warnings.add(new TransformationWarning("IMPORT_UNRESOLVED_DECLARED_SUPERTYPE", "$.rules[" + entry.getKey() + "]", "Declared supertype " + parentName + " could not be resolved for " + entry.getKey()));
                    continue;
                }

                MetaRelationKind kind = parent.kind == MetaClassKind.INTERFACE ? MetaRelationKind.IMPLEMENTATION : MetaRelationKind.INHERITANCE;

                if(subtype.kind == MetaClassKind.INTERFACE && parent.kind == MetaClassKind.INTERFACE) {
                    kind = MetaRelationKind.INHERITANCE;
                }

                String baseId = (kind == MetaRelationKind.IMPLEMENTATION ? "implementation:" : "supertype:") + subtype.id + ":" + parent.id;
                String relationId = uniqueId(baseId, relationIds);
                relations.add(new MetaRelation(relationId, "", subtype.id, parent.id, kind, null, null, "", true, true, Map.of("sourceRule", entry.getKey(), "declared", "true")));
            }
        }
    }

    /** Descripción: Ejecuta la operación «detectLeftRecursion» correspondiente a la responsabilidad de Assignment. Entradas: rules: dato de entrada necesario para la operación; warnings: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void detectLeftRecursion(List<GrammarRule> rules, List<TransformationWarning> warnings) {
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

    /** Descripción: Ejecuta la operación «firstUnassignedRuleInvocation» correspondiente a la responsabilidad de Assignment. Entradas: alternative: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String firstUnassignedRuleInvocation(String alternative) {
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

    /** Descripción: Ejecuta la operación «inferScopeKey» correspondiente a la responsabilidad de Assignment. Entradas: target: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static String inferScopeKey(ClassBuilder target) {
        if(target == null) {
            return "";
        }

        if(target.identifierFeatures.contains("name")) {
            return "name";
        }

        return target.identifierFeatures.stream().findFirst().orElse("");
    }

    /** Descripción: Ejecuta la operación «uniqueFeatureName» correspondiente a la responsabilidad de Assignment. Entradas: requested: solicitud con los datos necesarios para ejecutar la operación; used: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; warnings: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static String uniqueFeatureName(String requested, Set<String> used, String path, List<TransformationWarning> warnings) {
        String base = requested == null || requested.isBlank() ? "value" : requested;
        String candidate = base;
        int suffix = 2;

        while(!used.add(candidate.toLowerCase(Locale.ROOT))) {
            candidate = base + suffix;
            suffix += 1;
        }

        if(!candidate.equals(base)) {
            warnings.add(new TransformationWarning("IMPORT_FEATURE_NAME_CONFLICT_AUTO_FIXED", path, "Feature name " + base + " was repeated and was renamed to " + candidate));
        }

        return candidate;
    }

    /** Descripción: Ejecuta la operación «uniqueId» correspondiente a la responsabilidad de Assignment. Entradas: base: dato de entrada necesario para la operación; used: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static String uniqueId(String base, Set<String> used) {
        String candidate = base;
        int suffix = 2;

        while(!used.add(candidate)) {
            candidate = base + ":" + suffix;
            suffix += 1;
        }

        return candidate;
    }

    /** Descripción: Ejecuta la operación «classifierId» correspondiente a la responsabilidad de Assignment. Entradas: name: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static String classifierId(String name) {
        return "class:" + ImportParsingSupport.safeId(name, "class");
    }

    enum RuleKind {
        PARSER,
        ENUM,
        TYPE_ALIAS,
        INTERFACE
    }

    record GrammarRule(String name, String returnsType, String body, boolean entry, RuleKind kind, int line, int start) {
    }

    record GrammarDeclarations(List<GrammarRule> rules) {
        /** Descripción: Ejecuta la operación «rule» correspondiente a la responsabilidad de Assignment. Entradas: name: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ GrammarRule rule(String name) {
            return rules.stream().filter(item -> item.name().equals(name)).findFirst().orElse(null);
        }
    }

    record Assignment(String featureName, String operator, String expression, char immediateQuantifier, List<Character> enclosingQuantifiers, int offset) {
    }

    record Expression(String text, int end) {
    }

    record GroupRange(int open, int close, char quantifier) {
    }

    record CrossReference(String targetRule, String terminalRule, String syntax) {
    }


}
