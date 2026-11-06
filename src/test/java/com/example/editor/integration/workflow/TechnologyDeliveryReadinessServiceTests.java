package com.example.editor.integration.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import com.example.editor.config.StorageProperties;
import com.example.editor.definition.EditorDefinitionTestFixtures;
import com.example.editor.grammar.GrammarProperties;
import com.example.editor.grammar.persistence.GrammarRepository;
import com.example.editor.grammar.persistence.JsonGrammarRepository;
import com.example.editor.integration.IntegrationArtifactResolution;
import com.example.editor.integration.IntegrationArtifactResolver;
import com.example.editor.integration.IntegrationCheckReport;
import com.example.editor.integration.IntegrationDiagnostic;
import com.example.editor.integration.IntegrationDiagnosticSeverity;
import com.example.editor.integration.IntegrationProfileApplicationService;
import com.example.editor.integration.IntegrationProfileChecker;
import com.example.editor.integration.IntegrationProfileLoadResult;
import com.example.editor.integration.IntegrationProfileProperties;
import com.example.editor.integration.IntegrationProfileTestFixtures;
import com.example.editor.integration.IntegrationResolutionStatus;
import com.example.editor.integration.IntegrationSupportLevel;
import com.example.editor.integration.InjectionFileOwnership;
import com.example.editor.integration.IntegrationTargetKind;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.IntegrationVerificationStep;
import com.example.editor.integration.compiler.TechnologyCompilationException;
import com.example.editor.integration.compiler.TechnologyCompilationResult;
import com.example.editor.integration.compiler.TechnologyGeneratedFile;
import com.example.editor.integration.compiler.TechnologyIntegrationApplicationService;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.persistence.LocalCanonicalDocumentStoreFactory;
import tools.jackson.databind.json.JsonMapper;

class TechnologyDeliveryReadinessServiceTests {
    private IntegrationProfileApplicationService profiles;
    private TechnologyWorkflowService workflows;
    private TechnologyIntegrationApplicationService integrations;
    private TechnologyDeliveryReadinessService service;

    @BeforeEach void setUp(){
        profiles=Mockito.mock(IntegrationProfileApplicationService.class);
        workflows=Mockito.mock(TechnologyWorkflowService.class);
        integrations=Mockito.mock(TechnologyIntegrationApplicationService.class);
        service=new TechnologyDeliveryReadinessService(profiles,workflows,integrations);
    }

    @Test void exposesGenerationVerificationInjectionAndExternalExecutionFromRealCompilationEvidence(){
        var profile=IntegrationProfileTestFixtures.emf();
        when(profiles.find("people",profile.id())).thenReturn(new IntegrationProfileLoadResult(profile,"etag",valid(profile.id())));
        when(workflows.descriptor(IntegrationTechnology.EMF)).thenReturn(workflow(IntegrationTechnology.EMF, IntegrationTargetKind.MODELING_RUNTIME));
        when(integrations.compile("people",profile.id())).thenReturn(compilation(IntegrationTechnology.EMF,2,1));

        var readiness=service.readiness("people",profile.id());

        assertThat(readiness.profileValid()).isTrue();
        assertThat(readiness.compilationReady()).isTrue();
        assertThat(readiness.generatedFileCount()).isEqualTo(2);
        assertThat(readiness.nativeVerificationAvailable()).isTrue();
        assertThat(action(readiness,TechnologyWorkflowStage.GENERATE).state()).isEqualTo(TechnologyDeliveryActionState.READY);
        assertThat(action(readiness,TechnologyWorkflowStage.VERIFY).apiSuffix()).isEqualTo("/technology/validate");
        assertThat(action(readiness,TechnologyWorkflowStage.PREVIEW).targetDirectoryRequired()).isTrue();
        assertThat(action(readiness,TechnologyWorkflowStage.APPLY).previewRequired()).isTrue();
        assertThat(action(readiness,TechnologyWorkflowStage.EXECUTE).state()).isEqualTo(TechnologyDeliveryActionState.EXTERNAL);
    }

    @Test void blocksGenerationWithoutCompilingWhenCompatibilityHasErrors(){
        var profile=IntegrationProfileTestFixtures.emf();
        var diagnostic=new IntegrationDiagnostic(IntegrationDiagnosticSeverity.ERROR,"BROKEN","profile","artifacts","Required artifact is missing");
        var report=new IntegrationCheckReport(profile.id(),List.of(diagnostic),List.of());
        when(profiles.find("people",profile.id())).thenReturn(new IntegrationProfileLoadResult(profile,"etag",report));
        when(workflows.descriptor(IntegrationTechnology.EMF)).thenReturn(workflow(IntegrationTechnology.EMF, IntegrationTargetKind.MODELING_RUNTIME));

        var readiness=service.readiness("people",profile.id());

        assertThat(readiness.profileValid()).isFalse();
        assertThat(action(readiness,TechnologyWorkflowStage.GENERATE).state()).isEqualTo(TechnologyDeliveryActionState.BLOCKED);
        assertThat(readiness.blockers()).anyMatch(value->value.contains("BROKEN"));
        verify(integrations,never()).compile("people",profile.id());
    }

    @Test void convertsCompilationFailuresIntoActionableReadinessInsteadOfA500(){
        var profile=IntegrationProfileTestFixtures.emf();
        when(profiles.find("people",profile.id())).thenReturn(new IntegrationProfileLoadResult(profile,"etag",valid(profile.id())));
        when(workflows.descriptor(IntegrationTechnology.EMF)).thenReturn(workflow(IntegrationTechnology.EMF, IntegrationTargetKind.MODELING_RUNTIME));
        when(integrations.compile("people",profile.id())).thenThrow(new TechnologyCompilationException("Generated grammar is inconsistent"));

        var readiness=service.readiness("people",profile.id());

        assertThat(readiness.compilationReady()).isFalse();
        assertThat(action(readiness,TechnologyWorkflowStage.GENERATE).state()).isEqualTo(TechnologyDeliveryActionState.BLOCKED);
        assertThat(action(readiness,TechnologyWorkflowStage.VERIFY).reason()).contains("Generation is blocked");
        assertThat(readiness.blockers()).contains("Generated grammar is inconsistent");
    }

    @Test void projectionTargetsRemainActionableWithoutPretendingTheyHaveAHostInjectionPipeline(){
        var profile=IntegrationProfileTestFixtures.plantUml();
        when(profiles.find("people",profile.id())).thenReturn(new IntegrationProfileLoadResult(profile,"etag",valid(profile.id())));
        when(workflows.descriptor(IntegrationTechnology.PLANTUML)).thenReturn(projectionWorkflow());

        var readiness=service.readiness("people",profile.id());

        assertThat(action(readiness,TechnologyWorkflowStage.GENERATE).state()).isEqualTo(TechnologyDeliveryActionState.READY);
        assertThat(action(readiness,TechnologyWorkflowStage.GENERATE).hydraExecutable()).isFalse();
        assertThat(action(readiness,TechnologyWorkflowStage.PREVIEW).state()).isEqualTo(TechnologyDeliveryActionState.NOT_APPLICABLE);
        verify(integrations,never()).compile("people",profile.id());
    }

    @Test void loadsARealProfileFromARealRepositoryAndComputesGenuineArtifactDiagnosticsInsteadOfAMockedLoad(@TempDir Path temp)throws Exception{
        var mapper=JsonMapper.builder().findAndAddModules().build();
        var storage=new StorageProperties(temp.resolve("metamodels"),temp.resolve("models"),temp.resolve("notations"),
                temp.resolve("validations"),temp.resolve("quickfixes"),temp.resolve("generators"),temp.resolve("assets"),
                temp.resolve("exports"),true);
        var metamodelService=new CanonicalMetamodelApplicationService(new JsonCanonicalMetamodelRepository(storage,new CanonicalMetamodelProperties(),mapper),new NeutralMetamodelValidator());
        metamodelService.create(EditorDefinitionTestFixtures.metamodel());

        var profileProperties=new IntegrationProfileProperties();
        profileProperties.setDirectory(temp.resolve("integration-profiles"));
        var repository=new com.example.editor.integration.persistence.JsonIntegrationProfileRepository(profileProperties,mapper);
        var profile=IntegrationProfileTestFixtures.emf();
        repository.create(profile);

        IntegrationArtifactResolver resolver=(metamodelId,artifact)->new IntegrationArtifactResolution(artifact,IntegrationResolutionStatus.NOT_FOUND,"No real artifact is registered for "+artifact.kind()+" "+artifact.documentId()+" in "+metamodelId);
        var checker=new IntegrationProfileChecker(resolver);
        var grammarProperties=new GrammarProperties();
        grammarProperties.setDirectory(temp.resolve("grammars"));
        GrammarRepository grammars=new JsonGrammarRepository(grammarProperties,mapper,new LocalCanonicalDocumentStoreFactory());
        var realProfiles=new IntegrationProfileApplicationService(repository,metamodelService,checker,grammars);

        var loaded=realProfiles.find("people",profile.id());

        assertThat(loaded.profile().id()).isEqualTo(profile.id());
        assertThat(loaded.etag()).isNotBlank();
        assertThat(loaded.checkReport().artifactResolutions()).isNotEmpty().allMatch(value->value.status()==IntegrationResolutionStatus.NOT_FOUND);
        assertThat(loaded.checkReport().diagnostics()).filteredOn(value->value.code().equals("UNRESOLVED_REQUIRED_ARTIFACT")).hasSize(4);
        assertThat(loaded.checkReport().valid()).isFalse();

        service=new TechnologyDeliveryReadinessService(realProfiles,workflows,integrations);
        when(workflows.descriptor(IntegrationTechnology.EMF)).thenReturn(workflow(IntegrationTechnology.EMF,IntegrationTargetKind.MODELING_RUNTIME));
        var readiness=service.readiness("people",profile.id());
        assertThat(readiness.profileValid()).isFalse();
        assertThat(action(readiness,TechnologyWorkflowStage.GENERATE).state()).isEqualTo(TechnologyDeliveryActionState.BLOCKED);
        verify(integrations,never()).compile("people",profile.id());
    }

    private IntegrationCheckReport valid(String profileId){return new IntegrationCheckReport(profileId,List.of(),List.of());}

    private TechnologyCompilationResult compilation(IntegrationTechnology technology,int files,int nativeSteps){
        var generated=java.util.stream.IntStream.range(0,files).mapToObj(index->new TechnologyGeneratedFile("generated/file"+index+".txt","text/plain",("file"+index).getBytes(java.nio.charset.StandardCharsets.UTF_8),InjectionFileOwnership.GENERATED,true,List.of("people"),Map.of())).toList();
        var steps=java.util.stream.IntStream.range(0,nativeSteps).mapToObj(index->new IntegrationVerificationStep("native-"+index,"Native verification",".",List.of("tool"),0,true)).toList();
        return new TechnologyCompilationResult(technology,generated,List.of(),List.of(),List.of(),steps,List.of(),Map.of());
    }

    private TechnologyWorkflowDescriptor workflow(IntegrationTechnology technology,IntegrationTargetKind kind){
        return new TechnologyWorkflowDescriptor(technology,technology.name(),kind,TechnologyWorkflowMaturity.OPERATIONAL,85,true,true,
                List.of(
                        stage(TechnologyWorkflowStage.CONFIGURE,TechnologyWorkflowAction.EDIT_PROFILE,IntegrationSupportLevel.FULL,true),
                        stage(TechnologyWorkflowStage.ANALYZE,TechnologyWorkflowAction.CHECK_PROFILE,IntegrationSupportLevel.FULL,true),
                        stage(TechnologyWorkflowStage.GENERATE,TechnologyWorkflowAction.COMPILE_ARCHIVE,IntegrationSupportLevel.FULL,true),
                        stage(TechnologyWorkflowStage.VERIFY,TechnologyWorkflowAction.VALIDATE_PROJECT,IntegrationSupportLevel.FULL,true),
                        stage(TechnologyWorkflowStage.PREVIEW,TechnologyWorkflowAction.PREVIEW_CHANGES,IntegrationSupportLevel.FULL,true),
                        stage(TechnologyWorkflowStage.APPLY,TechnologyWorkflowAction.APPLY_CHANGES,IntegrationSupportLevel.FULL,true),
                        stage(TechnologyWorkflowStage.EXECUTE,TechnologyWorkflowAction.RUN_NATIVE_HOST,IntegrationSupportLevel.EXTERNAL,true),
                        stage(TechnologyWorkflowStage.ROUND_TRIP,TechnologyWorkflowAction.ROUND_TRIP_SYNC,IntegrationSupportLevel.PARTIAL,true),
                        stage(TechnologyWorkflowStage.EXPORT,TechnologyWorkflowAction.EXPORT_PROJECT,IntegrationSupportLevel.FULL,true)),List.of(),List.of());
    }

    private TechnologyWorkflowDescriptor projectionWorkflow(){
        return new TechnologyWorkflowDescriptor(IntegrationTechnology.PLANTUML,"PlantUML",IntegrationTargetKind.DIAGRAM_PROJECTION,TechnologyWorkflowMaturity.PROJECTION_ONLY,70,false,false,
                List.of(
                        stage(TechnologyWorkflowStage.CONFIGURE,TechnologyWorkflowAction.EDIT_PROFILE,IntegrationSupportLevel.FULL,true),
                        stage(TechnologyWorkflowStage.ANALYZE,TechnologyWorkflowAction.CHECK_PROFILE,IntegrationSupportLevel.FULL,true),
                        stage(TechnologyWorkflowStage.GENERATE,TechnologyWorkflowAction.EXPORT_PROJECTION,IntegrationSupportLevel.FULL,true),
                        stage(TechnologyWorkflowStage.VERIFY,TechnologyWorkflowAction.VALIDATE_PROJECT,IntegrationSupportLevel.UNSUPPORTED,false),
                        stage(TechnologyWorkflowStage.PREVIEW,TechnologyWorkflowAction.PREVIEW_CHANGES,IntegrationSupportLevel.UNSUPPORTED,false),
                        stage(TechnologyWorkflowStage.APPLY,TechnologyWorkflowAction.APPLY_CHANGES,IntegrationSupportLevel.UNSUPPORTED,false),
                        stage(TechnologyWorkflowStage.EXECUTE,TechnologyWorkflowAction.RUN_NATIVE_HOST,IntegrationSupportLevel.UNSUPPORTED,false),
                        stage(TechnologyWorkflowStage.ROUND_TRIP,TechnologyWorkflowAction.ROUND_TRIP_SYNC,IntegrationSupportLevel.PARTIAL,true),
                        stage(TechnologyWorkflowStage.EXPORT,TechnologyWorkflowAction.EXPORT_PROJECTION,IntegrationSupportLevel.FULL,true)),List.of(),List.of());
    }

    private TechnologyWorkflowStageDescriptor stage(TechnologyWorkflowStage stage,TechnologyWorkflowAction action,IntegrationSupportLevel support,boolean applicable){
        return new TechnologyWorkflowStageDescriptor(stage,support,applicable,action,stage.name(),"description","outcome",List.of());
    }

    private TechnologyDeliveryActionDescriptor action(TechnologyDeliveryReadiness readiness,TechnologyWorkflowStage stage){
        return readiness.actions().stream().filter(value->value.stage()==stage).findFirst().orElseThrow();
    }
}
