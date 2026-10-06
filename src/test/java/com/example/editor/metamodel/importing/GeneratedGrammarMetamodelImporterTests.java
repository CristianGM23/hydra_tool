package com.example.editor.metamodel.importing;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.importing.importers.AntlrMetamodelImporter;
import com.example.editor.metamodel.importing.importers.LangiumMetamodelImporter;
import com.example.editor.metamodel.importing.importers.XtextMetamodelImporter;
import com.example.editor.metamodel.importing.canonicalization.ImportedMetamodelCanonicalizer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba las gramáticas generadas por la herramienta.
 */
class GeneratedGrammarMetamodelImporterTests {

    @Test
    void importsLangiumClassesAttributesEnumsAndContainment() {
        String grammar = """
                grammar Company

                entry Model:
                    elements+=Element*;

                Element:
                    Company | Person;

                Company:
                    'company' name=ID '{'
                    'employees' employees+=Person
                    '}';

                Person:
                    'person' name=ID '{'
                    'age' age=INT
                    'state' state=State
                    '}';

                State returns string:
                    'ACTIVE' | 'INACTIVE';
                """;
        var output = new LangiumMetamodelImporter().importMetamodel(source("company.langium", grammar), MetamodelImportOptions.defaults());
        MetaClass root = findClass(output, "Model");
        MetaClass element = findClass(output, "Element");
        MetaClass company = findClass(output, "Company");
        MetaClass person = findClass(output, "Person");

        assertThat(output.metamodel().classes()).extracting(MetaClass::name).containsExactlyInAnyOrder("Model", "Element", "Company", "Person");
        assertThat(root.kind()).isEqualTo(MetaClassKind.CLASS);
        assertThat(root.annotations()).containsEntry("root", "true").containsEntry("entryRule", "true");
        assertThat(element.kind()).isEqualTo(MetaClassKind.ABSTRACT_CLASS);
        assertThat(output.metamodel().annotations()).containsEntry("entryRule", "Model").containsEntry("rootClassId", root.id());

        assertThat(findRelation(output, "elements", MetaRelationKind.CONTAINMENT)).satisfies(relation -> {
            assertThat(relation.sourceTypeId()).isEqualTo(root.id());
            assertThat(relation.targetTypeId()).isEqualTo(element.id());
            assertThat(relation.targetMultiplicity().lowerBound()).isZero();
            assertThat(relation.targetMultiplicity().upperBound()).isNull();
        });
        assertThat(findRelation(output, "employees", MetaRelationKind.CONTAINMENT)).satisfies(relation -> {
            assertThat(relation.sourceTypeId()).isEqualTo(company.id());
            assertThat(relation.targetTypeId()).isEqualTo(person.id());
        });
        assertThat(output.metamodel().relations().stream()
                .filter(item -> item.kind() == MetaRelationKind.INHERITANCE)
                .map(item -> item.sourceTypeId() + "->" + item.targetTypeId()))
                .containsExactlyInAnyOrder(company.id() + "->" + element.id(), person.id() + "->" + element.id());
        assertThat(output.metamodel().enums()).singleElement().satisfies(item -> {
            assertThat(item.name()).isEqualTo("State");
            assertThat(item.literals()).extracting(literal -> literal.name()).containsExactly("ACTIVE", "INACTIVE");
        });
        assertThat(person.attributes()).extracting(attribute -> attribute.name()).containsExactlyInAnyOrder("name", "age", "state");
        assertThat(output.warnings()).extracting(item -> item.code()).contains("IMPORT_ABSTRACT_RULE_INFERRED");
    }

    @Test
    void importsLangiumAbstractAlternativesAsInheritance() {
        String grammar = """
                grammar Shapes
                entry Model: elements+=Element*;
                Element: Circle;
                Shape: Circle | Square;
                Circle: 'circle' name=ID '{' '}';
                Square: 'square' name=ID '{' '}';
                """;
        var output = new LangiumMetamodelImporter().importMetamodel(source("shapes.langium", grammar), MetamodelImportOptions.defaults());
        assertThat(output.metamodel().classes()).extracting(item -> item.name()).contains("Shape", "Circle", "Square");
        assertThat(output.metamodel().relations().stream().filter(item -> item.kind() == MetaRelationKind.INHERITANCE)).hasSize(2);
        assertThat(output.warnings()).extracting(item -> item.code())
                .contains("IMPORT_INFO_FORWARDING_RULE_RESOLVED", "IMPORT_ABSTRACT_RULE_INFERRED")
                .doesNotContain("IMPORT_LOSSY_ABSTRACT_OR_INTERFACE");
    }

    @Test
    void importsXtextNamespaceAndReference() {
        String grammar = """
                grammar generated.Company with org.eclipse.xtext.common.Terminals
                import "urn:company" as model

                Model: elements+=Element*;
                Element: Company | Person;
                Company returns model::Company:
                    'company' name=ID '{'
                    'owner' owner=[model::Person|ID]
                    '}';
                Person returns model::Person:
                    'person' name=ID '{' '}';
                """;
        var output = new XtextMetamodelImporter().importMetamodel(source("company.xtext", grammar), MetamodelImportOptions.defaults());
        MetaClass root = findClass(output, "Model");
        MetaClass element = findClass(output, "Element");
        MetaClass company = findClass(output, "Company");
        MetaClass person = findClass(output, "Person");
        MetaRelation owner = findRelation(output, "owner", MetaRelationKind.REFERENCE);

        assertThat(ImportedMetamodelCanonicalizer.prepare(output.metamodel()).targetConfiguration().ecore().namespaceUri()).isEqualTo("urn:company");
        assertThat(output.metamodel().classes()).extracting(MetaClass::name).containsExactlyInAnyOrder("Model", "Element", "Company", "Person");
        assertThat(root.annotations()).containsEntry("root", "true");
        assertThat(element.kind()).isEqualTo(MetaClassKind.ABSTRACT_CLASS);
        assertThat(findRelation(output, "elements", MetaRelationKind.CONTAINMENT)).satisfies(relation -> {
            assertThat(relation.sourceTypeId()).isEqualTo(root.id());
            assertThat(relation.targetTypeId()).isEqualTo(element.id());
        });
        assertThat(owner.sourceTypeId()).isEqualTo(company.id());
        assertThat(owner.targetTypeId()).isEqualTo(person.id());
        assertThat(ImportedMetamodelCanonicalizer.prepare(output.metamodel()).targetConfiguration().ecore().relation(owner.id(), false).resolveProxies()).isTrue();
        assertThat(owner.annotations()).containsEntry("referenceSyntax", "xtext-pipe").containsEntry("referenceTerminal", "ID").containsEntry("scopeKeyFeature", "name");
        assertThat(output.metamodel().relations().stream()
                .filter(item -> item.kind() == MetaRelationKind.INHERITANCE)
                .map(item -> item.sourceTypeId() + "->" + item.targetTypeId()))
                .containsExactlyInAnyOrder(company.id() + "->" + element.id(), person.id() + "->" + element.id());
        assertThat(output.warnings()).extracting(item -> item.code()).contains("IMPORT_INFO_CROSS_REFERENCE_DETECTED", "IMPORT_SCOPE_EXTERNAL_CONFIGURATION");
    }

    @Test
    void importsAntlrClassesEnumsAndMultiplicity() {
        String grammar = """
                grammar Company;
                model: element* EOF;
                element: company | person;
                company: 'company' ID '{' ('employees' person)* '}';
                person: 'person' ID '{' ('age' INT)? ('state' state) '}';
                state: 'ACTIVE' | 'INACTIVE';
                ID: [a-zA-Z_] [a-zA-Z_0-9]*;
                INT: [0-9]+;
                """;
        var output = new AntlrMetamodelImporter().importMetamodel(source("company.g4", grammar), MetamodelImportOptions.defaults());
        assertThat(output.metamodel().classes()).extracting(item -> item.name()).containsExactlyInAnyOrder("Company", "Person");
        assertThat(output.metamodel().relations()).singleElement().satisfies(item -> {
            assertThat(item.kind()).isEqualTo(MetaRelationKind.CONTAINMENT);
            assertThat(item.targetMultiplicity().upperBound()).isNull();
        });
        assertThat(output.metamodel().enums()).singleElement().extracting(item -> item.name()).isEqualTo("State");
    }

    @Test
    void reportsAntlrReferenceTargetLoss() {
        String grammar = """
                grammar Company;
                model: element* EOF;
                element: company;
                company: 'company' ID '{' ('owner' ID)? '}';
                ID: [a-zA-Z_] [a-zA-Z_0-9]*;
                """;
        var output = new AntlrMetamodelImporter().importMetamodel(source("company.g4", grammar), MetamodelImportOptions.defaults());
        assertThat(output.warnings()).extracting(item -> item.code()).contains("IMPORT_LOSSY_ANTLR_REFERENCE_TARGET");
    }

    private MetaClass findClass(MetamodelImportOutput output, String name) {
        return output.metamodel().classes().stream().filter(item -> item.name().equals(name)).findFirst().orElseThrow();
    }

    private MetaRelation findRelation(MetamodelImportOutput output, String name, MetaRelationKind kind) {
        return output.metamodel().relations().stream().filter(item -> item.name().equals(name) && item.kind() == kind).findFirst().orElseThrow();
    }

    private MetamodelImportSource source(String fileName, String content) {
        return new MetamodelImportSource(fileName, "text/plain", content.getBytes(StandardCharsets.UTF_8));
    }
}
