package com.example.editor.metamodel.importing;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba la detección de formatos.
 */
class MetamodelImportDetectorTests {

    private final MetamodelImportDetector detector = new MetamodelImportDetector();

    @Test
    void detectsNeutralJson() {
        assertThat(detect("sample.json", "{\"schemaVersion\":\"1.0\"}")).isEqualTo(MetamodelImportFormat.JSON);
    }

    @Test
    void detectsDrawioBeforeGenericXml() {
        assertThat(detect("sample.xml", "<mxfile><diagram/></mxfile>")).isEqualTo(MetamodelImportFormat.DRAWIO);
    }

    @Test
    void detectsEcoreContent() {
        assertThat(detect("sample.ecore", "<ecore:EPackage xmlns:ecore=\"http://www.eclipse.org/emf/2002/Ecore\"/>")).isEqualTo(MetamodelImportFormat.ECORE);
    }

    @Test
    void keepsXmiExtensionForEPackageXmi() {
        assertThat(detect("sample.xmi", "<ecore:EPackage xmlns:ecore=\"http://www.eclipse.org/emf/2002/Ecore\"/>")).isEqualTo(MetamodelImportFormat.XMI);
    }

    @Test
    void detectsNeutralXml() {
        assertThat(detect("sample.xml", "<?xml version=\"1.0\"?><metamodel id=\"sample\"/>")).isEqualTo(MetamodelImportFormat.XML);
    }

    @Test
    void detectsLangium() {
        assertThat(detect("sample.langium", "grammar Sample\nentry Model: elements+=Element*;")).isEqualTo(MetamodelImportFormat.LANGIUM);
    }

    @Test
    void detectsXtext() {
        assertThat(detect("sample.xtext", "grammar generated.Sample with org.eclipse.xtext.common.Terminals")).isEqualTo(MetamodelImportFormat.XTEXT);
    }

    @Test
    void detectsAntlr() {
        assertThat(detect("sample.g4", "grammar Sample;\nmodel: EOF;")).isEqualTo(MetamodelImportFormat.ANTLR);
    }

    @Test
    void detectsAgentMarkdownBeforePlantUml() {
        assertThat(detect("sample.agent.md", "---\nartifactType: metamodel\n---\n```plantuml\n@startuml\n@enduml\n```")).isEqualTo(MetamodelImportFormat.MARKDOWN_AGENT);
    }

    @Test
    void detectsEmfaticByExtension() {
        assertThat(detect("sample.emf", "package sample; class Item { }")).isEqualTo(MetamodelImportFormat.EMF);
    }

    @Test
    void detectsEmfaticByStructuralKeywords() {
        assertThat(detect("sample.txt", "package sample; class Item { attr String name; }")).isEqualTo(MetamodelImportFormat.EMF);
    }

    @Test
    void keepsEmfaticExtensionWhenTheContentAlsoLooksLikeJava() {
        assertThat(detect("sample.emfatic", "package sample; public class Item { }")).isEqualTo(MetamodelImportFormat.EMF);
    }

    @Test
    void keepsPythonExtensionForABareClass() {
        assertThat(detect("person.py", "class Person:\n    pass\n")).isEqualTo(MetamodelImportFormat.PYTHON);
    }

    @Test
    void keepsJavaExtensionForABareClass() {
        assertThat(detect("Person.java", "class Person { }")).isEqualTo(MetamodelImportFormat.JAVA);
    }

    @Test
    void doesNotGuessAFormatFromAnUnclassifiedGenericZipName() {
        assertThatThrownBy(() -> detect("sources.zip", "not a zip archive")).isInstanceOf(RuntimeException.class).hasMessageContaining("could not be detected");
    }


    @Test
    void detectsJavaSourcesByExtensionAndStructure() {
        assertThat(detect("Person.java", "package demo; public class Person { String name; }")).isEqualTo(MetamodelImportFormat.JAVA);
    }

    @Test
    void detectsPythonSourcesByExtensionAndStructure() {
        assertThat(detect("person.py", "from dataclasses import dataclass\n@dataclass\nclass Person:\n    name: str\n")).isEqualTo(MetamodelImportFormat.PYTHON);
    }

    @Test
    void doesNotMisclassifyNeutralJsonOnlyBecauseItContainsDiagramType() {
        assertThat(detect("model.json", "{\"schemaVersion\":\"2.1\",\"diagramType\":\"visual-only\"}")).isEqualTo(MetamodelImportFormat.JSON);
    }

    private MetamodelImportFormat detect(String fileName, String content) {
        return detector.detect(new MetamodelImportSource(fileName, "text/plain", content.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Descripción:
     * Detecta el Markdown documental incluso sin una extensión útil.
     */
    @Test
    void detectsHumanMarkdownByContent() {
        MetamodelImportSource source = new MetamodelImportSource("upload.txt", "text/plain", "# Metamodel: Company\n\n## Classes\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThat(detector.detect(source)).isEqualTo(MetamodelImportFormat.MARKDOWN);
    }
}
