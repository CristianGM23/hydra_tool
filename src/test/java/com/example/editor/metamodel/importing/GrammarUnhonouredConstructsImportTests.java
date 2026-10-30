package com.example.editor.metamodel.importing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.TransformationWarning;
import com.example.editor.metamodel.importing.importers.LangiumMetamodelImporter;
import com.example.editor.metamodel.importing.importers.XtextMetamodelImporter;

/**
 * FIND-GR-IMPORT-SILENT-RETURNS-TYPE and FIND-GR-IMPORT-SILENT-ALTERNATIVE-ACTIONS. Jar 13 only reported the loss (a rule with an unqualified
 * «returns T» was imported as a class named after the rule; the actions inside its alternatives were merged into that class), because recovering
 * T and the alternative subtypes (proposal D-2/D-3) was not authorised yet. Since the authorisation of 30/10 the constructs are represented, so
 * the same inputs now check the represented types and the absence of the loss warnings; an action form the importer still does not read keeps
 * being reported (TextualGrammarImportParser.reportUnrepresentedConstructs).
 */
class GrammarUnhonouredConstructsImportTests {

    @Test
    void anUnqualifiedReturnTypeIsTheClassOfEveryRuleThatReturnsIt() {
        String grammar = """
                grammar Items
                entry Model: items+=Item* others+=Other*;
                Item returns Element: 'item' name=ID;
                Other returns Element: 'other' name=ID code=ID;
                terminal ID: /[a-z]+/;
                """;
        var output = new LangiumMetamodelImporter().importMetamodel(source("items.langium", grammar), MetamodelImportOptions.defaults());
        assertThat(codes(output.warnings(), "IMPORT_GRAMMAR_RETURN_TYPE_NOT_IMPORTED")).isEmpty();
        assertThat(output.metamodel().classes()).extracting(MetaClass::name).containsExactly("Model", "Element");
    }

    @Test
    void aQualifiedReturnTypeWithAnotherNameNamesTheClass() {
        String grammar = """
                grammar Items
                import "metamodel"
                entry Model: items+=Foo*;
                Foo returns model::Bar: 'foo' name=ID;
                terminal ID: /[a-z]+/;
                """;
        var output = new LangiumMetamodelImporter().importMetamodel(source("items.langium", grammar), MetamodelImportOptions.defaults());
        assertThat(codes(output.warnings(), "IMPORT_GRAMMAR_RETURN_TYPE_NOT_IMPORTED")).isEmpty();
        assertThat(output.metamodel().classes()).extracting(MetaClass::name).containsExactly("Model", "Bar");
    }

    @Test
    void actionsInsideAlternativesCreateTheirSubtypes() {
        String grammar = """
                grammar Values
                entry Model: items+=Primary*;
                Primary: '(' inner=Primary ')' | {infer NumberLiteral} value=INT | {infer Reference} name=ID;
                terminal INT returns number: /[0-9]+/;
                terminal ID: /[a-z]+/;
                """;
        var output = new LangiumMetamodelImporter().importMetamodel(source("values.langium", grammar), MetamodelImportOptions.defaults());
        assertThat(codes(output.warnings(), "IMPORT_GRAMMAR_ALTERNATIVE_ACTIONS_MERGED")).isEmpty();
        assertThat(output.metamodel().classes()).extracting(MetaClass::name).containsExactly("Model", "Primary", "NumberLiteral", "Reference");
    }

    @Test
    void xtextRewriteActionsCreateTheBinaryType() {
        String grammar = """
                grammar org.example.Ops with org.eclipse.xtext.common.Terminals
                generate ops "http://example.org/ops"
                Model: items+=Sum*;
                Sum: Atom ({Plus.left=current} '+' right=Atom)*;
                Atom: value=INT;
                """;
        var output = new XtextMetamodelImporter().importMetamodel(source("ops.xtext", grammar), MetamodelImportOptions.defaults());
        assertThat(codes(output.warnings(), "IMPORT_GRAMMAR_ALTERNATIVE_ACTIONS_MERGED")).isEmpty();
        MetaClass plus = output.metamodel().classes().stream().filter(c -> c.name().equals("Plus")).findFirst().orElseThrow();
        assertThat(output.metamodel().relations()).filteredOn(r -> r.sourceTypeId().equals(plus.id()) && r.kind() == MetaRelationKind.CONTAINMENT)
                .extracting(r -> r.name()).containsExactlyInAnyOrder("left", "right");
        assertThat(output.metamodel().relations()).filteredOn(r -> r.sourceTypeId().equals(plus.id()) && r.kind() == MetaRelationKind.INHERITANCE).hasSize(1);
    }

    @Test
    void whatHydraGeneratesAndKeywordBracesRaiseNoSuchWarning() {
        String generated = """
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
                """;
        assertThat(new LangiumMetamodelImporter().importMetamodel(source("company.langium", generated), MetamodelImportOptions.defaults()).warnings())
                .extracting(TransformationWarning::code).doesNotContain("IMPORT_GRAMMAR_RETURN_TYPE_NOT_IMPORTED", "IMPORT_GRAMMAR_ALTERNATIVE_ACTIONS_MERGED");
    }

    private static List<TransformationWarning> codes(List<TransformationWarning> warnings, String code) {
        return warnings.stream().filter(w -> w.code().equals(code)).toList();
    }

    private static MetamodelImportSource source(String name, String content) {
        return new MetamodelImportSource(name, "text/plain", content.getBytes(StandardCharsets.UTF_8));
    }
}
