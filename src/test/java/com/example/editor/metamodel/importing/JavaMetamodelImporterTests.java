package com.example.editor.metamodel.importing;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.importing.importers.JavaMetamodelImporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavaMetamodelImporterTests {

    private final MetamodelImportProperties properties = new MetamodelImportProperties();
    private final JavaMetamodelImporter importer = new JavaMetamodelImporter(new SafeImportArchiveReader(properties));

    @Test void importsClassesInterfacesRecordsAndEnums() {
        var model = importJava("package demo; class A{} interface B{} record C(String value){} enum D{ONE,TWO}").metamodel();
        assertThat(model.classes()).extracting(item -> item.name()).containsExactly("A", "B", "C");
        assertThat(model.enums()).extracting(item -> item.name()).containsExactly("D");
    }

    @Test void preservesPackagesAndSourceProvenance() {
        var model = importJava("package demo.domain; class Person{}").metamodel();
        assertThat(model.annotations()).containsEntry("import:format", "java").containsEntry("import:packages", "demo.domain");
        assertThat(model.classes()).singleElement().satisfies(item -> assertThat(item.annotations()).containsEntry("java:qualifiedName", "demo.domain.Person"));
    }

    @Test void mapsPrimitiveAndOptionalFields() {
        var person = importJava("import java.util.Optional; class Person { String name; Optional<Integer> age; }").metamodel().classes().getFirst();
        assertThat(person.attributes()).extracting(item -> item.name()).containsExactly("name", "age");
        assertThat(person.attributes().get(1).multiplicity().lowerBound()).isZero();
    }

    @Test void derivesCollectionsAsManyRelations() {
        var output = importJava("import java.util.List; class Person { List<Address> addresses; } class Address{}");
        assertThat(output.metamodel().relations()).singleElement().satisfies(item -> {
            assertThat(item.name()).isEqualTo("addresses");
            assertThat(item.targetMultiplicity().upperBound()).isNull();
        });
    }

    @Test void derivesInheritanceAndImplementation() {
        var output = importJava("interface Named{} abstract class Base{} class Person extends Base implements Named{}");
        assertThat(output.metamodel().classes()).anySatisfy(item -> assertThat(item.kind()).isEqualTo(MetaClassKind.ABSTRACT_CLASS));
        assertThat(output.metamodel().relations()).extracting(item -> item.kind()).contains(MetaRelationKind.INHERITANCE, MetaRelationKind.IMPLEMENTATION);
    }

    @Test void importsMethodsParametersReturnAndExceptions() {
        var person = importJava("class Person { String label(int max) throws Exception { return \"\"; } }").metamodel().classes().getFirst();
        assertThat(person.operations()).singleElement().satisfies(operation -> {
            assertThat(operation.name()).isEqualTo("label");
            assertThat(operation.parameters()).extracting(item -> item.name()).containsExactly("max");
            assertThat(operation.exceptionTypes()).hasSize(1);
        });
    }

    @Test void neverImportsOrExecutesMethodBodies() {
        var output = importJava("class Unsafe { void run(){ System.exit(1); } }");
        assertThat(output.warnings()).extracting(item -> item.code()).contains("IMPORT_JAVA_METHOD_BODY_OMITTED");
        assertThat(output.metamodel().classes().getFirst().operations().getFirst().annotations()).containsEntry("java:bodyImported", "false");
    }

    @Test void skipsStaticFieldsButPreservesInstanceFields() {
        var person = importJava("class Person { static final String TYPE=\"P\"; String name; }").metamodel().classes().getFirst();
        assertThat(person.attributes()).extracting(item -> item.name()).containsExactly("name");
    }

    @Test void importsSeveralSourceRootsFromZip() throws Exception {
        byte[] archive = zip(Map.of("core/src/main/java/demo/A.java", "package demo; public class A { B b; }", "api/src/main/java/demo/B.java", "package demo; public class B {}"));
        var output = importer.importMetamodel(new MetamodelImportSource("project.java.zip", "application/zip", archive), MetamodelImportOptions.defaults());
        assertThat(output.metamodel().classes()).hasSize(2);
        assertThat(output.metamodel().relations()).singleElement().extracting(item -> item.name()).isEqualTo("b");
    }

    @Test void adaptsStructuralInterfacesUsedByFieldsWithoutBreakingRelations() {
        var output = importJava("interface Node { String id(); } class Diagram { Node root; }");
        assertThat(output.metamodel().classes())
                .filteredOn(item -> item.name().equals("Node"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.kind()).isEqualTo(MetaClassKind.ABSTRACT_CLASS);
                    assertThat(item.annotations()).containsEntry("java:declaredInterface", "true");
                });
        assertThat(output.metamodel().relations()).filteredOn(item -> item.name().equals("root")).singleElement();
        assertThat(output.warnings()).extracting(item -> item.code()).contains("IMPORT_JAVA_STRUCTURAL_INTERFACE_ADAPTED");
    }

    @Test void reportsExternalSupertypesWithoutInventingClasses() {
        var output = importJava("class Person extends external.Base {}");
        assertThat(output.warnings()).extracting(item -> item.code()).contains("IMPORT_JAVA_EXTERNAL_SUPERTYPE");
        assertThat(output.metamodel().classes()).singleElement();
    }

    @Test void rejectsInvalidJavaWithControlledError() {
        assertThatThrownBy(() -> importJava("class Broken { void x( }")).isInstanceOf(RuntimeException.class).hasMessageContaining("Java AST parsing failed");
    }

    @Test void rejectsArchiveTraversalPaths() throws Exception {
        byte[] archive = zip(Map.of("../Unsafe.java", "class Unsafe{}"));
        assertThatThrownBy(() -> importer.importMetamodel(new MetamodelImportSource("unsafe.zip", "application/zip", archive), MetamodelImportOptions.defaults()))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("Unsafe import archive path");
    }

    private MetamodelImportOutput importJava(String value) {
        return importer.importMetamodel(new MetamodelImportSource("sample.java", "text/x-java-source", value.getBytes(StandardCharsets.UTF_8)), MetamodelImportOptions.defaults());
    }

    private byte[] zip(Map<String, String> files) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try(ZipOutputStream output = new ZipOutputStream(bytes)) {
            for(var entry : new LinkedHashMap<>(files).entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
