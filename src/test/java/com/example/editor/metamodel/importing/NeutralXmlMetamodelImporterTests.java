package com.example.editor.metamodel.importing;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.importing.importers.NeutralXmlMetamodelImporter;
import com.example.editor.metamodel.importing.canonicalization.ImportedMetamodelCanonicalizer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba el XML neutral.
 */
class NeutralXmlMetamodelImporterTests {

    private final NeutralXmlMetamodelImporter importer = new NeutralXmlMetamodelImporter();

    @Test
    void importsClassesEnumsDataTypesAndRelations() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <metamodel schemaVersion="2.1" id="company" name="Company" fileName="company.ecore" namespaceUri="urn:company" namespacePrefix="company">
                  <dataTypes><dataType id="datatype-money" name="Money" implementationTypeName="com.example.Money" serializable="true"/></dataTypes>
                  <enumerations><enumeration id="enum-state" name="State"><literal id="literal-active" name="ACTIVE" value="1"/></enumeration></enumerations>
                  <classes>
                    <class id="class-person" name="Person" kind="CLASS"><attribute id="attribute-name" name="name" type="String" typeKind="PRIMITIVE" lowerBound="1" upperBound="1" ordered="true" unique="true" changeable="true" defaultValue=""/></class>
                    <class id="class-company" name="Company" kind="CLASS"/>
                  </classes>
                  <relations><reference id="reference-employees" name="employees" sourceTypeId="class-company" targetTypeId="class-person" kind="CONTAINMENT" targetMultiplicity="0..*" oppositeRelationId=""/></relations>
                </metamodel>
                """;
        var output = importer.importMetamodel(source(xml), MetamodelImportOptions.defaults());
        assertThat(output.metamodel().classes()).hasSize(2);
        assertThat(output.metamodel().enums()).hasSize(1);
        assertThat(output.metamodel().dataTypes()).hasSize(1);
        assertThat(output.metamodel().relations()).singleElement().extracting(item -> item.kind()).isEqualTo(MetaRelationKind.CONTAINMENT);
    }

    @Test
    void preservesEcoreSpecificOptionsInTargetConfiguration() {
        String xml = """
                <metamodel schemaVersion="2.1" id="special" name="Special" fileName="special.ecore" namespaceUri="urn:special" namespacePrefix="sp">
                  <dataTypes><dataType id="datatype-money" name="Money" implementationTypeName="com.example.Money" serializable="true"/></dataTypes>
                  <classes>
                    <class id="class-root" name="Root" kind="CLASS"><attribute id="attribute-value" name="value" type="Money" typeKind="DATATYPE" lowerBound="1" upperBound="1" changeable="false"/></class>
                    <class id="class-child" name="Child" kind="CLASS"/>
                  </classes>
                  <relations><relation id="relation-children" name="children" sourceTypeId="class-root" targetTypeId="class-child" kind="REFERENCE" targetMultiplicity="0..*" changeable="false" resolveProxies="true"/></relations>
                </metamodel>
                """;

        var prepared = ImportedMetamodelCanonicalizer.prepare(importer.importMetamodel(source(xml), MetamodelImportOptions.defaults()).metamodel());
        var ecore = prepared.targetConfiguration().ecore();

        assertThat(ecore.fileName()).isEqualTo("special.ecore");
        assertThat(ecore.namespaceUri()).isEqualTo("urn:special");
        assertThat(ecore.namespacePrefix()).isEqualTo("sp");
        assertThat(ecore.attribute("attribute-value").changeable()).isFalse();
        assertThat(ecore.relation("relation-children", false).changeable()).isFalse();
        assertThat(ecore.relation("relation-children", false).resolveProxies()).isTrue();
        assertThat(ecore.dataType("datatype-money").instanceClassName()).isEqualTo("com.example.Money");
        assertThat(prepared.metamodel().annotations().keySet()).noneMatch(key -> key.startsWith("import:ecore:"));
    }

    @Test
    void reportsMissingLayout() {
        var output = importer.importMetamodel(source("<metamodel id=\"sample\" name=\"Sample\"><classes/></metamodel>"), MetamodelImportOptions.defaults());
        assertThat(output.warnings()).extracting(item -> item.code()).contains("IMPORT_LAYOUT_NOT_AVAILABLE");
    }

    private MetamodelImportSource source(String xml) {
        return new MetamodelImportSource("sample.xml", "application/xml", xml.getBytes(StandardCharsets.UTF_8));
    }
}
