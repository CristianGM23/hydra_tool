package com.example.editor.metamodel.importing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.TransformationWarning;
import com.example.editor.metamodel.importing.importers.LangiumMetamodelImporter;
import com.example.editor.metamodel.importing.importers.XtextMetamodelImporter;

/**
 * Two defects of the existing grammar import found with real third-party grammars (battery scenario INT-REAL: Langium's arithmetics
 * example and Xtext's domain-model example):
 * <ul>
 * <li>a rule whose header the importer does not understand (Langium {@code X infers Y:}, {@code infix X on Y:}, {@code fragment X:}) was
 * dropped with no warning at all;</li>
 * <li>a feature assigned in several places of one rule ({@code args+=P (',' args+=P)*}) is one feature in Xtext and Langium, but was split
 * into {@code args} and {@code args2}.</li>
 * </ul>
 * Supporting the constructs themselves is outside the current contract; what is required here is that nothing is lost silently.
 */
class GrammarRuleCoverageImportTests {

    private static final String ARITHMETICS = """
            grammar Arithmetics

            entry Module:
                'module' name=ID
                (statements+=Statement)*;

            Statement:
                Definition | Evaluation;

            Definition:
                'def' name=ID ('(' args+=DeclaredParameter (',' args+=DeclaredParameter)* ')')?
                ':' expr=Expression ';';

            DeclaredParameter:
                name=ID;

            fragment Named:
                label=ID;

            Evaluation:
                expression=Expression ';';

            Expression:
                BinaryExpression;

            infix BinaryExpression on PrimaryExpression:
                '%'
                > '*' | '/'
                > '+' | '-';

            PrimaryExpression infers Expression:
                '(' Expression ')' |
                {infer NumberLiteral} value=NUMBER;

            hidden terminal WS: /\\s+/;
            terminal ID: /[_a-zA-Z][\\w_]*/;
            terminal NUMBER returns number: /[0-9]+(\\.[0-9]*)?/;
            """;

    @Test
    void ruleDeclarationsTheImporterDoesNotSupportAreReportedNeverDroppedSilently() {
        List<TransformationWarning> warnings = new LangiumMetamodelImporter().importMetamodel(source("arithmetics.langium", ARITHMETICS), MetamodelImportOptions.defaults()).warnings();

        assertThat(warnings).filteredOn(w -> w.code().equals("IMPORT_GRAMMAR_RULE_NOT_IMPORTED")).extracting(TransformationWarning::message)
                .hasSize(3)
                .anySatisfy(m -> assertThat(m).contains("infix BinaryExpression on PrimaryExpression"))
                .anySatisfy(m -> assertThat(m).contains("PrimaryExpression infers Expression"))
                .anySatisfy(m -> assertThat(m).contains("fragment Named"));
    }

    @Test
    void grammarsGeneratedByHydraAndTerminalsProduceNoSuchWarning() {
        String generated = """
                grammar Company

                entry Model:
                    elements+=Element*;

                Element:
                    Company | Person;

                Company returns model::Company:
                    'company' name=ID '{' 'employees' employees+=Person* '}';

                Person:
                    {infer Person} 'person' '{' '}';

                terminal ID: /[_a-zA-Z][\\w_]*/;
                terminal INT returns number: /[0-9]+/;
                hidden terminal WS: /\\s+/;
                """;
        assertThat(new LangiumMetamodelImporter().importMetamodel(source("company.langium", generated), MetamodelImportOptions.defaults()).warnings())
                .extracting(TransformationWarning::code).doesNotContain("IMPORT_GRAMMAR_RULE_NOT_IMPORTED");
    }

    @Test
    void aFeatureAssignedSeveralTimesInOneRuleIsOneFeature() {
        var langium = new LangiumMetamodelImporter().importMetamodel(source("arithmetics.langium", ARITHMETICS), MetamodelImportOptions.defaults());
        List<MetaRelation> args = relations(langium, "Definition");
        assertThat(args).extracting(MetaRelation::name).containsExactly("args", "expr");
        assertThat(args.getFirst().targetMultiplicity().lowerBound()).isZero();
        assertThat(args.getFirst().targetMultiplicity().upperBound()).isNull();
        assertThat(langium.warnings()).filteredOn(w -> w.code().equals("IMPORT_FEATURE_NAME_CONFLICT_AUTO_FIXED")).isEmpty();

        String xtext = """
                grammar org.example.Ops with org.eclipse.xtext.common.Terminals
                generate ops "http://www.example.org/ops"
                Model: operations+=Operation*;
                Operation: 'op' name=ID '(' (params+=Parameter (',' params+=Parameter)*)? ')';
                Parameter: name=ID;
                """;
        var output = new XtextMetamodelImporter().importMetamodel(source("ops.xtext", xtext), MetamodelImportOptions.defaults());
        assertThat(relations(output, "Operation")).extracting(MetaRelation::name).containsExactly("params");
    }

    @Test
    void theSameNameWithAnotherTypeIsStillAConflict() {
        String grammar = """
                grammar Values
                entry Model: values+=Value*;
                Value: 'text' content=STRING | 'number' content=INT;
                terminal ID: /[_a-zA-Z][\\w_]*/;
                terminal INT returns number: /[0-9]+/;
                terminal STRING: /"[^"]*"/;
                """;
        var output = new LangiumMetamodelImporter().importMetamodel(source("values.langium", grammar), MetamodelImportOptions.defaults());
        MetaClass value = output.metamodel().classes().stream().filter(c -> c.name().equals("Value")).findFirst().orElseThrow();
        assertThat(value.attributes()).extracting(a -> a.name()).containsExactly("content", "content2");
        assertThat(output.warnings()).extracting(TransformationWarning::code).contains("IMPORT_FEATURE_NAME_CONFLICT_AUTO_FIXED");
    }

    private static List<MetaRelation> relations(MetamodelImportOutput output, String className) {
        String id = output.metamodel().classes().stream().filter(c -> c.name().equals(className)).findFirst().orElseThrow().id();
        return output.metamodel().relations().stream().filter(r -> r.sourceTypeId().equals(id) && !r.name().isBlank()).toList();
    }

    private static MetamodelImportSource source(String name, String content) {
        return new MetamodelImportSource(name, "text/plain", content.getBytes(StandardCharsets.UTF_8));
    }
}
