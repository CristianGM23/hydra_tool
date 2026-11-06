package com.example.editor.metamodel.importing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaEnum;
import com.example.editor.metamodel.core.MetaEnumLiteral;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.TransformationWarning;
import com.example.editor.metamodel.importing.importers.LangiumMetamodelImporter;
import com.example.editor.metamodel.importing.importers.XtextMetamodelImporter;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Langium D-1..D-5 (authorisation of 30/10): the textual grammar importer recovers the types the grammar language itself infers. Langium:
 * {@code R infers T} and an unqualified {@code returns T} name the type T, and every rule that produces T contributes to one class (D-1/D-2);
 * an action creates a subtype of the rule's type with the features assigned after it along the parse path, and a rewrite action
 * {@code {X.f=current}} adds the containment f (D-3, the classic precedence encoding of Langium and Xtext); {@code infix B on P} is a binary
 * type with left/operator/right and its precedence (D-4); a forwarding rule {@code E: B;} whose name is also a type produced by another rule is a
 * real supertype instead of collapsing (D-5). Fragments are inlined and terminal return types type the features. What HYDRA itself generates
 * is imported exactly as before. The real grammars are compared with the types langium-cli 4.4.0 generates (src/test/resources/grammar-real).
 */
class GrammarTypeInferenceImportTests {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test void anInferredTypeNamesTheClassInsteadOfTheRule() {
        MetaModel mm = langium("""
                grammar Items
                entry Model: items+=Item*;
                Item infers Thing: 'item' name=ID;
                terminal ID: /[a-z]+/;
                """).metamodel();
        assertThat(names(mm)).containsExactly("Model", "Thing");
        assertThat(containment(mm, "Model", "items")).isEqualTo("Thing");
    }

    @Test void rulesThatReturnTheSameTypeContributeToOneClass() {
        var output = langium("""
                grammar Items
                entry Model: items+=Item* others+=Other*;
                Item returns Element: 'item' name=ID;
                Other returns Element: 'other' name=ID code=ID;
                terminal ID: /[a-z]+/;
                """);
        MetaModel mm = output.metamodel();
        assertThat(names(mm)).containsExactly("Model", "Element");
        assertThat(features(mm, "Element")).containsExactlyInAnyOrder("name", "code");
        assertThat(containment(mm, "Model", "items")).isEqualTo("Element");
        assertThat(containment(mm, "Model", "others")).isEqualTo("Element");
        assertThat(output.warnings()).extracting(TransformationWarning::code).doesNotContain("IMPORT_GRAMMAR_RETURN_TYPE_NOT_IMPORTED");
    }

    @Test void actionsInAlternativesCreateSubtypesWithTheFeaturesAssignedAfterThem() {
        var output = langium("""
                grammar Values
                entry Model: items+=Primary*;
                Primary: '(' inner=Primary ')' | {infer NumberLiteral} value=INT | {infer Reference} name=ID;
                terminal INT returns number: /[0-9]+/;
                terminal ID: /[a-z]+/;
                """);
        MetaModel mm = output.metamodel();
        assertThat(names(mm)).containsExactly("Model", "Primary", "NumberLiteral", "Reference");
        assertThat(features(mm, "Primary")).containsExactly("inner");
        assertThat(features(mm, "NumberLiteral")).containsExactly("value");
        assertThat(features(mm, "Reference")).containsExactly("name");
        assertThat(generalisations(mm)).contains("NumberLiteral->Primary", "Reference->Primary");
        assertThat(output.warnings()).extracting(TransformationWarning::code).doesNotContain("IMPORT_GRAMMAR_ALTERNATIVE_ACTIONS_MERGED");
    }

    @Test void langiumRewriteActionsBuildTheBinaryExpressionTypeOfThePrecedenceChain() {
        MetaModel mm = langium("""
                grammar Calc
                entry Model: exprs+=Expr*;
                Expr: Add;
                Add infers Expr: Mult ({infer BinExpr.e1=current} op=('+'|'-') e2=Mult)*;
                Mult infers Expr: Prim ({infer BinExpr.e1=current} op=('*'|'/') e2=Prim)*;
                Prim: Lit | Group;
                Lit: val=NUMBER;
                Group: '(' ge=Expr ')';
                terminal NUMBER returns number: /[0-9]+/;
                """).metamodel();
        assertThat(names(mm)).containsExactlyInAnyOrder("Model", "Expr", "BinExpr", "Prim", "Lit", "Group");
        assertThat(features(mm, "BinExpr")).containsExactlyInAnyOrder("e1", "op", "e2");
        assertThat(containment(mm, "BinExpr", "e1")).isEqualTo("Expr");
        assertThat(containment(mm, "BinExpr", "e2")).isEqualTo("Expr");
        assertThat(enumLiteralValues(mm, "BinExpr", "op")).containsExactlyInAnyOrder("+", "-", "*", "/");
        assertThat(generalisations(mm)).contains("BinExpr->Expr", "Prim->Expr", "Lit->Prim", "Group->Prim");
        assertThat(mm.classes()).filteredOn(c -> c.name().equals("Expr")).singleElement().extracting(MetaClass::kind).isEqualTo(MetaClassKind.ABSTRACT_CLASS);
    }

    @Test void xtextRewriteActionsInsideGroupedAlternativesKeepTheFeaturesThatFollowTheGroup() {
        MetaModel mm = xtext("""
                grammar org.example.Ops with org.eclipse.xtext.common.Terminals
                generate ops "http://example.org/ops"
                Model: items+=Expression*;
                Expression: Addition;
                Addition returns Expression: Atom (({Plus.left=current} '+' | {Minus.left=current} '-') right=Atom)*;
                Atom returns Expression: {Value} value=INT;
                """).metamodel();
        assertThat(names(mm)).containsExactlyInAnyOrder("Model", "Expression", "Plus", "Minus", "Value");
        assertThat(features(mm, "Plus")).containsExactlyInAnyOrder("left", "right");
        assertThat(features(mm, "Minus")).containsExactlyInAnyOrder("left", "right");
        assertThat(containment(mm, "Plus", "left")).isEqualTo("Expression");
        assertThat(generalisations(mm)).contains("Plus->Expression", "Minus->Expression", "Value->Expression");
    }

    @Test void anInfixRuleIsABinaryTypeWithItsOperatorsAndPrecedence() {
        MetaModel mm = langium("""
                grammar Arith
                entry Model: items+=Expression*;
                Expression: BinaryExpression;
                infix BinaryExpression on PrimaryExpression: '%' > '^' > '*' | '/' > '+' | '-';
                PrimaryExpression infers Expression: '(' Expression ')' | {infer NumberLiteral} value=NUMBER;
                terminal NUMBER returns number: /[0-9]+/;
                """).metamodel();
        assertThat(names(mm)).containsExactlyInAnyOrder("Model", "Expression", "BinaryExpression", "NumberLiteral");
        assertThat(features(mm, "BinaryExpression")).containsExactlyInAnyOrder("left", "operator", "right");
        assertThat(containment(mm, "BinaryExpression", "left")).isEqualTo("Expression");
        assertThat(containment(mm, "BinaryExpression", "right")).isEqualTo("Expression");
        assertThat(enumLiteralValues(mm, "BinaryExpression", "operator")).containsExactly("%", "^", "*", "/", "+", "-");
        MetaClass binary = mm.classes().stream().filter(c -> c.name().equals("BinaryExpression")).findFirst().orElseThrow();
        assertThat(binary.annotations()).containsEntry("infix:operand", "PrimaryExpression").containsEntry("infix:precedence", "% > ^ > * | / > + | -");
        assertThat(generalisations(mm)).contains("BinaryExpression->Expression", "NumberLiteral->Expression");
    }

    @Test void aForwardingRuleIsCollapsedUnlessItsNameIsAlsoAProducedType() {
        var collapsed = langium("""
                grammar Fwd
                entry Model: items+=Item*;
                Item: Thing;
                Thing: 'thing' name=ID;
                terminal ID: /[a-z]+/;
                """);
        assertThat(names(collapsed.metamodel())).containsExactly("Model", "Thing");
        assertThat(collapsed.warnings()).extracting(TransformationWarning::code).contains("IMPORT_INFO_FORWARDING_RULE_RESOLVED");
        MetaModel kept = langium("""
                grammar Fwd
                entry Model: items+=Item*;
                Item: Thing;
                Thing: 'thing' name=ID;
                Other infers Item: 'other' code=ID;
                terminal ID: /[a-z]+/;
                """).metamodel();
        assertThat(names(kept)).contains("Item", "Thing");
        assertThat(generalisations(kept)).contains("Thing->Item");
        assertThat(features(kept, "Item")).containsExactly("code");
        assertThat(containment(kept, "Model", "items")).isEqualTo("Item");
    }

    @Test void fragmentsAreInlinedIntoTheRulesThatCallThem() {
        var output = langium("""
                grammar Frag
                entry Model: defs+=Def*;
                Def: 'def' name=ID Block;
                fragment Block: '{' body+=Stmt* '}';
                Stmt: 'stmt' text=ID;
                terminal ID: /[a-z]+/;
                """);
        assertThat(features(output.metamodel(), "Def")).containsExactlyInAnyOrder("name", "body");
        assertThat(containment(output.metamodel(), "Def", "body")).isEqualTo("Stmt");
        assertThat(output.warnings()).extracting(TransformationWarning::code).doesNotContain("IMPORT_GRAMMAR_RULE_NOT_IMPORTED");
    }

    @Test void terminalReturnTypesTypeTheFeaturesAndEqualTerminalsAreOneFeature() {
        MetaModel mm = langium("""
                grammar Colors
                entry Color: 'color' (color=ID | color=HEX) size=SIZE;
                terminal HEX returns string: /#[0-9a-f]+/;
                terminal SIZE returns number: /[0-9]+/;
                terminal ID: /[a-z]+/;
                """).metamodel();
        MetaClass color = mm.classes().getFirst();
        assertThat(color.attributes()).extracting(a -> a.name()).containsExactlyInAnyOrder("color", "size");
        assertThat(color.attributes()).filteredOn(a -> a.name().equals("color")).singleElement().satisfies(a -> assertThat(a.type().targetId()).isEqualTo(MetaPrimitiveType.STRING.name()));
        assertThat(color.attributes()).filteredOn(a -> a.name().equals("size")).singleElement().satisfies(a -> assertThat(a.type().targetId()).isEqualTo(MetaPrimitiveType.DOUBLE.name()));
    }

    @Test void whatHydraGeneratesIsImportedExactlyAsBefore() {
        var output = langium("""
                grammar Company
                import "metamodel"
                entry Model:
                    elements+=Element*;
                Element:
                    Company | Person;
                Company returns model::Company:
                    'company' name=ID '{' 'employees' employees+=Person* '}';
                Person:
                    {infer Person} 'person' '{' '}';
                Holder returns Holder:
                    'holder' '{' name=ID '}';
                terminal ID: /[_a-zA-Z][\\w_]*/;
                hidden terminal WS: /\\s+/;
                """);
        assertThat(names(output.metamodel())).containsExactly("Model", "Element", "Company", "Person", "Holder");
        assertThat(generalisations(output.metamodel())).containsExactlyInAnyOrder("Company->Element", "Person->Element");
        assertThat(output.warnings()).extracting(TransformationWarning::code)
                .doesNotContain("IMPORT_GRAMMAR_RETURN_TYPE_NOT_IMPORTED", "IMPORT_GRAMMAR_ALTERNATIVE_ACTIONS_MERGED", "IMPORT_GRAMMAR_RULE_NOT_IMPORTED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"arithmetics.langium", "domain-model.langium", "statemachine.langium", "minilogo.langium", "Arithmetics.xtext"})
    void realGrammarsRecoverTheTypesTheirLanguageInfers(String file) throws Exception {
        MetaModel mm = (file.endsWith(".xtext") ? xtext(resource(file)) : langium(resource(file))).metamodel();
        Map<String, Set<String>> expected = oracle(file);
        Map<String, Set<String>> observed = view(mm);
        for(String aspect : List.of("classes", "generalisations", "features", "crossReferences", "containments")) {
            Set<String> lost = new TreeSet<>(expected.get(aspect));
            lost.removeAll(observed.get(aspect));
            assertThat(lost).as(file + " " + aspect + " lost").isEmpty();
        }
        if(file.endsWith(".langium")) {
            Set<String> extra = new TreeSet<>(observed.get("classes"));
            extra.removeAll(expected.get("classes"));
            assertThat(extra).as(file + " classes that Langium does not infer").isEmpty();
        }
    }

    @Test void domainmodelXtextOnlyMissesTheFeaturesXtextLiftsToTheCommonSupertype() throws Exception {
        Map<String, Set<String>> expected = oracle("Domainmodel.xtext");
        Map<String, Set<String>> observed = view(xtext(resource("Domainmodel.xtext")).metamodel());
        for(String aspect : List.of("classes", "generalisations", "containments")) assertThat(observed.get(aspect)).as(aspect).containsAll(expected.get(aspect));
        Set<String> lost = new TreeSet<>(expected.get("features"));
        lost.removeAll(observed.get("features"));
        assertThat(lost).containsExactly("AbstractElement.name", "Feature.name", "Feature.type");
    }

    private static MetamodelImportOutput langium(String grammar) {
        return new LangiumMetamodelImporter().importMetamodel(new MetamodelImportSource("g.langium", "text/plain", grammar.getBytes(StandardCharsets.UTF_8)), MetamodelImportOptions.defaults());
    }

    private static MetamodelImportOutput xtext(String grammar) {
        return new XtextMetamodelImporter().importMetamodel(new MetamodelImportSource("g.xtext", "text/plain", grammar.getBytes(StandardCharsets.UTF_8)), MetamodelImportOptions.defaults());
    }

    private static String resource(String file) throws Exception {
        try(InputStream in = GrammarTypeInferenceImportTests.class.getResourceAsStream("/grammar-real/" + file)) { return new String(in.readAllBytes(), StandardCharsets.UTF_8); }
    }

    private static Map<String, Set<String>> oracle(String file) throws Exception {
        JsonNode root;
        try(InputStream in = GrammarTypeInferenceImportTests.class.getResourceAsStream("/grammar-real/oracles/" + file + ".json")) { root = JSON.readTree(in); }
        Map<String, Set<String>> result = new java.util.LinkedHashMap<>();
        for(String aspect : List.of("classes", "generalisations", "features", "crossReferences", "containments")) {
            Set<String> facts = new TreeSet<>();
            for(JsonNode fact : root.get(aspect)) {
                if(fact.isString()) { facts.add(fact.asString()); continue; }
                List<String> parts = new ArrayList<>();
                fact.forEach(part -> parts.add(part.asString()));
                facts.add(aspect.equals("generalisations") ? parts.get(0) + "->" + parts.get(1) : String.join(".", parts));
            }
            result.put(aspect, facts);
        }
        return result;
    }

    private static Map<String, Set<String>> view(MetaModel mm) {
        Map<String, String> names = mm.classes().stream().collect(Collectors.toMap(MetaClass::id, MetaClass::name));
        Set<String> gens = new TreeSet<>(), features = new TreeSet<>(), cross = new TreeSet<>(), cont = new TreeSet<>();
        mm.classes().forEach(c -> c.attributes().forEach(a -> features.add(c.name() + "." + a.name())));
        for(MetaRelation r : mm.relations()) {
            String s = names.getOrDefault(r.sourceTypeId(), r.sourceTypeId()), t = names.getOrDefault(r.targetTypeId(), r.targetTypeId());
            if(r.kind() == MetaRelationKind.INHERITANCE || r.kind() == MetaRelationKind.IMPLEMENTATION) { gens.add(s + "->" + t); continue; }
            features.add(s + "." + r.name());
            if(r.kind() == MetaRelationKind.CONTAINMENT) cont.add(s + "." + r.name()); else cross.add(s + "." + r.name() + "." + t);
        }
        return Map.of("classes", new TreeSet<>(names.values()), "generalisations", gens, "features", features, "crossReferences", cross, "containments", cont);
    }

    private static List<String> names(MetaModel mm) { return mm.classes().stream().map(MetaClass::name).toList(); }

    private static List<String> features(MetaModel mm, String className) {
        MetaClass owner = mm.classes().stream().filter(c -> c.name().equals(className)).findFirst().orElseThrow(() -> new AssertionError("no class " + className + " in " + names(mm)));
        List<String> result = new ArrayList<>(owner.attributes().stream().map(a -> a.name()).toList());
        mm.relations().stream().filter(r -> r.sourceTypeId().equals(owner.id()) && r.kind() != MetaRelationKind.INHERITANCE && r.kind() != MetaRelationKind.IMPLEMENTATION)
                .forEach(r -> result.add(r.name()));
        return result;
    }

    private static Set<String> generalisations(MetaModel mm) { return view(mm).get("generalisations"); }

    private static String containment(MetaModel mm, String className, String feature) {
        Map<String, String> byId = mm.classes().stream().collect(Collectors.toMap(MetaClass::id, MetaClass::name));
        String owner = mm.classes().stream().filter(c -> c.name().equals(className)).findFirst().orElseThrow().id();
        return mm.relations().stream().filter(r -> r.sourceTypeId().equals(owner) && feature.equals(r.name()) && r.kind() == MetaRelationKind.CONTAINMENT)
                .map(r -> byId.get(r.targetTypeId())).findFirst().orElse("<none>");
    }

    private static List<String> enumLiteralValues(MetaModel mm, String className, String feature) {
        MetaClass owner = mm.classes().stream().filter(c -> c.name().equals(className)).findFirst().orElseThrow();
        String enumId = owner.attributes().stream().filter(a -> a.name().equals(feature)).findFirst().orElseThrow().type().targetId();
        Function<MetaEnumLiteral, String> serialized = literal -> literal.annotations().getOrDefault("serializedValue", literal.name());
        return mm.enums().stream().filter(e -> e.id().equals(enumId)).findFirst().map(MetaEnum::literals).orElseThrow().stream().map(serialized).toList();
    }
}
