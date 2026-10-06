package com.example.editor.integration.projection.canonical;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.example.editor.integration.IntegrationArtifactKind;
import com.example.editor.integration.IntegrationSupportLevel;
import com.example.editor.integration.compiler.TechnologyAdapterTestFixtures;
import com.example.editor.integration.projection.CanonicalArtifactContent;

class CanonicalProjectionRoundTripTests {
    private final PlantUmlCanonicalProjectionAdapter plant = new PlantUmlCanonicalProjectionAdapter(TechnologyAdapterTestFixtures.MAPPER);
    private final DrawioCanonicalProjectionAdapter drawio = new DrawioCanonicalProjectionAdapter(TechnologyAdapterTestFixtures.MAPPER);

    @Test void plantUmlRestoresExactMetamodelBytes(){assertExact(plant, IntegrationArtifactKind.METAMODEL, metamodel());}
    @Test void drawioRestoresExactMetamodelBytes(){assertExact(drawio, IntegrationArtifactKind.METAMODEL, metamodel());}
    @Test void plantUmlRestoresExactValidationBytes(){assertExact(plant, IntegrationArtifactKind.VALIDATIONS, json("rules"));}
    @Test void drawioRestoresExactEventFlowBytes(){assertExact(drawio, IntegrationArtifactKind.EVENT_FLOWS, json("flows"));}
    @Test void plantUmlWithoutHydraMetadataIsPartial(){assertEquals(IntegrationSupportLevel.PARTIAL, plant.importProjection("external.puml", "@startuml\nclass Person\n@enduml".getBytes(StandardCharsets.UTF_8)).supportLevel());}
    @Test void drawioWithoutHydraMetadataIsPartial(){String xml="<mxfile><diagram><mxGraphModel><root><mxCell id=\"0\"/><mxCell id=\"1\" value=\"Person\" parent=\"0\"/></root></mxGraphModel></diagram></mxfile>";assertEquals(IntegrationSupportLevel.PARTIAL, drawio.importProjection("external.drawio",xml.getBytes(StandardCharsets.UTF_8)).supportLevel());}
    @Test void plantUmlRejectsTamperedCanonicalPayload(){var generated=plant.generate(List.of(new CanonicalArtifactContent(IntegrationArtifactKind.VALIDATIONS,"v","application/json",json("rules")))).artifacts().get(0);String value=new String(generated.content(),StandardCharsets.UTF_8).replace("HYDRA:sha256=", "HYDRA:sha256=broken");assertEquals(IntegrationSupportLevel.UNSUPPORTED, plant.importProjection("bad.puml",value.getBytes(StandardCharsets.UTF_8)).supportLevel());}
    @Test void drawioRejectsDoctype(){String xml="<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><mxfile/>";assertEquals(IntegrationSupportLevel.UNSUPPORTED, drawio.importProjection("bad.drawio",xml.getBytes(StandardCharsets.UTF_8)).supportLevel());}
    @Test void plantUmlMetamodelContainsClassesAndRelations(){String value=text(plant,IntegrationArtifactKind.METAMODEL,metamodel());assertTrue(value.contains("class \"Person\"") && value.contains("class \"Company\"") && value.contains("employer"));}
    @Test void drawioProducesValidMxfileEnvelope(){String value=text(drawio,IntegrationArtifactKind.METAMODEL,metamodel());assertTrue(value.contains("<mxfile") && value.contains("<mxGraphModel") && value.contains("hydraSha256") && value.contains("hydraData"));}
    @Test void projectionGenerationIsDeterministic(){var artifact=new CanonicalArtifactContent(IntegrationArtifactKind.GAMIFICATION_DEFINITIONS,"g","application/json",json("points"));assertEquals(plant.generate(List.of(artifact)).artifacts(),plant.generate(List.of(artifact)).artifacts());assertEquals(drawio.generate(List.of(artifact)).artifacts(),drawio.generate(List.of(artifact)).artifacts());}

    private void assertExact(CanonicalProjectionAdapter adapter, IntegrationArtifactKind kind, byte[] bytes){var generated=adapter.generate(List.of(new CanonicalArtifactContent(kind,"doc","application/json",bytes))).artifacts().get(0);var imported=adapter.importProjection(generated.relativePath(),generated.content());assertEquals(IntegrationSupportLevel.FULL, imported.supportLevel());assertEquals(1, imported.recoveredArtifacts().size());assertArrayEquals(bytes, imported.recoveredArtifacts().get(0).content());}
    private String text(CanonicalProjectionAdapter adapter,IntegrationArtifactKind kind,byte[] bytes){return new String(adapter.generate(List.of(new CanonicalArtifactContent(kind,"doc","application/json",bytes))).artifacts().get(0).content(),StandardCharsets.UTF_8);}
    private byte[] metamodel(){try{return TechnologyAdapterTestFixtures.MAPPER.writeValueAsBytes(com.example.editor.definition.EditorDefinitionTestFixtures.metamodel());}catch(RuntimeException e){throw e;}}
    private byte[] json(String field){return ("{\"schemaVersion\":\"1.0\",\"id\":\"doc\",\""+field+"\":[{\"id\":\"one\",\"name\":\"One\"}]}").getBytes(StandardCharsets.UTF_8);}
}
