package com.example.editor.definition.importing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.zip.Deflater;

import org.junit.jupiter.api.Test;

import com.example.editor.definition.EditorDefinitionTestFixtures;

import tools.jackson.databind.json.JsonMapper;

class VisualDefinitionImportAnalysisTests {
    private final VisualDefinitionImportAnalyzer analyzer = new VisualDefinitionImportAnalyzer();
    private final DrawioVisualImportSupport drawio = new DrawioVisualImportSupport(JsonMapper.builder().build(), analyzer);

    @Test
    void normalizesUniqueClassReferencesAndReportsUndefinedExpressionFeatures() {
        VisualDefinitionImportDraft draft = new VisualDefinitionImportDraft("SIRIUS_DESKTOP", EditorDefinitionTestFixtures.metamodel());
        assertThat(analyzer.resolveClass(draft, "person", "sample.odesign", "node", true)).isEqualTo("class:Person");
        analyzer.inspectExpression(draft, "sample.odesign", "labelExpression", "self.name + self.missingFeature", "class:Person");
        assertThat(draft.fixes).anyMatch(fix -> fix.code().equals("NORMALIZE_CLASS_REFERENCE") && fix.automatic() && fix.applied());
        assertThat(draft.diagnostics).anyMatch(diagnostic -> diagnostic.code().equals("UNDEFINED_EXPRESSION_FEATURE") && diagnostic.element().equals("missingFeature"));
    }

    @Test
    void recoversUncompressedDrawioNodesAndInfersTheOnlyCompatibleRelation() {
        VisualDefinitionImportDraft draft = new VisualDefinitionImportDraft("DRAWIO", EditorDefinitionTestFixtures.metamodel());
        drawio.recover(Map.of("people.drawio", drawioProject(graphXml()).getBytes(StandardCharsets.UTF_8)), draft);
        analyzer.validateRecoveredDraft(draft);
        assertThat(draft.nodeMappings).hasSize(2);
        assertThat(draft.edgeMappings).hasSize(1);
        assertThat(draft.edgeMappings.get(0).relationId()).isEqualTo("relation:Person:employer");
        assertThat(draft.fixes).anyMatch(fix -> fix.code().equals("INFER_RELATION_FROM_ENDPOINTS") && fix.applied());
        assertThat(draft.nodeMappings.get(0).styles()).containsKeys("fillColor", "strokeColor", "labelColor");
        assertThat(draft.representations).hasSize(1);
    }

    @Test
    void decodesCompressedDrawioPagesWithinTheSafetyBoundary() {
        VisualDefinitionImportDraft draft = new VisualDefinitionImportDraft("DRAWIO", EditorDefinitionTestFixtures.metamodel());
        String project = "<mxfile><diagram id=\"p1\" name=\"Compressed\">" + compressDrawio(graphXml()) + "</diagram></mxfile>";
        drawio.recover(Map.of("compressed.drawio", project.getBytes(StandardCharsets.UTF_8)), draft);
        assertThat(draft.nodeMappings).hasSize(2);
        assertThat(draft.fixes).anyMatch(fix -> fix.code().equals("DECOMPRESS_DRAWIO_PAGE") && fix.applied());
    }

    @Test
    void reportsBrokenDrawioReferencesAndConflictingCellRolesWithoutInventingSemantics() {
        VisualDefinitionImportDraft draft = new VisualDefinitionImportDraft("DRAWIO", EditorDefinitionTestFixtures.metamodel());
        String graph = """
                <mxGraphModel><root>
                  <mxCell id="0"/><mxCell id="1" parent="0"/>
                  <mxCell id="broken" value="Person" vertex="1" edge="1" parent="1"><mxGeometry width="120" height="80" as="geometry"/></mxCell>
                  <mxCell id="edge" edge="1" parent="1" source="missing" target="broken"><mxGeometry relative="1" as="geometry"/></mxCell>
                </root></mxGraphModel>
                """;
        drawio.recover(Map.of("broken.drawio", drawioProject(graph).getBytes(StandardCharsets.UTF_8)), draft);
        assertThat(draft.diagnostics).anyMatch(diagnostic -> diagnostic.code().equals("DRAWIO_CELL_VERTEX_EDGE_CONFLICT"));
        assertThat(draft.diagnostics).anyMatch(diagnostic -> diagnostic.code().equals("DRAWIO_DANGLING_EDGE_REFERENCE"));
        assertThat(draft.fixes).anyMatch(fix -> fix.code().equals("CHOOSE_DRAWIO_CELL_KIND") && !fix.automatic());
    }

    @Test
    void importsMxLibraryXmlTemplates() {
        VisualDefinitionImportDraft draft = new VisualDefinitionImportDraft("DRAWIO", EditorDefinitionTestFixtures.metamodel());
        String escaped = graphXml().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "");
        String library = "<mxlibrary>[{\"title\":\"Person\",\"w\":220,\"h\":140,\"xml\":\"" + escaped.replace("<", "&lt;").replace(">", "&gt;") + "\"}]</mxlibrary>";
        drawio.recover(Map.of("people.mxlibrary", library.getBytes(StandardCharsets.UTF_8)), draft);
        assertThat(draft.representations).isNotEmpty();
        assertThat(draft.nodeMappings).isNotEmpty();
    }

    private String drawioProject(String graph) { return "<mxfile><diagram id=\"p1\" name=\"People\">" + graph + "</diagram></mxfile>"; }

    private String graphXml() {
        return """
                <mxGraphModel><root>
                  <mxCell id="0"/><mxCell id="1" parent="0"/>
                  <mxCell id="person" value="Person" vertex="1" parent="1" style="rounded=1;fillColor=#E3F2FD;strokeColor=#1565C0;fontColor=#102A43;"><mxGeometry x="40" y="60" width="220" height="140" as="geometry"/></mxCell>
                  <mxCell id="company" value="Company" vertex="1" parent="1"><mxGeometry x="360" y="60" width="220" height="140" as="geometry"/></mxCell>
                  <mxCell id="employer-edge" value="" edge="1" parent="1" source="person" target="company"><mxGeometry relative="1" as="geometry"/></mxCell>
                </root></mxGraphModel>
                """;
    }

    private String compressDrawio(String xml) {
        byte[] encoded = URLEncoder.encode(xml, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        deflater.setInput(encoded); deflater.finish(); ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[1024];
        while(!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer));
        deflater.end(); return Base64.getEncoder().encodeToString(out.toByteArray());
    }
}
