package com.example.editor.integration;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
class IntegrationProfileCheckerTests {
    private final IntegrationArtifactResolver resolver = (metamodelId, artifact) -> new IntegrationArtifactResolution(artifact,
            artifact.kind() == IntegrationArtifactKind.GAMIFICATION_DEFINITIONS ? IntegrationResolutionStatus.DEFERRED : IntegrationResolutionStatus.RESOLVED,
            artifact.kind() == IntegrationArtifactKind.GAMIFICATION_DEFINITIONS ? "Implemented in a later block" : "Resolved");
    private final IntegrationProfileChecker checker = new IntegrationProfileChecker(resolver);
    @Test void acceptsAProjectionOnlyPlantUmlProfile() { assertThat(checker.check(IntegrationProfileTestFixtures.plantUml()).valid()).isTrue(); }
    @Test void acceptsBesserSdkWithDeferredGamificationAsWarning() {
        var report=checker.check(IntegrationProfileTestFixtures.besserSdk());
        assertThat(report.valid()).isTrue();
        assertThat(report.diagnostics()).extracting(IntegrationDiagnostic::code).contains("DEFERRED_ARTIFACT");
    }
    @Test void rejectsRuntimeFeaturesForPlantUml() {
        var base=IntegrationProfileTestFixtures.plantUml();
        var invalid=new IntegrationProfileModel(base.schemaVersion(),base.id(),base.metamodelId(),base.name(),base.target(),base.deliveryMode(),Set.of(IntegrationFeature.EVENT_PUBLICATION),base.artifacts(),base.mappings(),base.injectionPolicy(),base.requiredCapabilities(),base.annotations());
        assertThat(checker.check(invalid).diagnostics()).extracting(IntegrationDiagnostic::code).contains("PLANTUML_RUNTIME_FORBIDDEN","UNSUPPORTED_CAPABILITY");
    }
    @Test void rejectsBesserSdkUiAndAllowsWebEditorUiAsPartial() {
        var sdk=IntegrationProfileTestFixtures.besserSdk();
        var invalid=new IntegrationProfileModel(sdk.schemaVersion(),sdk.id(),sdk.metamodelId(),sdk.name(),sdk.target(),sdk.deliveryMode(),Set.of(IntegrationFeature.GAMIFICATION_UI),sdk.artifacts(),sdk.mappings(),sdk.injectionPolicy(),sdk.requiredCapabilities(),sdk.annotations());
        assertThat(checker.check(invalid).diagnostics()).extracting(IntegrationDiagnostic::code).contains("BESSER_SDK_UI_TARGET");
    }
    @Test void detectsMissingFeatureArtifacts() {
        var base=IntegrationProfileTestFixtures.emf();
        var artifacts=new ArrayList<>(base.artifacts()); artifacts.removeIf(item -> item.kind()==IntegrationArtifactKind.VALIDATIONS);
        var invalid=new IntegrationProfileModel(base.schemaVersion(),base.id(),base.metamodelId(),base.name(),base.target(),base.deliveryMode(),base.features(),artifacts,base.mappings(),base.injectionPolicy(),base.requiredCapabilities(),base.annotations());
        assertThat(checker.check(invalid).diagnostics()).extracting(IntegrationDiagnostic::code).contains("MISSING_FEATURE_ARTIFACT");
    }
    @Test void requiresPreviewAndRollback() {
        var base=IntegrationProfileTestFixtures.emf();
        var unsafe=new InjectionPolicy(base.injectionPolicy().strategy(),base.injectionPolicy().generatedRoot(),"",false,false,true,false,false,true);
        var invalid=new IntegrationProfileModel(base.schemaVersion(),base.id(),base.metamodelId(),base.name(),base.target(),base.deliveryMode(),base.features(),base.artifacts(),base.mappings(),unsafe,base.requiredCapabilities(),base.annotations());
        assertThat(checker.check(invalid).diagnostics()).extracting(IntegrationDiagnostic::code).contains("PREVIEW_REQUIRED","ROLLBACK_REQUIRED");
    }
    @Test void rejectsLocatorNotSupportedByTarget() {
        var base=IntegrationProfileTestFixtures.plantUml();
        var mapping=IntegrationProfileTestFixtures.mapping(IntegrationLocatorStrategy.AST_PATH,"Person");
        var invalid=new IntegrationProfileModel(base.schemaVersion(),base.id(),base.metamodelId(),base.name(),base.target(),base.deliveryMode(),base.features(),base.artifacts(),List.of(mapping),base.injectionPolicy(),base.requiredCapabilities(),base.annotations());
        assertThat(checker.check(invalid).diagnostics()).extracting(IntegrationDiagnostic::code).contains("UNSUPPORTED_LOCATOR");
    }
}
