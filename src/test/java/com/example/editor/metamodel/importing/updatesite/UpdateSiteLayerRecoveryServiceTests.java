package com.example.editor.metamodel.importing.updatesite;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateSiteLayerRecoveryServiceTests {
    private final UpdateSiteLayerRecoveryService service = new UpdateSiteLayerRecoveryService();

    @Test
    void recoversGmfPaletteRepresentationAndMappingsAsSeparateNeutralLayers() {
        Map<String, byte[]> files = UpdateSiteTestFixtures.entries(
                "plugins/diagram.jar!/model/library.gmftool", """
                        <tools:ToolRegistry xmlns:tools="http://www.eclipse.org/gmf/2005/ToolDefinition" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                          <palette><tools xsi:type="tools:ToolGroup" title="Library">
                            <tools xsi:type="tools:CreationTool" title="Book" element="Book"/>
                            <tools xsi:type="tools:CreationTool" title="Author" element="Author"/>
                          </tools></palette>
                        </tools:ToolRegistry>
                        """,
                "plugins/diagram.jar!/model/library.gmfgraph", """
                        <gmfgraph:Canvas xmlns:gmfgraph="http://www.eclipse.org/gmf/2006/GraphicalDefinition" name="LibraryCanvas">
                          <figures name="BookFigure"/><figures name="AuthorFigure"/>
                        </gmfgraph:Canvas>
                        """,
                "plugins/diagram.jar!/model/library.gmfmap", """
                        <mapping:Mapping xmlns:mapping="http://www.eclipse.org/gmf/2008/mappings">
                          <nodes domainMetaElement="library.ecore#//Book" diagramNode="library.gmfgraph#//BookFigure"/>
                          <links domainMetaElement="library.ecore#//Book/authors" diagramLink="library.gmfgraph#//AuthorLink"/>
                        </mapping:Mapping>
                        """);
        UpdateSiteRepositoryContent repository = new UpdateSiteRepositoryContent(files, "fingerprint", 500);

        UpdateSiteRecoveredLayers layers = service.recover(repository, UpdateSiteImportOptions.defaults());

        assertThat(layers.palettes()).isNotEmpty();
        assertThat(layers.palettes().stream().flatMap(group -> group.tools().stream()))
                .extracting(UpdateSitePaletteToolDraft::label).contains("Book", "Author");
        assertThat(layers.representations()).extracting(UpdateSiteRepresentationDraft::technology).contains("GMF");
        assertThat(layers.mappings()).extracting(UpdateSiteMappingDraft::semanticElement).contains("Book", "authors");
        assertThat(layers.mappings()).extracting(UpdateSiteMappingDraft::confidence)
                .containsOnly(UpdateSiteConfidence.DERIVED);
    }

    @Test
    void recoversSiriusViewpointsAndXtextGrammarToolsWithoutCouplingThemToTheMetamodel() {
        Map<String, byte[]> files = UpdateSiteTestFixtures.entries(
                "plugins/design.jar!/description/library.odesign", """
                        <description:Group xmlns:description="http://www.eclipse.org/sirius/description/1.1.0">
                          <ownedViewpoints name="Library">
                            <ownedRepresentations xsi:type="description_1:DiagramDescription" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:description_1="http://www.eclipse.org/sirius/diagram/description/1.1.0" name="Library diagram" domainClass="library.Book">
                              <defaultLayer><nodeMappings name="Book node" domainClass="library.Book"/>
                                <toolSections name="Creation"><ownedTools xsi:type="description_1:NodeCreationDescription" name="Create Book"/></toolSections>
                              </defaultLayer>
                            </ownedRepresentations>
                          </ownedViewpoints>
                        </description:Group>
                        """,
                "plugins/dsl.jar!/com/example/Library.xtext", """
                        grammar com.example.Library with org.eclipse.xtext.common.Terminals
                        generate library "https://example.com/library"
                        entry Library: books+=Book*;
                        Book: 'book' name=ID;
                        """);
        UpdateSiteRepositoryContent repository = new UpdateSiteRepositoryContent(files, "fingerprint", 500);

        UpdateSiteRecoveredLayers layers = service.recover(repository, UpdateSiteImportOptions.defaults());

        assertThat(layers.representations()).extracting(UpdateSiteRepresentationDraft::technology)
                .contains("SIRIUS", "XTEXT");
        assertThat(layers.palettes().stream().flatMap(group -> group.tools().stream()))
                .extracting(UpdateSitePaletteToolDraft::label).contains("Create Book");
        assertThat(layers.mappings()).anyMatch(mapping -> mapping.mappingKind().equals("GRAMMAR_RULE"));
    }

    @Test
    void respectsIndependentLayerSelectionOptions() {
        UpdateSiteRepositoryContent repository = new UpdateSiteRepositoryContent(Map.of(
                "library.xtext", "entry Library: books+=Book*;\nBook: name=ID;".getBytes(StandardCharsets.UTF_8)), "f", 50);
        UpdateSiteImportOptions options = new UpdateSiteImportOptions("", false, false, "", false, true, false, false);

        UpdateSiteRecoveredLayers layers = service.recover(repository, options);

        assertThat(layers.palettes()).isEmpty();
        assertThat(layers.mappings()).isEmpty();
        assertThat(layers.representations()).hasSize(1);
    }
    @Test
    void preservesDiagramTableAndTreeKindsFromTheCompleteSiriusFixture() throws Exception {
        byte[] odesign = fixture("com.hydra.sirius.fixture.design/description/hydratest.odesign");
        UpdateSiteRepositoryContent repository = new UpdateSiteRepositoryContent(
                Map.of("com.hydra.sirius.fixture.design/description/hydratest.odesign", odesign), "fixture", odesign.length);

        UpdateSiteRecoveredLayers layers = service.recover(repository, UpdateSiteImportOptions.defaults());

        assertThat(layers.representations()).extracting(UpdateSiteRepresentationDraft::name)
                .contains("Hydra Workflow Diagram", "Workflow Elements Table", "Workflow Tree");
        assertThat(layers.representations()).filteredOn(value -> value.name().equals("Hydra Workflow Diagram"))
                .extracting(UpdateSiteRepresentationDraft::kind).containsExactly("DIAGRAM");
        assertThat(layers.representations()).filteredOn(value -> value.name().equals("Workflow Elements Table"))
                .extracting(UpdateSiteRepresentationDraft::kind).containsExactly("TABLE");
        assertThat(layers.representations()).filteredOn(value -> value.name().equals("Workflow Tree"))
                .extracting(UpdateSiteRepresentationDraft::kind).containsExactly("TREE");
    }

    private static byte[] fixture(String relative) throws IOException {
        try(InputStream input = UpdateSiteLayerRecoveryServiceTests.class.getResourceAsStream("/fixtures/sirius-complete/" + relative)) {
            if(input == null) throw new IOException("Missing fixture: " + relative);
            return input.readAllBytes();
        }
    }

}
