package com.example.editor.integration;
import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
class IntegrationContractTests {
    @Test void createsSafeImmutableProfile() {
        var profile=IntegrationProfileTestFixtures.emf();
        assertThat(profile.schemaVersion()).isEqualTo("1.0");
        assertThatThrownBy(() -> profile.features().add(IntegrationFeature.PROJECT_EXPORT)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(profile.injectionPolicy().previewRequired()).isTrue();
    }
    @Test void rejectsDestructivePatchWithoutPermission() { assertThatThrownBy(() -> new InjectionPolicy(InjectionStrategy.PATCH_EXISTING,"generated","",true,true,true,false,false,true)).isInstanceOf(IllegalArgumentException.class); }
    @Test void normalizesPortableGeneratedRootSeparators() {
        var policy=new InjectionPolicy(InjectionStrategy.ADD_MODULE," tools\\generated\\emf/ ","",true,true,true,false,false,true);
        assertThat(policy.generatedRoot()).isEqualTo("tools/generated/emf");
    }
    @Test void rejectsGeneratedRootsThatEscapeOrBindTheProfileToOneMachine() {
        assertThatThrownBy(() -> new InjectionPolicy(InjectionStrategy.ADD_MODULE,"../outside","",true,true,true,false,false,true)).hasMessageContaining("inside the project");
        assertThatThrownBy(() -> new InjectionPolicy(InjectionStrategy.ADD_MODULE,"C:\\temp\\generated","",true,true,true,false,false,true)).hasMessageContaining("project-relative");
        assertThatThrownBy(() -> new InjectionPolicy(InjectionStrategy.ADD_MODULE,"/tmp/generated","",true,true,true,false,false,true)).hasMessageContaining("project-relative");
        assertThatThrownBy(() -> new InjectionPolicy(InjectionStrategy.ADD_MODULE,"generated/a:b","",true,true,true,false,false,true)).hasMessageContaining("portable");
        assertThatThrownBy(() -> new InjectionPolicy(InjectionStrategy.ADD_MODULE,"generated/CON","",true,true,true,false,false,true)).hasMessageContaining("portable");
        assertThatThrownBy(() -> new InjectionPolicy(InjectionStrategy.ADD_MODULE,"generated/com1.txt","",true,true,true,false,false,true)).hasMessageContaining("portable");
        assertThatThrownBy(() -> new InjectionPolicy(InjectionStrategy.ADD_MODULE,"generated/folder.","",true,true,true,false,false,true)).hasMessageContaining("portable");
    }
    @Test void requiresARealHostLocator() { assertThatThrownBy(() -> new HostElementReference(IntegrationLocatorStrategy.QUALIFIED_NAME,"","","","","","",Map.of())).isInstanceOf(IllegalArgumentException.class); }
    @Test void recordsGeneratedAndModifiedFilesForRollback() {
        var manifest=new InjectionManifest("1.0","manifest-1","profile-1",IntegrationTechnology.EMF,"2.39","r7",
                Instant.parse("2026-07-26T18:00:00Z"),
                List.of(new InjectionFileChange("generated/EventBridge.java",InjectionFileChangeKind.ADD,InjectionFileOwnership.GENERATED,"","hash",true)),
                List.of(),List.of("install generated module"),List.of("delete generated/EventBridge.java"),Map.of());
        assertThat(manifest.files()).singleElement().extracting(InjectionFileChange::reversible).isEqualTo(true);
        assertThat(manifest.rollbackSteps()).containsExactly("delete generated/EventBridge.java");
    }
    @Test void createsAReversibleReadyInjectionPlan() {
        var plan=new InjectionPlan("1.0","plan-1","profile-1",IntegrationTechnology.BESSER_SDK,InjectionPlanStatus.READY,
                Instant.parse("2026-07-26T18:30:00Z"),List.of(),List.of(),List.of("install generated package"),
                List.of("remove generated package"),List.of(new IntegrationVerificationStep("python-tests","Run generated tests",".",List.of("python","-m","pytest"),0,true)),List.of(),Map.of());
        assertThat(plan.verificationSteps()).singleElement().extracting(step -> step.command().get(0)).isEqualTo("python");
    }
    @Test void rejectsReadyPlanWithBlockingDiagnostics() {
        var error=new IntegrationDiagnostic(IntegrationDiagnosticSeverity.ERROR,"BLOCKED","profile-1","target","Cannot inject");
        assertThatThrownBy(() -> new InjectionPlan("1.0","plan-2","profile-1",IntegrationTechnology.EMF,InjectionPlanStatus.READY,Instant.now(),List.of(),List.of(),List.of(),List.of("rollback"),List.of(new IntegrationVerificationStep("verify","Verify",".",List.of("mvn","test"),0,true)),List.of(error),Map.of())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void requiresRollbackForPreviewAndReadyPlans() {
        assertThatThrownBy(() -> new InjectionPlan("1.0","plan-3","profile-1",IntegrationTechnology.PLANTUML,InjectionPlanStatus.PREVIEW,Instant.now(),List.of(),List.of(),List.of(),List.of(),List.of(new IntegrationVerificationStep("verify","Verify",".",List.of("plantuml","-checkmetadata"),0,true)),List.of(),Map.of())).isInstanceOf(IllegalArgumentException.class);
    }

}
