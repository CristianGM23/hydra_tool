package com.example.editor.metamodel.importing;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaOperation;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReferenceKind;
import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.importing.importers.EmfaticMetamodelImporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmfaticMetamodelImporterTests {

    private final EmfaticMetamodelImporter importer = new EmfaticMetamodelImporter();

    @Test
    void importsMainPackageAndConcreteClass() {
        var output = importText("package library; class Book { }");
        assertThat(output.metamodel().id()).isEqualTo("library");
        assertThat(output.metamodel().name()).isEqualTo("Library");
        assertThat(output.metamodel().classes()).singleElement().satisfies(item -> {
            assertThat(item.name()).isEqualTo("Book");
            assertThat(item.kind()).isEqualTo(MetaClassKind.CLASS);
        });
    }

    @Test
    void preservesNamespaceUriAndPrefixForEcoreProjection() {
        var output = importText("@namespace(uri=\"urn:library\", prefix=\"lib\") package library; class Book { }");
        assertThat(output.metamodel().annotations())
                .containsEntry("import:ecore:namespaceUri", "urn:library")
                .containsEntry("import:ecore:namespacePrefix", "lib")
                .containsEntry("import:ecore:artifactName", "sample.ecore");
    }

    @Test
    void importsAbstractClassesInterfacesAndTheirHierarchy() {
        var output = importText("""
                package shapes;
                interface Named { op String getName(); }
                abstract class Shape extends Named { }
                class Circle extends Shape { }
                """);
        assertThat(findClass(output, "Named").kind()).isEqualTo(MetaClassKind.INTERFACE);
        assertThat(findClass(output, "Shape").kind()).isEqualTo(MetaClassKind.ABSTRACT_CLASS);
        assertThat(output.metamodel().relations()).extracting(MetaRelation::kind).contains(MetaRelationKind.IMPLEMENTATION, MetaRelationKind.INHERITANCE);
    }

    @Test
    void importsPrimitiveAttributesMultiplicityDefaultsAndModifiers() {
        var output = importText("""
                package people;
                class Person {
                  id attr String[1] name = unknown;
                  !ordered !unique attr int[*] scores;
                }
                """);
        MetaClass person = findClass(output, "Person");
        assertThat(person.attributes()).hasSize(2);
        assertThat(person.attributes().get(0).type().targetId()).isEqualTo(MetaPrimitiveType.STRING.name());
        assertThat(person.attributes().get(0).multiplicity().lowerBound()).isEqualTo(1);
        assertThat(person.attributes().get(0).defaultValue()).isEqualTo("unknown");
        assertThat(person.attributes().get(0).annotations()).containsEntry("emfatic:id", "true");
        assertThat(person.attributes().get(1).multiplicity().upperBound()).isNull();
        assertThat(person.attributes().get(1).ordered()).isFalse();
        assertThat(person.attributes().get(1).unique()).isFalse();
    }

    @Test
    void importsContainmentReferencesAndResolvesOpposites() {
        var output = importText("""
                package families;
                class Parent { val Child[*]#parent children; }
                class Child { ref Parent#children parent; }
                """);
        MetaRelation children = findRelation(output, "children");
        MetaRelation parent = findRelation(output, "parent");
        assertThat(children.kind()).isEqualTo(MetaRelationKind.CONTAINMENT);
        assertThat(children.targetMultiplicity().upperBound()).isNull();
        assertThat(children.oppositeRelationId()).isEqualTo(parent.id());
        assertThat(parent.oppositeRelationId()).isEqualTo(children.id());
    }

    @Test
    void importsOperationsParametersReturnTypesAndExceptions() {
        var output = importText("""
                package operations;
                class Calculator {
                  op int add(int[1] first, int[1] second) throws CalculationException;
                  op void clear();
                }
                """);
        MetaClass calculator = findClass(output, "Calculator");
        MetaOperation add = calculator.operations().get(0);
        assertThat(add.name()).isEqualTo("add");
        assertThat(add.parameters()).extracting(item -> item.name()).containsExactly("first", "second");
        assertThat(add.returnType().targetId()).isEqualTo(MetaPrimitiveType.INTEGER.name());
        assertThat(add.exceptionTypes()).singleElement().satisfies(item -> assertThat(item.kind()).isEqualTo(MetaTypeReferenceKind.EXTERNAL));
        assertThat(calculator.operations().get(1).returnsVoid()).isTrue();
    }

    @Test
    void importsEnumsWithExplicitAndImplicitValues() {
        var output = importText("package state; enum Status { NEW; ACTIVE = 5; CLOSED; }");
        assertThat(output.metamodel().enums()).singleElement().satisfies(item -> {
            assertThat(item.name()).isEqualTo("Status");
            assertThat(item.literals()).extracting(literal -> literal.value()).containsExactly(0, 5, 6);
        });
    }

    @Test
    void importsSerializableAndTransientDataTypes() {
        var output = importText("""
                package values;
                datatype Money : java.math.BigDecimal;
                transient datatype Payload : "byte[]";
                """);
        assertThat(output.metamodel().dataTypes()).hasSize(2);
        assertThat(output.metamodel().dataTypes().get(0).annotations()).containsEntry("emfatic:instanceClassName", "java.math.BigDecimal").containsEntry("emfatic:serializable", "true");
        assertThat(output.metamodel().dataTypes().get(1).annotations()).containsEntry("emfatic:instanceClassName", "byte[]").containsEntry("emfatic:serializable", "false");
    }

    @Test
    void importsNestedPackagesAndResolvesQualifiedTypes() {
        var output = importText("""
                package root;
                package domain { datatype Code : java.lang.String; }
                class Item { attr domain.Code code; }
                """);
        assertThat(findClass(output, "Item").attributes()).singleElement().satisfies(item -> {
            assertThat(item.type().kind()).isEqualTo(MetaTypeReferenceKind.DATATYPE);
            assertThat(item.type().targetId()).isEqualTo("code");
        });
    }

    @Test
    void importsMapEntriesAsPortableNeutralClasses() {
        var output = importText("package maps; mapentry StringToString : String -> String;");
        assertThat(output.metamodel().classes()).singleElement().satisfies(item -> {
            assertThat(item.annotations()).containsEntry("emfatic:mapEntry", "true");
            assertThat(item.attributes()).extracting(attribute -> attribute.name()).containsExactly("key", "value");
        });
    }

    @Test
    void ignoresCommentsAndUnescapesKeywordIdentifiers() {
        var output = importText("""
                package escaped;
                // line comment
                class Entry {
                  /* block comment */ attr boolean ~abstract;
                }
                """);
        assertThat(findClass(output, "Entry").attributes()).singleElement().extracting(item -> item.name()).isEqualTo("abstract");
    }

    @Test
    void preservesExternalImportsAndCreatesPlaceholderReferenceTargets() {
        var output = importText("""
                package external;
                import "platform:/resource/other/model.ecore";
                class Holder { ref other.External target; }
                """);
        assertThat(output.metamodel().annotations()).containsEntry("emfatic:import:0", "platform:/resource/other/model.ecore");
        assertThat(output.metamodel().classes()).anySatisfy(item -> assertThat(item.annotations()).containsEntry("external", "true"));
        assertThat(output.warnings()).extracting(item -> item.code()).contains("IMPORT_UNRESOLVED_EMFATIC_CLASS");
    }

    @Test
    void rejectsUnterminatedCommentsWithAControlledDesignException() {
        assertThatThrownBy(() -> importText("package invalid; /* not closed"))
                .isInstanceOf(MetamodelDesignException.class)
                .hasMessageContaining("Unterminated block comment");
    }

    @Test
    void rejectsMalformedFeaturesWithoutLeakingParserInternals() {
        assertThatThrownBy(() -> importText("package invalid; class Item { attr ; }"))
                .isInstanceOf(MetamodelDesignException.class)
                .hasMessageContaining("Could not parse EMF/Emfatic file");
    }

    @Test
    void normalizesUnspecifiedUpperBoundsAndReportsTheLoss() {
        var output = importText("package bounds; class Item { attr String[1..?] values; }");
        assertThat(findClass(output, "Item").attributes()).singleElement().satisfies(item -> {
            assertThat(item.multiplicity().lowerBound()).isEqualTo(1);
            assertThat(item.multiplicity().upperBound()).isNull();
        });
        assertThat(output.warnings()).extracting(item -> item.code()).contains("IMPORT_LOSSY_EMFATIC_UNSPECIFIED_UPPER_BOUND");
    }

    private MetamodelImportOutput importText(String text) {
        return importer.importMetamodel(new MetamodelImportSource("sample.emf", "text/plain", text.getBytes(StandardCharsets.UTF_8)), MetamodelImportOptions.defaults());
    }

    private MetaClass findClass(MetamodelImportOutput output, String name) {
        return output.metamodel().classes().stream().filter(item -> item.name().equals(name)).findFirst().orElseThrow();
    }

    private MetaRelation findRelation(MetamodelImportOutput output, String name) {
        return output.metamodel().relations().stream().filter(item -> item.name().equals(name)).findFirst().orElseThrow();
    }
}
