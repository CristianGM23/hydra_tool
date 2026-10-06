package com.example.editor.integration;
import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
class IntegrationContractResourcesTests {
    @Test void publishesProfileAndManifestSchemas() {
        assertThat(resource("/schemas/integration/integration-profile-model-1.0.schema.json")).contains("IntegrationProfileModel 1.0","BESSER_SDK","PLANTUML","previewRequired");
        assertThat(resource("/schemas/integration/injection-manifest-1.0.schema.json")).contains("InjectionManifest 1.0","rollbackSteps","files");
        assertThat(resource("/schemas/integration/injection-plan-1.0.schema.json")).contains("InjectionPlan 1.0","verificationSteps","rollbackSteps");
    }
    @Test void publishesBesserAndPlantUmlExamples() {
        assertThat(resource("/examples/integration/people-besser-sdk.integration.json")).contains("BESSER_SDK","GAMIFICATION_RUNTIME","BESSER_ELEMENT_ID");
        assertThat(resource("/examples/integration/people-plantuml.integration.json")).contains("PLANTUML","PROJECTION_ONLY","GENERATE_PROJECTION");
    }
    @Test void publishesTechnologyCompilerAndInjectionSchemas() {
        assertThat(resource("/schemas/integration/technology-compilation-result-1.0.schema.json")).contains("TechnologyCompilationResult 1.0","relativePath","rollbackSteps");
        assertThat(resource("/schemas/integration/technology-injection-preview-1.0.schema.json")).contains("TechnologyInjectionPreview 1.0","manualConflictFiles","generatedBytes");
    }
    @Test void publishesEmfAndSiriusExamples() {
        assertThat(resource("/examples/integration/people-emf-module.integration.json")).contains("EMF","ADD_MODULE","basePackage");
        assertThat(resource("/examples/integration/people-sirius-desktop.integration.json")).contains("SIRIUS_DESKTOP","ADD_PLUGIN","pluginId");
    }
    private String resource(String path){try(var stream=getClass().getResourceAsStream(path)){if(stream==null)throw new AssertionError("Missing resource "+path);return new String(stream.readAllBytes(),StandardCharsets.UTF_8);}catch(Exception exception){throw new AssertionError(exception);}}
}
