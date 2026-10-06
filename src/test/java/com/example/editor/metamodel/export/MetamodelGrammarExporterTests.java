package com.example.editor.metamodel.export;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Comprueba las gramáticas y el XML generados desde MetaModel. */
class MetamodelGrammarExporterTests {

    @Test
    void generatesLangiumGrammar() {
        String content = new LangiumMetamodelExporter().export(CanonicalExportTestFixtures.context());

        assertThat(content).contains("grammar Company", "entry Model:", "employer=[Company:ID]", "Status returns string:", "terminal ID:");
    }

    @Test
    void generatesXtextGrammarBoundToConfiguredEcoreProjection() {
        String content = new XtextMetamodelExporter().export(CanonicalExportTestFixtures.context());

        assertThat(content).contains("with org.eclipse.xtext.common.Terminals");
        assertThat(content).contains("import \"https://example.com/company\" as model");
        assertThat(content).contains("Person returns model::Person:");
        assertThat(content).contains("employer=[model::Company|ID]");
        assertThat(content).contains("enum Status returns model::Status:");
    }

    @Test
    void generatesAntlrGrammar() {
        String content = new AntlrMetamodelExporter().export(CanonicalExportTestFixtures.context());

        assertThat(content).contains(
                "grammar Company;",
                "model\n    : element* EOF",
                "('employer' ID /* HYDRA_REFERENCE company */)?",
                "status\n    : 'ACTIVE'",
                "WS\n    : [ \\t\\r\\n]+ -> skip");
    }

    @Test
    void generatesNeutralXmlWithTargetsAndDiagram() {
        String content = new NeutralXmlMetamodelExporter().export(CanonicalExportTestFixtures.context());

        assertThat(content).contains("<metamodel fileName=\"company.ecore\"");
        assertThat(content).contains("namespaceUri=\"https://example.com/company\"");
        assertThat(content).contains("<class id=\"class-person\" name=\"Person\"");
        assertThat(content).contains("<reference id=\"reference-employer\"");
        assertThat(content).contains("<diagram metamodelId=\"company\"");
    }
}
