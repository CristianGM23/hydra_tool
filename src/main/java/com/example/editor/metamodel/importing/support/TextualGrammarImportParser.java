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
    // an object action «{X}», «{infer X}» or a rewrite action «{X.feature=current}» (keywords are blanked out first, so '{' never matches)
    private static final Pattern OBJECT_ACTION = Pattern.compile("\\{\\s*(?:infer\\s+)?([A-Za-z_][\\w:]*)\\s*(\\.|\\})");
    private static final Pattern QUOTED = Pattern.compile("'(?:\\\\.|[^'\\\\])*'|\"(?:\\\\.|[^\"\\\\])*\"");

    /**
     * FIND-GR-IMPORT-SILENT-ALTERNATIVE-ACTIONS: since D-2/D-3 the type a rule returns and the objects its actions create are represented
     * (TextualGrammarTypeInference). An action written in a form the importer does not understand stays inside the rule's own features; that
     * loss is still reported instead of being silent.
     */
    private static void reportUnrepresentedConstructs(GrammarRule rule, TextualGrammarTypeInference.Segmentation segmentation, List<TransformationWarning> warnings) {
        List<String> parts = new ArrayList<>();
        parts.add(segmentation.ownBody());
        segmentation.segments().forEach(segment -> parts.add(segment.body()));
        List<String> unread = new ArrayList<>();
        String ownType = typeOf(rule);
        for(String part : parts) {
            Matcher action = OBJECT_ACTION.matcher(QUOTED.matcher(part).replaceAll(" "));
            while(action.find()) {
                // an action that only names the rule's own type ({infer Person} in Person, what HYDRA generates) creates no other object
                if(action.group(2).equals("}") && simpleTypeName(action.group(1)).equals(ownType)) continue;
                unread.add(action.group(1));
            }
        }
        if(!unread.isEmpty()) {
            warnings.add(new TransformationWarning("IMPORT_GRAMMAR_ALTERNATIVE_ACTIONS_MERGED", path(rule), "Rule " + rule.name() + " creates " + String.join(", ", unread)
                    + " with an action form this importer does not read; its features were merged into the rule's type"));
        }
    }
    private static final Class<?>[] FOCUSED_COMPONENTS = { TextualGrammarScanner.class, TextualGrammarSyntaxSupport.class, TextualGrammarModelSupport.class };
    private static final Pattern ENUM_BRACE_HEADER = Pattern.compile("(?m)^\\s*enum\\s+([A-Za-z_][A-Za-z0-9_]*)(?:\\s+returns\\s+([^\\{]+))?\\s*\\{");
    private static final Pattern ENUM_COLON_HEADER = Pattern.compile("(?m)^\\s*enum\\s+([A-Za-z_][A-Za-z0-9_]*)(?:\\s+returns\\s+([^:]+))?\\s*:");
    private static final Pattern TYPE_ALIAS_HEADER = Pattern.compile("(?m)^\\s*type\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=");
    private static final Pattern INTERFACE_HEADER = Pattern.compile("(?m)^\\s*interface\\s+([A-Za-z_][A-Za-z0-9_]*)(?:\\s+extends\\s+([^\\{]+))?\\s*\\{");
    // D-1: «R infers T:» is read like «R returns T:»; a qualified type («model::T», «ecore::EString») is read whole, never cut at its '::'
    // rule parameters («Expression<In>:», Langium and Xtext) only guide parsing: the type is still named after the rule
    private static final Pattern PARSER_RULE_HEADER = Pattern.compile("(?m)^\\s*(entry\\s+)?([A-Za-z_][A-Za-z0-9_]*)(?:\\s*<[^<>:;]*>)?(?:\\s+(?:returns|infers)\\s+([A-Za-z_][A-Za-z0-9_.]*(?:::[A-Za-z_][A-Za-z0-9_]*)*))?\\s*:(?!:)");
    private static final Pattern INFIX_RULE_HEADER = Pattern.compile("(?m)^\\s*infix\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+on\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*:(?!:)");
    private static final Pattern FRAGMENT_RULE_HEADER = Pattern.compile("(?m)^\\s*fragment\\s+[A-Za-z_][A-Za-z0-9_]*(?:\\s+(?:returns|infers)\\s+[A-Za-z_][A-Za-z0-9_.:]*)?\\s*:(?!:)");
    // a rule header made of two or more words before its colon (never "::" of a qualified name): "X infers Y:", "infix X on Y:", "fragment X:"
    private static final Pattern UNRECOGNIZED_RULE_HEADER = Pattern.compile("(?m)^[ \\t]*([A-Za-z_][A-Za-z0-9_]*(?:[ \\t]+[A-Za-z_][A-Za-z0-9_.]*)+)[ \\t]*:(?!:)");
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
        Map<String, String> terminals = TextualGrammarTypeInference.terminalTypes(cleaned);
        GrammarDeclarations declarations = inlineFragments(readDeclarations(cleaned, warnings), TextualGrammarTypeInference.fragments(cleaned));
        NameAllocator classifierNames = new NameAllocator(warnings);
        EnumRegistry enumRegistry = new EnumRegistry(classifierNames, warnings);
        Map<String, MetaDataType> dataTypesByRule = new LinkedHashMap<>();
        Map<String, ClassBuilder> classesByRule = new LinkedHashMap<>();
        Map<String, List<String>> alternativesByAbstractRule = new LinkedHashMap<>();
        Map<String, List<String>> declaredParentsByRule = new LinkedHashMap<>();
        // D-1/D-2/D-5: the type every object rule produces, and the types produced by a rule under another name (infers/returns T)
        Map<String, String> typeByRule = new LinkedHashMap<>();
        declarations.rules().forEach(rule -> typeByRule.put(rule.name(), typeOf(rule)));
        Set<String> producedTypes = new LinkedHashSet<>();
        for(GrammarRule rule : declarations.rules()) {
            if((isClassRule(rule) || isAlternativeRule(rule)) && !typeByRule.get(rule.name()).equals(rule.name()) && !isDataTypeReturn(rule.returnsType())) producedTypes.add(typeByRule.get(rule.name()));
        }
        Map<String, String> forwardingRules = forwardingRules(declarations, warnings, producedTypes);
        // the classes in declaration order: an existing builder, or the name of a type whose contributions are collected first (D-2/D-3)
        List<Object> order = new ArrayList<>();
        Map<String, TypeContribution> contributions = new LinkedHashMap<>();
        Map<String, Set<String>> inferredSupertypes = new LinkedHashMap<>();
        Map<String, String> typeAliases = new LinkedHashMap<>();

        for(GrammarRule rule : declarations.rules()) {
            if(rule.kind() == RuleKind.ENUM || isQuotedEnumRule(rule)) {
                enumRegistry.register(rule);
            }
        }

        for(GrammarRule rule : declarations.rules()) {
            if(rule.kind() == RuleKind.ENUM || isQuotedEnumRule(rule)) {
                continue;
            }

            if(rule.kind() == RuleKind.PARSER && isAlternativeRule(rule) && !typeByRule.get(rule.name()).equals(rule.name())) {
                // D-1/D-2: «X returns T: A | B» produces T, whose subtypes are what A and B produce; no class X exists
                String produced = typeByRule.get(rule.name());
                GrammarRule declaredType = declarations.rule(produced);
                if(declaredType == null || (declaredType.kind() != RuleKind.INTERFACE && declaredType.kind() != RuleKind.TYPE_ALIAS)) contribution(contributions, order, produced, rule, MetaClassKind.CLASS);
                typeAliases.put(rule.name(), produced);
                passThroughSupertypes(rule.body(), produced, declarations, typeByRule, producedTypes, inferredSupertypes);
                continue;
            }

            if(rule.kind() == RuleKind.TYPE_ALIAS || isAlternativeRule(rule)) {
                String classifierName = classifierNames.allocate(rule.name(), "AbstractClass", path(rule), "abstract class");
                ClassBuilder builder = new ClassBuilder(rule.name(), classifierId(classifierName), classifierName, MetaClassKind.ABSTRACT_CLASS, rule.body(), rule.line(), rule.entry(), sourceFormat);
                builder.annotations.put("abstractionOrigin", rule.kind() == RuleKind.TYPE_ALIAS ? "langium-type-alias" : "parser-alternatives");
                classesByRule.put(rule.name(), builder);
                order.add(builder);
                alternativesByAbstractRule.put(rule.name(), alternativeNames(rule.body()));
                warnings.add(new TransformationWarning("IMPORT_ABSTRACT_RULE_INFERRED", path(rule), "Rule " + rule.name() + " was imported as an abstract class because it only groups alternative object rules"));
                continue;
            }

            if(rule.kind() == RuleKind.INTERFACE) {
                String classifierName = classifierNames.allocate(rule.name(), "Interface", path(rule), "interface");
                ClassBuilder builder = new ClassBuilder(rule.name(), classifierId(classifierName), classifierName, MetaClassKind.INTERFACE, rule.body(), rule.line(), rule.entry(), sourceFormat);
                builder.annotations.put("abstractionOrigin", "langium-interface");
                classesByRule.put(rule.name(), builder);
                order.add(builder);
                declaredParentsByRule.put(rule.name(), splitNames(rule.returnsType()));
                continue;
            }

            if(rule.kind() == RuleKind.INFIX) {
                // D-4: «infix B on P: ops» is the binary type B (left/right are what P produces, operator one of the declared operators)
                TypeContribution binary = contribution(contributions, order, rule.name(), rule, MetaClassKind.CLASS);
                binary.bodies.add("left=" + rule.returnsType() + " operator=(" + String.join(" | ", quotedOperators(rule.body())) + ") right=" + rule.returnsType());
                binary.annotations.put("infix:operand", rule.returnsType());
                binary.annotations.put("infix:precedence", precedence(rule.body()));
                continue;
            }

            if(isForwardingRule(rule) && producedTypes.contains(rule.name())) {
                // D-5: «E: B;» where another rule also produces E: E is the common supertype, not an alias of B
                contribution(contributions, order, rule.name(), rule, MetaClassKind.CLASS);
                String target = typeByRule.getOrDefault(alternativeNames(rule.body()).getFirst(), alternativeNames(rule.body()).getFirst());
                if(!target.equals(rule.name())) inferredSupertypes.computeIfAbsent(target, key -> new LinkedHashSet<>()).add(rule.name());
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
                String type = typeByRule.get(rule.name());
                GrammarRule declaredType = declarations.rule(type);
                if(!type.equals(rule.name()) && declaredType != null && (declaredType.kind() == RuleKind.INTERFACE || declaredType.kind() == RuleKind.TYPE_ALIAS)) {
                    // the rule produces a declared interface or union type: its features are declared there
                    typeAliases.put(rule.name(), type);
                    continue;
                }
                TextualGrammarTypeInference.Segmentation segmentation = TextualGrammarTypeInference.segment(rule.body(), type);
                contribution(contributions, order, type, rule, MetaClassKind.CLASS).bodies.add(segmentation.ownBody());
                typeAliases.put(rule.name(), type);
                for(TextualGrammarTypeInference.Segment segment : segmentation.segments()) {
                    // D-3: the object an action creates is a subtype of the rule's type; a rewrite action contains the object built so far
                    TypeContribution created = contribution(contributions, order, segment.type(), rule, MetaClassKind.CLASS);
                    created.bodies.add(segment.rewriteFeature() == null ? segment.body() : segment.rewriteFeature() + segment.rewriteOperator() + rule.name() + " " + segment.body());
                    created.annotations.putIfAbsent("inferredByAction", "true");
                    if(!segment.type().equals(type)) inferredSupertypes.computeIfAbsent(segment.type(), key -> new LinkedHashSet<>()).add(type);
                }
                if(!segmentation.segments().isEmpty() || !type.equals(rule.name())) {
                    passThroughSupertypes(segmentation.ownBody(), type, declarations, typeByRule, producedTypes, inferredSupertypes);
                }
                reportUnrepresentedConstructs(rule, segmentation, warnings);
            }
        }

        // the types collected from several rules and actions become classes, in declaration order
        Set<String> supertypeNames = new LinkedHashSet<>();
        inferredSupertypes.values().forEach(supertypeNames::addAll);
        List<ClassBuilder> builders = new ArrayList<>();
        for(Object item : order) {
            if(item instanceof ClassBuilder builder) {
                builders.add(builder);
                continue;
            }
            TypeContribution contribution = contributions.get((String)item);
            String body = contribution.bodies.size() == 1 ? contribution.bodies.getFirst()
                    : String.join(" | ", contribution.bodies.stream().map(part -> "(" + part + ")").toList());
            MetaClassKind kind = contribution.kind;
            if("langium".equalsIgnoreCase(sourceFormat) && kind == MetaClassKind.CLASS && !hasAssignment(body) && supertypeNames.contains(contribution.type) && !contribution.entry) {
                kind = MetaClassKind.ABSTRACT_CLASS; // a Langium type that only unites its subtypes (type T = A | B)
            }
            String desired = contribution.type.equals(contribution.firstRule.name()) ? classifierName(contribution.firstRule) : contribution.type;
            String classifierName = classifierNames.allocate(desired, kind == MetaClassKind.ABSTRACT_CLASS ? "AbstractClass" : "Class", path(contribution.firstRule), "class");
            ClassBuilder builder = new ClassBuilder(contribution.firstRule.name(), classifierId(classifierName), classifierName, kind, body, contribution.firstRule.line(), contribution.entry, sourceFormat);
            builder.annotations.putAll(contribution.annotations);
            if(contribution.rules.size() > 1) builder.annotations.put("sourceRules", String.join(",", contribution.rules));
            classesByRule.put(contribution.type, builder);
            builders.add(builder);
        }
        typeAliases.forEach((rule, type) -> { if(classesByRule.containsKey(type)) classesByRule.put(rule, classesByRule.get(type)); });

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
        ensureReferencedClassDeclarations(declarations, classesByRule, builders, enumRegistry, dataTypesByRule, classifierNames, warnings, sourceFormat, forwardingRules, terminals);

        Map<ClassBuilder, Set<ClassBuilder>> ancestors = ancestors(alternativesByAbstractRule, declaredParentsByRule, inferredSupertypes, classesByRule, forwardingRules);
        for(ClassBuilder builder : builders) {
            builder.readFeatureSpecs(enumRegistry, dataTypesByRule, classesByRule, warnings, terminals, ancestors);
        }

        List<MetaRelation> relations = new ArrayList<>();

        for(ClassBuilder builder : builders) {
            builder.materializeFeatures(enumRegistry, dataTypesByRule, classesByRule, relations, warnings, forwardingRules, terminals);
        }

        createInheritanceRelations(alternativesByAbstractRule, classesByRule, relations, warnings, forwardingRules);
        createDeclaredSupertypeRelations(declaredParentsByRule, classesByRule, relations, warnings);
        createInferredSupertypeRelations(inferredSupertypes, classesByRule, relations);
        List<MetaClass> classes = builders.stream().map(ClassBuilder::build).toList();
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
        readInfixRules(content, occupied, rules);
        markFragments(content, occupied);
        readParserRules(content, occupied, rules);
        reportUnrecognizedRules(content, occupied, warnings);
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

    /**
     * Descripción: Avisa de toda declaración de regla con una cabecera de varias palabras que ningún lector ha consumido (Langium
     * «X infers Y:», «infix X on Y:», «fragment X:»…): antes se descartaba sin rastro. Los terminales y las cabeceras de la gramática no son
     * reglas del metamodelo y no se avisan. Entradas: contenido sin comentarios, posiciones consumidas y avisos. Salida: añade un aviso por regla.
     */
    private static void reportUnrecognizedRules(String content, boolean[] occupied, List<TransformationWarning> warnings) {
        Matcher matcher = UNRECOGNIZED_RULE_HEADER.matcher(content);
        while(matcher.find()) {
            if(isOccupied(occupied, matcher.start(1))) continue;
            String header = matcher.group(1).trim().replaceAll("\\s+", " ");
            String first = header.split(" ")[0].toLowerCase(Locale.ROOT);
            if(Set.of("terminal", "hidden", "grammar", "import", "generate", "with", "entry").contains(first)) continue;
            warnings.add(new TransformationWarning("IMPORT_GRAMMAR_RULE_NOT_IMPORTED", "$.rules[" + header + "]@line" + line(content, matcher.start(1)),
                    "Rule declaration '" + header + "' uses syntax this importer does not support (for example Langium infers, infix or fragment rules); the rule was not imported"));
        }
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

    /** Descripción: Lee las reglas «infix B on P: operadores;» (D-4); el tipo de operando P queda en returnsType. Entradas: content, occupied, result. Salida: ninguna. */
    private static void readInfixRules(String content, boolean[] occupied, List<GrammarRule> result) {
        Matcher matcher = INFIX_RULE_HEADER.matcher(content);
        while(matcher.find()) {
            if(isOccupied(occupied, matcher.start())) continue;
            int end = findStatementEnd(content, matcher.end());
            mark(occupied, matcher.start(), end + 1);
            result.add(new GrammarRule(matcher.group(1), matcher.group(2), content.substring(matcher.end(), end).trim(), false, RuleKind.INFIX, line(content, matcher.start()), matcher.start()));
        }
    }

    /** Descripción: Marca como leídas las reglas fragment: no son tipos y se expanden en las reglas que las llaman. Entradas: content, occupied. Salida: ninguna. */
    private static void markFragments(String content, boolean[] occupied) {
        Matcher matcher = FRAGMENT_RULE_HEADER.matcher(content);
        while(matcher.find()) {
            if(isOccupied(occupied, matcher.start())) continue;
            mark(occupied, matcher.start(), findStatementEnd(content, matcher.end()) + 1);
        }
    }

    /** Descripción: Expande los fragmentos en las reglas de parser. Entradas: declarations, fragments. Salida: declaraciones con los cuerpos expandidos. */
    private static GrammarDeclarations inlineFragments(GrammarDeclarations declarations, Map<String, String> fragments) {
        if(fragments.isEmpty()) return declarations;
        return new GrammarDeclarations(declarations.rules().stream().map(rule -> rule.kind() != RuleKind.PARSER ? rule
                : new GrammarRule(rule.name(), rule.returnsType(), TextualGrammarTypeInference.inlineFragments(rule.body(), fragments), rule.entry(), rule.kind(), rule.line(), rule.start())).toList());
    }

    /**
     * Descripción: Tipo que produce una regla (D-1/D-2): su nombre, salvo un «returns/infers T» que no sea un tipo de datos (T, o el nombre simple
     * de un tipo cualificado, como ya se hacía con «model::T»). Entradas: rule. Salida: nombre del tipo.
     */
    private static String typeOf(GrammarRule rule) {
        if(rule.kind() != RuleKind.PARSER) return rule.name();
        String returnsType = trim(rule.returnsType());
        if(returnsType.isBlank() || isDataTypeReturn(returnsType)) return rule.name();
        return simpleTypeName(returnsType);
    }

    /** Descripción: Indica si un tipo de retorno es primitivo (number, string, ecore::EString…). Entradas: returnsType. Salida: indicador. */
    private static boolean isDataTypeReturn(String returnsType) {
        String value = simpleTypeName(trim(returnsType));
        if(value.isBlank()) return false;
        return Set.of("STRING", "NUMBER", "BOOLEAN", "INT", "INTEGER", "FLOAT", "DOUBLE", "DECIMAL", "DATE", "BIGINT", "LONG", "CHAR").contains(value.toUpperCase(Locale.ROOT))
                || (value.length() > 1 && value.charAt(0) == 'E' && Character.isUpperCase(value.charAt(1)) && !"STRING".equals(TextualGrammarTypeInference.primitiveKeyword(value)))
                || Set.of("EString", "EJavaObject", "EJavaClass").contains(value);
    }

    /** Descripción: Aportación de una regla al tipo que produce. Entradas: contributions, order, type, rule, kind. Salida: la aportación (creada en orden de declaración si no existía). */
    private static TypeContribution contribution(Map<String, TypeContribution> contributions, List<Object> order, String type, GrammarRule rule, MetaClassKind kind) {
        TypeContribution existing = contributions.get(type);
        if(existing == null) {
            existing = new TypeContribution(type, rule, kind);
            contributions.put(type, existing);
            order.add(type);
        }
        if(!existing.rules.contains(rule.name())) existing.rules.add(rule.name());
        if(rule.entry() && type.equals(typeOf(rule))) existing.entry = true;
        return existing;
    }

    /** Descripción: Operadores de una regla infix, entre comillas, en orden. Entradas: body. Salida: literales. */
    private static List<String> quotedOperators(String body) {
        return quotedValues(body).stream().map(value -> "'" + value.replace("'", "\\'") + "'").toList();
    }

    /** Descripción: Precedencia declarada de una regla infix, del grupo más fuerte al más débil tal como se escribe («%» > «^» > «*» | «/» …). Entradas: body. Salida: texto. */
    private static String precedence(String body) {
        String masked = maskQuotedText(body);
        List<String> groups = new ArrayList<>();
        int start = 0;
        for(int index = 0; index <= masked.length(); index++) {
            if(index == masked.length() || masked.charAt(index) == '>') {
                String group = body.substring(start, index);
                List<String> operators = quotedValues(group);
                String rest = QUOTED_LITERAL.matcher(group).replaceAll(" ").replace("|", " ").trim().replaceAll("\\s+", " ");
                groups.add(String.join(" | ", operators) + (rest.isBlank() ? "" : " (" + rest + ")"));
                start = index + 1;
            }
        }
        return String.join(" > ", groups);
    }

    /**
     * Descripción: Una alternativa que empieza por una llamada no asignada devuelve lo que devuelve esa regla: su tipo es subtipo del de la regla
     * (Langium: «Mult infers Expr: Prim (…)*» hace de Prim un Expr). Entradas: ownBody, type, declarations, typeByRule, producedTypes,
     * supertypes. Salida: añade las generalizaciones.
     */
    private static void passThroughSupertypes(String ownBody, String type, GrammarDeclarations declarations, Map<String, String> typeByRule, Set<String> producedTypes, Map<String, Set<String>> supertypes) {
        for(String alternative : splitTopLevel(ownBody, '|')) {
            String called = firstUnassignedRuleInvocation(alternative);
            GrammarRule target = called.isBlank() ? null : declarations.rule(called);
            if(target == null) continue;
            boolean objectRule = isClassRule(target) || isAlternativeRule(target) || target.kind() == RuleKind.TYPE_ALIAS || target.kind() == RuleKind.INTERFACE
                    || target.kind() == RuleKind.INFIX || producedTypes.contains(target.name());
            String subtype = typeByRule.getOrDefault(called, called);
            if(objectRule && !subtype.equals(type)) supertypes.computeIfAbsent(subtype, key -> new LinkedHashSet<>()).add(type);
        }
    }

    /** Descripción: Crea las generalizaciones inferidas por acciones, alternativas de paso y reenvíos hacia un tipo producido. Entradas: supertypes, classes, relations. Salida: ninguna. */
    private static void createInferredSupertypeRelations(Map<String, Set<String>> supertypes, Map<String, ClassBuilder> classes, List<MetaRelation> relations) {
        Set<String> relationIds = relations.stream().map(MetaRelation::id).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> pairs = new HashSet<>();
        relations.stream().filter(r -> r.kind() == MetaRelationKind.INHERITANCE || r.kind() == MetaRelationKind.IMPLEMENTATION).forEach(r -> pairs.add(r.sourceTypeId() + ">" + r.targetTypeId()));
        for(Map.Entry<String, Set<String>> entry : supertypes.entrySet()) {
            ClassBuilder subtype = classes.get(entry.getKey());
            for(String parentName : entry.getValue()) {
                ClassBuilder parent = classes.get(parentName);
                if(subtype == null || parent == null || subtype == parent || !pairs.add(subtype.id + ">" + parent.id)) continue;
                MetaRelationKind kind = parent.kind == MetaClassKind.INTERFACE && subtype.kind != MetaClassKind.INTERFACE ? MetaRelationKind.IMPLEMENTATION : MetaRelationKind.INHERITANCE;
                String relationId = uniqueId((kind == MetaRelationKind.IMPLEMENTATION ? "implementation:" : "supertype:") + subtype.id + ":" + parent.id, relationIds);
                relations.add(new MetaRelation(relationId, "", subtype.id, parent.id, kind, null, null, "", true, true, Map.of("sourceRule", entry.getKey(), "inferred", "true", "inference", "grammar-type")));
            }
        }
    }

    /**
     * Descripción: Supertipos (directos e indirectos) de cada clase según las alternativas, los supertipos declarados y los inferidos. Entradas:
     * alternatives, declaredParents, inferred, classes, forwardingRules. Salida: clase -> ancestros.
     */
    private static Map<ClassBuilder, Set<ClassBuilder>> ancestors(Map<String, List<String>> alternatives, Map<String, List<String>> declaredParents, Map<String, Set<String>> inferred,
            Map<String, ClassBuilder> classes, Map<String, String> forwardingRules) {
        Map<ClassBuilder, Set<ClassBuilder>> direct = new java.util.IdentityHashMap<>();
        java.util.function.BiConsumer<ClassBuilder, ClassBuilder> edge = (sub, sup) -> { if(sub != null && sup != null && sub != sup) direct.computeIfAbsent(sub, key -> new LinkedHashSet<>()).add(sup); };
        alternatives.forEach((abstractRule, names) -> names.forEach(name -> edge.accept(classes.get(resolveForwardingRule(name, forwardingRules)), classes.get(abstractRule))));
        declaredParents.forEach((rule, parents) -> parents.forEach(parent -> edge.accept(classes.get(rule), classes.get(simpleTypeName(parent)))));
        inferred.forEach((sub, parents) -> parents.forEach(parent -> edge.accept(classes.get(sub), classes.get(parent))));
        Map<ClassBuilder, Set<ClassBuilder>> result = new java.util.IdentityHashMap<>();
        for(ClassBuilder start : direct.keySet()) {
            Set<ClassBuilder> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            Deque<ClassBuilder> pending = new ArrayDeque<>(direct.get(start));
            while(!pending.isEmpty()) {
                ClassBuilder next = pending.pop();
                if(next != start && seen.add(next)) pending.addAll(direct.getOrDefault(next, Set.of()));
            }
            result.put(start, seen);
        }
        return result;
    }

    /** Lo que aportan a un tipo las reglas y acciones que lo producen (D-2/D-3), hasta crear su clase. */
    static final class TypeContribution {
        final String type;
        final GrammarRule firstRule;
        final MetaClassKind kind;
        final List<String> bodies = new ArrayList<>();
        final List<String> rules = new ArrayList<>();
        final Map<String, String> annotations = new LinkedHashMap<>();
        boolean entry;

        /** Descripción: Aportación vacía. Entradas: type, firstRule, kind. Salida: aportación. */
        TypeContribution(String type, GrammarRule firstRule, MetaClassKind kind) {
            this.type = type;
            this.firstRule = firstRule;
            this.kind = kind;
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

    /** Descripción: Ejecuta la operación «forwardingRules» correspondiente a la responsabilidad de Assignment. Entradas: declarations: dato de entrada necesario para la operación; warnings: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String, String> forwardingRules(GrammarDeclarations declarations, List<TransformationWarning> warnings, Set<String> producedTypes) {
        Map<String, String> result = new LinkedHashMap<>();

        for(GrammarRule rule : declarations.rules()) {
            // D-5: «E: B;» is only an alias of B when no other rule also produces E; otherwise E is their common supertype
            if(!isForwardingRule(rule) || producedTypes.contains(rule.name())) {
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

        if(isDataTypeReturn(rule.returnsType())) {
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
            return target != null && (isClassRule(target) || isAlternativeRule(target) || target.kind() == RuleKind.TYPE_ALIAS || target.kind() == RuleKind.INTERFACE
                    || target.kind() == RuleKind.INFIX || producesAnotherType(target, declarations));
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

    /** Descripción: Valida las condiciones necesarias para «ensureReferencedClassDeclarations» y rechaza entradas no válidas. Entradas: declarations: dato de entrada necesario para la operación; classes: dato de entrada necesario para la operación; enums: dato de entrada necesario para la operación; dataTypes: dato de entrada necesario para la operación; names: dato de entrada necesario para la operación; warnings: dato de entrada necesario para la operación; sourceFormat: dato de entrada necesario para la operación; forwardingRules: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void ensureReferencedClassDeclarations(GrammarDeclarations declarations, Map<String, ClassBuilder> classes, List<ClassBuilder> builders, EnumRegistry enums, Map<String, MetaDataType> dataTypes, NameAllocator names, List<TransformationWarning> warnings, String sourceFormat, Map<String, String> forwardingRules, Map<String, String> terminals) {
        Set<String> referenced = new LinkedHashSet<>();

        for(ClassBuilder builder : new ArrayList<>(builders)) {
            for(Assignment assignment : readAssignments(builder.body)) {
                String expression = assignment.expression();

                if(expression.startsWith("[")) {
                    referenced.add(resolveForwardingRule(parseCrossReference(expression).targetRule(), forwardingRules));
                } else {
                    String simple = resolveForwardingRule(simpleExpressionName(expression), forwardingRules);

                    if(!simple.isBlank() && !isPrimitiveExpression(simple) && !terminals.containsKey(simple) && !enums.containsRule(simple) && !dataTypes.containsKey(simple)) {
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
            builders.add(placeholder);
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

    /** Descripción: Indica si otra regla de objeto produce el tipo con el nombre de esta regla (D-5). Entradas: rule, declarations. Salida: indicador. */
    private static boolean producesAnotherType(GrammarRule rule, GrammarDeclarations declarations) {
        return declarations.rules().stream().anyMatch(other -> other != rule && (isClassRule(other) || isAlternativeRule(other)) && !isDataTypeReturn(other.returnsType()) && typeOf(other).equals(rule.name()) && !other.name().equals(rule.name()));
    }

    enum RuleKind {
        PARSER,
        ENUM,
        TYPE_ALIAS,
        INTERFACE,
        INFIX
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
