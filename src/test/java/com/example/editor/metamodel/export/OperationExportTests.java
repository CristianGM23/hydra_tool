package com.example.editor.metamodel.export;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Comprueba que los principales destinos conservan las operaciones. */
class OperationExportTests {

    @Test
    void textualExportsContainInterfacePropertiesRelationsAndOperations() {
        NeutralMetamodelExportContext context = CanonicalExportTestFixtures.context();
        String plantUml = new PlantUmlMetamodelExporter().export(context);
        String xml = new NeutralXmlMetamodelExporter().export(context);
        String markdown = new MarkdownMetamodelExporter().export(context);
        String agentMarkdown = new AgentMarkdownMetamodelExporter(new PlantUmlMetamodelExporter()).export(context);
        String drawio = new DrawioMetamodelExporter().export(context);

        assertThat(plantUml).contains("identifier", "displayName()", "formatName(prefix");
        assertThat(xml).contains("<operation", "id=\"operation-format-name\"", "<parameter", "behaviorKind=\"QUERY\"");
        assertThat(markdown).contains("## Operations", "displayName", "formatName");
        assertThat(agentMarkdown).contains("operations:", "operation-format-name");
        assertThat(drawio).contains("operation-display-name", "operation-format-name", "employer");
    }

    @Test
    void javaAndPythonExportsContainSignaturesAndSafeStubs() throws Exception {
        Map<String, String> javaFiles = unzip(new JavaMetamodelExporter().export(CanonicalExportTestFixtures.context()));
        String identifiable = javaFiles.get("Identifiable.java");
        String person = javaFiles.get("Person.java");
        Map<String, String> pythonFiles = unzip(new PythonMetamodelExporter().export(CanonicalExportTestFixtures.context()));
        String python = pythonFiles.get("company.py");

        assertThat(identifiable).contains("String getIdentifier()", "void setIdentifier(String value)", "String displayName()");
        assertThat(person).contains("String formatName(String prefix)", "UnsupportedOperationException", "Generated metamodel operation \"formatName\"");
        assertThat(python).contains("def display_name(self) -> str:", "def format_name(self, prefix: Optional[str]) -> str:", "raise NotImplementedError");
    }

    @Test
    void visualExportsRenderRelationDerivedAttributesAndOperationsTogether() {
        NeutralMetamodelExportContext context = CanonicalExportTestFixtures.context();
        String svg = new SvgMetamodelExporter().export(context);
        byte[] png = new PngMetamodelExporter().export(context);

        assertThat(svg).contains("employer : Company", "formatName(prefix: String", "displayName() : String");
        assertThat(png).isNotEmpty();
    }

    private String text(byte[] content) {
        return new String(content, StandardCharsets.UTF_8);
    }

    private Map<String, String> unzip(byte[] content) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        try(ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content), StandardCharsets.UTF_8)) {
            var entry = zip.getNextEntry();
            while(entry != null) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                zip.transferTo(output);
                result.put(entry.getName(), output.toString(StandardCharsets.UTF_8));
                entry = zip.getNextEntry();
            }
        }
        return result;
    }
}
