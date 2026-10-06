package com.example.editor.integration;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
class IntegrationTechnologyCatalogTests {
    @Test void exposesEverySupportedTarget() { assertThat(IntegrationTechnologyCatalog.descriptors()).extracting(IntegrationTechnologyDescriptor::technology).containsExactlyInAnyOrder(IntegrationTechnology.values()); }
    @Test void separatesBesserSdkFromWebEditor() {
        var sdk=IntegrationTechnologyCatalog.descriptor(IntegrationTechnology.BESSER_SDK);
        var web=IntegrationTechnologyCatalog.descriptor(IntegrationTechnology.BESSER_WEB_MODELING_EDITOR);
        assertThat(sdk.support(IntegrationCapability.UI_CONTRIBUTION)).isEqualTo(IntegrationSupportLevel.UNSUPPORTED);
        assertThat(web.support(IntegrationCapability.UI_CONTRIBUTION)).isEqualTo(IntegrationSupportLevel.PARTIAL);
        assertThat(sdk.locatorStrategies()).contains(IntegrationLocatorStrategy.BESSER_ELEMENT_ID);
    }
    @Test void treatsPlantUmlAsProjectionOnly() {
        var value=IntegrationTechnologyCatalog.descriptor(IntegrationTechnology.PLANTUML);
        assertThat(value.targetKind()).isEqualTo(IntegrationTargetKind.DIAGRAM_PROJECTION);
        assertThat(value.deliveryModes()).containsExactly(IntegrationDeliveryMode.PROJECTION_ONLY);
        assertThat(value.injectionStrategies()).containsExactly(InjectionStrategy.GENERATE_PROJECTION);
        assertThat(value.support(IntegrationCapability.DIAGRAM_RENDERING)).isEqualTo(IntegrationSupportLevel.FULL);
        assertThat(value.support(IntegrationCapability.EVENT_PUBLISH)).isEqualTo(IntegrationSupportLevel.UNSUPPORTED);
    }
    @Test void exposesNativeCodeGenerationForEmfAndSirius() {
        assertThat(IntegrationTechnologyCatalog.descriptor(IntegrationTechnology.EMF).support(IntegrationCapability.CODE_GENERATION)).isEqualTo(IntegrationSupportLevel.FULL);
        assertThat(IntegrationTechnologyCatalog.descriptor(IntegrationTechnology.SIRIUS_DESKTOP).support(IntegrationCapability.CODE_GENERATION)).isEqualTo(IntegrationSupportLevel.FULL);
    }
    @Test void keepsRuntimePlatformsIndependentFromTheirLibraries() {
        assertThat(IntegrationTechnologyCatalog.class.getDeclaredFields()).noneMatch(field -> field.getType().getName().startsWith("org.eclipse") || field.getType().getName().startsWith("com.google") || field.getType().getName().startsWith("besser"));
    }
    @Test void describesMxGraphDrawioAsGraphicalTargetWithExplicitIntegrationMethods() {
        var value=IntegrationTechnologyCatalog.descriptor(IntegrationTechnology.MXGRAPH_DRAWIO);
        assertThat(value.displayName()).isEqualTo("mxGraph / draw.io");
        assertThat(value.targetKind()).isEqualTo(IntegrationTargetKind.GRAPHICAL_MODELING_EDITOR);
        assertThat(value.recommendedDeliveryMode()).isEqualTo(IntegrationDeliveryMode.GENERATED_MODULE);
        assertThat(value.recommendedInjectionStrategy()).isEqualTo(InjectionStrategy.ADD_PACKAGE);
        assertThat(value.recommendedBuildSystem()).isEqualTo(IntegrationBuildSystem.NPM);
        assertThat(value.injectionStrategies()).contains(InjectionStrategy.ADD_PACKAGE, InjectionStrategy.ADD_MODULE, InjectionStrategy.GENERATE_SIDECAR);
        assertThat(value.support(IntegrationCapability.DIAGRAM_RENDERING)).isEqualTo(IntegrationSupportLevel.FULL);
        assertThat(value.support(IntegrationCapability.QUICK_FIX_BRIDGE)).isEqualTo(IntegrationSupportLevel.FULL);
    }
    @Test void everyTechnologyPublishesSupportedRecommendedDefaults() {
        for (var value : IntegrationTechnologyCatalog.descriptors()) {
            assertThat(value.deliveryModes()).contains(value.recommendedDeliveryMode());
            assertThat(value.injectionStrategies()).contains(value.recommendedInjectionStrategy());
            assertThat(value.recommendedBuildSystem()).isNotNull();
        }
    }

}
