package com.example.editor.metamodel.export;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** Comprueba todas las exportaciones textuales sobre el mismo contexto canónico. */
class NeutralExportCompleteSmokeTests {

    @Test
    void exportsNeutralXml() {
        assertThat(new NeutralXmlMetamodelExporter().export(context())).contains("<metamodel", "<reference", "CONTAINMENT", "fileName=\"company.ecore\"");
    }

    @Test
    void exportsNeutralJson() {
        assertThat(new JsonMetamodelExporter(new ObjectMapper()).export(context())).contains("\"schemaVersion\"", "\"relations\"");
    }

    @Test
    void exportsPlantUmlRelations() {
        assertThat(new PlantUmlMetamodelExporter().export(context())).contains("@startuml", "*--", "employees");
    }

    @Test
    void exportsDocumentationMarkdown() {
        assertThat(new MarkdownMetamodelExporter().export(context())).contains("# Metamodel: Company", "## Classes", "Person");
    }

    @Test
    void exportsAgentMarkdown() {
        assertThat(new AgentMarkdownMetamodelExporter(new PlantUmlMetamodelExporter()).export(context())).contains("artifactType: metamodel", "Interpretation rules", "PlantUML projection");
    }

    @Test
    void exportsLangiumGrammar() {
        assertThat(new LangiumMetamodelExporter().export(context())).contains("grammar Company", "entry Model", "employees+=Person", "terminal ID");
    }

    @Test
    void exportsXtextGrammar() {
        assertThat(new XtextMetamodelExporter().export(context())).contains("grammar generated.Company", "import \"https://example.com/company\"", "Person returns model::Person", "employees+=Person");
    }

    @Test
    void exportsAntlrGrammar() {
        assertThat(new AntlrMetamodelExporter().export(context())).contains("grammar Company;", "model\n    : element* EOF", "('employees' person)*", "WS\n    :");
    }

    @Test
    void exportsStructuredDrawio() {
        assertThat(new DrawioMetamodelExporter().export(context())).contains("<mxGraphModel", "swimlane", "childLayout=stackLayout", "startArrow=diamond", "portConstraint=eastwest");
    }

    @Test
    void exportsSvgWithUmlMarkers() {
        assertThat(new SvgMetamodelExporter().export(context())).contains("<svg", "diamond-filled", "Person", "employees");
    }

    @Test
    void omitsSvgBackgroundWhenTransparent() {
        MetamodelExportOptions options = new MetamodelExportOptions(true, true, true, true, 2, "");
        assertThat(new SvgMetamodelExporter().export(CanonicalExportTestFixtures.context(options))).doesNotContain("<rect width=\"100%\" height=\"100%\"");
    }

    static NeutralMetamodelExportContext context() {
        return CanonicalExportTestFixtures.context();
    }
}
