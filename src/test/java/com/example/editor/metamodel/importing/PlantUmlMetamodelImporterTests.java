package com.example.editor.metamodel.importing;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.importing.importers.AgentMarkdownMetamodelImporter;
import com.example.editor.metamodel.importing.importers.PlantUmlMetamodelImporter;
import com.example.editor.metamodel.importing.canonicalization.ImportedMetamodelCanonicalizer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba PlantUML y Markdown para agentes.
 */
class PlantUmlMetamodelImporterTests {

    private final PlantUmlMetamodelImporter plantImporter = new PlantUmlMetamodelImporter();

    @Test
    void importsUmlClassKindsAttributesEnumsAndRelations() {
        String plantUml = """
                @startuml
                interface "Named" as M_Named {
                  name : String [1]
                }
                class "Person" as M_Person {
                  age : Integer [0..1]
                }
                enum "State" as M_State {
                  ACTIVE = 1
                }
                class "Money" as M_Money <<datatype>>
                M_Person ..|> M_Named
                M_Person --> "0..1" M_Named : owner
                @enduml
                """;
        var output = plantImporter.importMetamodel(source("company.puml", plantUml), MetamodelImportOptions.defaults());
        assertThat(output.metamodel().classes().stream().filter(item -> item.name().equals("Named")).findFirst().orElseThrow().kind()).isEqualTo(MetaClassKind.INTERFACE);
        assertThat(output.metamodel().enums()).hasSize(1);
        assertThat(output.metamodel().dataTypes()).hasSize(1);
        assertThat(output.metamodel().relations()).extracting(item -> item.kind()).contains(MetaRelationKind.IMPLEMENTATION, MetaRelationKind.REFERENCE);
    }

    @Test
    void importsAgentMarkdownMetadata() {
        String markdown = """
                ---
                artifactType: metamodel
                schemaVersion: 2.1
                metamodelId: company
                name: Company Domain
                namespaceUri: urn:company
                namespacePrefix: company
                ---

                ```plantuml
                @startuml
                class "Person" as M_Person {
                  name : String [1]
                }
                @enduml
                ```
                """;
        var importer = new AgentMarkdownMetamodelImporter(plantImporter);
        var output = importer.importMetamodel(source("company.agent.md", markdown), MetamodelImportOptions.defaults());
        assertThat(output.metamodel().id()).isEqualTo("company");
        assertThat(output.metamodel().name()).isEqualTo("Company Domain");
        assertThat(ImportedMetamodelCanonicalizer.prepare(output.metamodel()).targetConfiguration().ecore().namespaceUri()).isEqualTo("urn:company");
    }

    private MetamodelImportSource source(String fileName, String content) {
        return new MetamodelImportSource(fileName, "text/plain", content.getBytes(StandardCharsets.UTF_8));
    }
}
