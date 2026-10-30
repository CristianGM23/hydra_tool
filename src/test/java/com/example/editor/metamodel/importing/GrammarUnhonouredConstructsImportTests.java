package com.example.editor.metamodel.importing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.TransformationWarning;
import com.example.editor.metamodel.importing.importers.LangiumMetamodelImporter;
import com.example.editor.metamodel.importing.importers.XtextMetamodelImporter;

/**
 * FIND-GR-IMPORT-SILENT-RETURNS-TYPE and FIND-GR-IMPORT-SILENT-ALTERNATIVE-ACTIONS (proposal D-2/D-3): the importer accepts a parser rule
 * with an unqualified «returns T» and actions inside its alternatives, but names the class after the rule and merges what every alternative
 * assigns. That loss must be reported, like an unsupported rule header is: this change only adds the warnings, the imported metamodel is the
 * same. Recovering T or the alternative subtypes is a new capability (D-2/D-3) that is not part of the current contract.
 */
class GrammarUnhonouredConstructsImportTests {

    @Test
    void anUnqualifiedReturnTypeThatIsNotImportedIsReported() {
        String grammar = """
                grammar Items
                entry Model: items+=Item* others+=Other*;
                Item returns Element: 'item' name=ID;
                Other returns Element: 'other' name=ID code=ID;
                terminal ID: /[a-z]+/;
                """;
        var output = new LangiumMetamodelImporter().importMetamodel(source("items.langium", grammar), MetamodelImportOptions.defaults());
        List<TransformationWarning> lost = codes(output.warnings(), "IMPORT_GRAMMAR_RETURN_TYPE_NOT_IMPORTED");
        assertThat(lost).hasSize(2);
        assertThat(lost.get(0).message()).contains("Item").contains("Element");
        assertThat(lost.get(1).message()).contains("Other").contains("Element");
        assertThat(output.metamodel().classes()).extracting(MetaClass::name).containsExactly("Model", "Item", "Other");
    }

    @Test
    void aQualifiedReturnTypeWithAnotherNameThatIsNotImportedIsReported() {
        String grammar = """
                grammar Items
                import "metamodel"
                entry Model: items+=Foo*;
                Foo returns model::Bar: 'foo' name=ID;
                terminal ID: /[a-z]+/;
                """;
        var output = new LangiumMetamodelImporter().importMetamodel(source("items.langium", grammar), MetamodelImportOptions.defaults());
        assertThat(codes(output.warnings(), "IMPORT_GRAMMAR_RETURN_TYPE_NOT_IMPORTED")).singleElement()
                .satisfies(w -> assertThat(w.message()).contains("Foo").contains("model::Bar"));
        assertThat(output.metamodel().classes()).extracting(MetaClass::name).containsExactly("Model", "Foo");
    }

    @Test
    void actionsInsideAlternativesThatAreMergedIntoTheRuleAreReported() {
        String grammar = """
                grammar Values
                entry Model: items+=Primary*;
                Primary: '(' inner=Primary ')' | {infer NumberLiteral} value=INT | {infer Reference} name=ID;
                terminal INT returns number: /[0-9]+/;
                terminal ID: /[a-z]+/;
                """;
        var output = new LangiumMetamodelImporter().importMetamodel(source("values.langium", grammar), MetamodelImportOptions.defaults());
        List<TransformationWarning> merged = codes(output.warnings(), "IMPORT_GRAMMAR_ALTERNATIVE_ACTIONS_MERGED");
        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).message()).contains("Primary").contains("NumberLiteral").contains("Reference");
        assertThat(output.metamodel().classes()).extracting(MetaClass::name).containsExactly("Model", "Primary");
    }

    @Test
    void xtextRewriteActionsAreReportedToo() {
        String grammar = """
                grammar org.example.Ops with org.eclipse.xtext.common.Terminals
                generate ops "http://example.org/ops"
                Model: items+=Sum*;
                Sum: Atom ({Plus.left=current} '+' right=Atom)*;
                Atom: value=INT;
                """;
        var output = new XtextMetamodelImporter().importMetamodel(source("ops.xtext", grammar), MetamodelImportOptions.defaults());
        assertThat(codes(output.warnings(), "IMPORT_GRAMMAR_ALTERNATIVE_ACTIONS_MERGED")).singleElement()
                .satisfies(w -> assertThat(w.message()).contains("Sum").contains("Plus"));
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
