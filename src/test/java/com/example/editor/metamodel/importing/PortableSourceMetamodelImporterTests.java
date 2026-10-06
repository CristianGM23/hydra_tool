package com.example.editor.metamodel.importing;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.importing.importers.GoMetamodelImporter;
import com.example.editor.metamodel.importing.importers.JavaScriptMetamodelImporter;
import com.example.editor.metamodel.importing.importers.TypeScriptMetamodelImporter;
import com.example.editor.metamodel.importing.updatesite.UpdateSitePortableSourceMetamodelDeriver;

import static org.assertj.core.api.Assertions.assertThat;

class PortableSourceMetamodelImporterTests {
    private final SafeImportArchiveReader archiveReader = new SafeImportArchiveReader(new MetamodelImportProperties());
    private final UpdateSitePortableSourceMetamodelDeriver deriver = new UpdateSitePortableSourceMetamodelDeriver();

    @Test
    void importsTypeScriptClassesRelationsAndAttributesWithoutExecution() {
        String source = "export interface Person { name: string; }\nexport class Company { employees: Person[]; active?: boolean; }";
        var output = new TypeScriptMetamodelImporter(archiveReader, deriver).importMetamodel(source("model.ts", source), MetamodelImportOptions.defaults());

        assertThat(output.metamodel().classes()).extracting(MetaClass::name).contains("Person", "Company");
        assertThat(output.metamodel().classes().stream().filter(item -> item.name().equals("Person")).findFirst().orElseThrow().attributes()).extracting(MetaAttribute::name).contains("name");
        assertThat(output.metamodel().relations()).anySatisfy(relation -> {
            assertThat(relation.name()).isEqualTo("employees");
            assertThat(relation.targetMultiplicity().unbounded()).isTrue();
        });
        assertThat(output.warnings()).extracting(item -> item.code()).contains("IMPORT_STATIC_SOURCE_INFERENCE");
    }

    @Test
    void importsGoStructsAndRelationsWithoutExecution() {
        String source = "package model\ntype Person struct {\n Name string\n}\ntype Company struct {\n Employees []Person\n Active bool\n}";
        var output = new GoMetamodelImporter(archiveReader, deriver).importMetamodel(source("model.go", source), MetamodelImportOptions.defaults());

        assertThat(output.metamodel().classes()).extracting(MetaClass::name).contains("Person", "Company");
        assertThat(output.metamodel().relations()).anySatisfy(relation -> assertThat(relation.name()).isEqualTo("employees"));
        assertThat(output.metamodel().classes().stream().filter(item -> item.name().equals("Company")).findFirst().orElseThrow().attributes()).extracting(MetaAttribute::name).contains("active");
    }

    @Test
    void importsGenericJavaScriptSeparatelyFromBlockly() {
        String source = "export class Person { constructor() { this.name = ''; this.active = true; } greet(prefix) { return prefix + this.name; } }";
        var output = new JavaScriptMetamodelImporter(archiveReader, deriver).importMetamodel(source("model.js", source), MetamodelImportOptions.defaults());

        MetaClass person = output.metamodel().classes().stream().filter(item -> item.name().equals("Person")).findFirst().orElseThrow();
        assertThat(person.attributes()).extracting(MetaAttribute::name).contains("name", "active");
        assertThat(person.operations()).extracting(item -> item.name()).contains("greet");
    }

    private MetamodelImportSource source(String name, String content) {
        return new MetamodelImportSource(name, "text/plain", content.getBytes(StandardCharsets.UTF_8));
    }
}
