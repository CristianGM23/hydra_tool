package com.example.editor.metamodel.export;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaEnum;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.importing.MetamodelImportOptions;
import com.example.editor.metamodel.importing.MetamodelImportSource;
import com.example.editor.metamodel.importing.importers.LangiumMetamodelImporter;

import static org.assertj.core.api.Assertions.assertThat;

/** Comprueba las gramáticas y el XML generados desde MetaModel. */
class MetamodelGrammarExporterTests {

    @Test
    void generatesLangiumGrammar() {
        String content = new LangiumMetamodelExporter().export(CanonicalExportTestFixtures.context());

        assertThat(content).contains("grammar Company", "entry Model:", "employer=[Company:ID]", "Status returns string:", "terminal ID:");
    }

    @Test
    void langiumRuleOfAClassWithoutFeaturesStillCreatesAnObject() {
        String content = new LangiumMetamodelExporter().export(CanonicalExportTestFixtures.context());

        // Without an assignment Langium cannot infer a type ("This parser rule does not create an object"): the rule starts with an action.
        assertThat(content).contains("Tag:\n    {infer Tag} 'tag' '{'", "Audit:\n    {infer Audit} 'audit' '{'");
        Matcher rule = Pattern.compile("(?m)^([A-Z]\\w*):\\n    ('[^']+'[^;]*);").matcher(content);
        while(rule.find()) {
            assertThat(rule.group(2)).as("class rule %s must assign a feature or start with an action", rule.group(1)).containsPattern("=|\\{infer ");
        }
    }

    @Test
    void langiumClassWithoutFeaturesIsReimportedAsAClass() {
        String content = new LangiumMetamodelExporter().export(CanonicalExportTestFixtures.context());
        MetaModel imported = new LangiumMetamodelImporter().importMetamodel(
                new MetamodelImportSource("company.langium", "text/plain", content.getBytes(StandardCharsets.UTF_8)), MetamodelImportOptions.defaults()).metamodel();

        assertThat(imported.classes()).extracting(MetaClass::name).contains("Tag", "Audit");
        assertThat(imported.enums()).extracting(MetaEnum::name).doesNotContain("Tag", "Audit");
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
