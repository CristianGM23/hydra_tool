package com.example.editor.integration.adapter.glsp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.editor.definition.EditorDefinitionModel;
import com.example.editor.definition.EditorDefinitionTestFixtures;
import com.example.editor.definition.RepresentationDefinition;
import com.example.editor.definition.RepresentationKind;
import com.example.editor.integration.IntegrationArtifactKind;
import com.example.editor.integration.IntegrationProfileModel;
import com.example.editor.integration.IntegrationTarget;
import com.example.editor.integration.compiler.TechnologyAdapterTestFixtures;
import com.example.editor.integration.compiler.TechnologyCompilationRequest;
import com.example.editor.integration.projection.CanonicalArtifactContent;

class EclipseGlspTechnologyAdapterTests {
    private final EclipseGlspTechnologyAdapter adapter=new EclipseGlspTechnologyAdapter(TechnologyAdapterTestFixtures.MAPPER);
    @Test void targetsEclipseGlsp(){assertThat(adapter.technology().name()).isEqualTo("ECLIPSE_GLSP");}
    @Test void generatesTypeScriptClientAndJavaServerByDefault(){var paths=paths(adapter.compile(TechnologyAdapterTestFixtures.glspRequest()));assertThat(paths).anyMatch(value->value.endsWith("client/src/hydra-diagram-module.ts")).anyMatch(value->value.endsWith("server/src/main/java/com/generated/people/glsp/HydraDiagramModule.java"));}
    @Test void generatesDescriptorWithCanonicalSourceModel(){assertThat(text(adapter.compile(TechnologyAdapterTestFixtures.glspRequest()),"hydra-glsp.json")).contains("people-editor","HYDRA_CANONICAL_JSON","representation:main");}
    @Test void generatesViewsAndPaletteFromEditorDefinition(){var result=adapter.compile(TechnologyAdapterTestFixtures.glspRequest());assertThat(text(result,"hydra-views.ts")).contains("Person");assertThat(text(result,"hydra-palette.ts")).contains("Person","Employer","Display");}
    @Test void generatesValidationOperationAndEventBoundaries(){var paths=paths(adapter.compile(TechnologyAdapterTestFixtures.glspRequest()));assertThat(paths).anyMatch(value->value.endsWith("HydraValidationBridge.java")).anyMatch(value->value.endsWith("HydraOperationHandler.java")).anyMatch(value->value.endsWith("HydraEventBridge.java"));}
    @Test void embedsAllCanonicalDocuments(){var paths=paths(adapter.compile(TechnologyAdapterTestFixtures.glspRequest()));assertThat(paths).anyMatch(value->value.endsWith("resources/hydra/metamodel.json")).anyMatch(value->value.endsWith("resources/hydra/editor_definition.json"));}
    @Test void declaresClientAndJavaServerDependencies(){assertThat(adapter.compile(TechnologyAdapterTestFixtures.glspRequest()).dependencies()).extracting(value->value.coordinate()).contains("@eclipse-glsp/client","@eclipse-glsp/protocol","org.eclipse.glsp:org.eclipse.glsp.server","com.google.code.gson:gson");}
    @Test void verifiesClientAndServerSeparately(){assertThat(adapter.compile(TechnologyAdapterTestFixtures.glspRequest()).verificationSteps()).hasSize(3).satisfiesExactly(
            install -> assertThat(install.command()).containsExactly("npm","install","--ignore-scripts"),
            client -> assertThat(client.command()).containsExactly("npm","run","check"),
            server -> assertThat(server.command()).containsExactly("mvn","-q","test"));}
    @Test void generationIsDeterministic(){assertThat(adapter.compile(TechnologyAdapterTestFixtures.glspRequest()).files()).usingRecursiveComparison().isEqualTo(adapter.compile(TechnologyAdapterTestFixtures.glspRequest()).files());}
    @Test void honorsConfiguredDiagramTypeAndPackage(){var result=adapter.compile(configured(Map.of("diagramType","people.custom","packageName","@example/people-glsp")));assertThat(result.metadata()).containsEntry("diagramType","people.custom").containsEntry("packageName","@example/people-glsp");assertThat(text(result,"diagram-type.ts")).contains("people.custom");}
    @Test void canGenerateTypeScriptServer(){var result=adapter.compile(configured(Map.of("serverLanguage","typescript")));assertThat(result.metadata()).containsEntry("serverLanguage","typescript");assertThat(paths(result)).anyMatch(value->value.endsWith("server/src/hydra-diagram-module.ts")).noneMatch(value->value.endsWith("HydraDiagramModule.java"));assertThat(result.dependencies()).extracting(value->value.coordinate()).contains("@eclipse-glsp/server","@eclipse-glsp/graph");}
    @Test void rejectsUnknownServerLanguage(){assertThatThrownBy(()->adapter.compile(configured(Map.of("serverLanguage","python")))).hasMessageContaining("java or typescript");}
    @Test void rejectsMissingEditorDefinition(){var source=TechnologyAdapterTestFixtures.glspRequest();var artifacts=source.artifacts().stream().filter(value->value.kind()!=IntegrationArtifactKind.EDITOR_DEFINITION).toList();assertThatThrownBy(()->adapter.compile(new TechnologyCompilationRequest(source.profile(),artifacts,"test",Map.of()))).hasMessageContaining("EDITOR_DEFINITION");}
    @Test void warnsForNonDiagramRepresentations(){var source=TechnologyAdapterTestFixtures.glspRequest();var editor=EditorDefinitionTestFixtures.editor();var table=new RepresentationDefinition("representation:table","Table",RepresentationKind.TABLE,List.of(),List.of(),List.of(),List.of(),true,Map.of());var changed=new EditorDefinitionModel(editor.schemaVersion(),editor.id(),editor.metamodelId(),editor.name(),List.of(editor.representations().getFirst(),table),editor.palette(),editor.forms(),editor.capabilityManifest(),editor.runtimePolicy(),editor.annotations());var artifacts=new ArrayList<>(source.artifacts());artifacts.removeIf(value->value.kind()==IntegrationArtifactKind.EDITOR_DEFINITION);artifacts.add(new CanonicalArtifactContent(IntegrationArtifactKind.EDITOR_DEFINITION,changed.id(),"application/json",TechnologyAdapterTestFixtures.MAPPER.writeValueAsBytes(changed)));assertThat(adapter.compile(new TechnologyCompilationRequest(source.profile(),artifacts,"test",Map.of())).diagnostics()).extracting(value->value.code()).contains("GLSP_NON_DIAGRAM_VIEWPOINT");}
    @Test void documentsDerivedGmodelAndDeployments(){assertThat(text(adapter.compile(TechnologyAdapterTestFixtures.glspRequest()),"README.md")).contains("MetaModel 2.1","standalone","VS Code","Theia","GModels are derived views");}
    @Test void consumesFullTextualStackInClientAndJavaServer(){var result=adapter.compile(TechnologyAdapterTestFixtures.glspFullTextualRequest());var paths=paths(result);assertThat(paths).anyMatch(v->v.endsWith("client/src/hydra-textual-full-stack.ts")).anyMatch(v->v.endsWith("HydraTextualFullStack.java")).anyMatch(v->v.endsWith("hydra/textual-editor-customization.json"));assertThat(text(result,"client/src/hydra-textual-full-stack.ts")).contains("validation:true","quickFixes:true","eventFlows:true","gamification:true","afterCommit");assertThat(text(result,"HydraTextualFullStack.java")).contains("VALIDATION=true","QUICK_FIXES=true","EVENT_FLOWS=true","GAMIFICATION=true","afterCommit");}

    @Test void generatesConcreteGlsp25LifecycleInsteadOfPassiveServerStubs(){
        var result=adapter.compile(TechnologyAdapterTestFixtures.glspRequest());
        assertThat(paths(result)).anyMatch(v->v.endsWith("HydraDiagramConfiguration.java"))
                .anyMatch(v->v.endsWith("HydraGModelState.java"))
                .anyMatch(v->v.endsWith("HydraSemanticRuntime.java"))
                .anyMatch(v->v.endsWith("HydraSemanticRuntimeService.java"));
        assertThat(text(result,"HydraDiagramModule.java")).contains("extends DiagramModule","bindSourceModelStorage","bindGModelFactory","bindGModelState","bindModelValidator").doesNotContain("DefaultDiagramModule");
        assertThat(text(result,"HydraSourceModelStorage.java")).contains("implements SourceModelStorage","ClientOptionsUtil.getSourceUriAsFile","hydraRuntime.synchronize","SOURCE_MODEL_PROPERTY");
        assertThat(text(result,"HydraGModelFactory.java")).contains("implements GModelFactory","GraphFactory.eINSTANCE.createGGraph","JsonParser.parseString","classId","relationId","createGNode","createGEdge","modelState.updateRoot");
        assertThat(text(result,"HydraGModelState.java")).contains("extends DefaultGModelState","checkpoint()","GLSP_OPERATION","GLSP_UNDO","GLSP_REDO","restore","getUndoCommand()==command","hydraRuntime.restore(before)");
        assertThat(text(result,"HydraValidationBridge.java")).contains("implements ModelValidator","validateHost","GLSP_LIVE_VALIDATION","GLSP_BATCH_VALIDATION");
        assertThat(text(result,"HydraRuntimeActivation.java")).contains("AtomicLong EPOCH","epoch()","EPOCH.incrementAndGet");
    }

    @Test void generatesTransactionalLifecycleForTypeScriptServer(){
        var result=adapter.compile(configured(Map.of("serverLanguage","typescript")));
        assertThat(paths(result)).anyMatch(v->v.endsWith("server/src/hydra-semantic-runtime.ts")).anyMatch(v->v.endsWith("server/src/hydra-semantic-runtime-service.ts")).anyMatch(v->v.endsWith("server/src/hydra-diagram-configuration.ts"));
        assertThat(text(result,"server/src/hydra-semantic-runtime.ts")).contains("validateHost","synchronize","checkpointBaseline","restore","hydraRuntimeActivation.epoch()");
        assertThat(text(result,"server/src/hydra-semantic-runtime-service.ts")).contains("class HydraSemanticRuntimeService","synchronize").doesNotContain("export const hydraSemanticRuntimeService");
        assertThat(text(result,"server/src/hydra-runtime-activation.ts")).contains("let epoch=0","epoch:()=>epoch","epoch++");
        assertThat(text(result,"server/src/hydra-diagram-module.ts")).contains("extends DiagramModule","bindSourceModelStorage","bindModelState","bindDiagramConfiguration","bindGModelFactory","configureOperationHandlers","HydraSemanticRuntimeService").doesNotContain("@eclipse-glsp/server-node");
        assertThat(text(result,"server/src/hydra-source-model-storage.ts")).contains("implements SourceModelStorage","ModelState","loadSourceModel","saveSourceModel");
        assertThat(text(result,"server/src/hydra-gmodel-factory.ts")).contains("implements GModelFactory","GGraph.builder()","GNode.builder()","GEdge.builder()","updateRoot");
        assertThat(text(result,"server/src/hydra-operation-handler.ts"))
                .contains("extends OperationHandler","createCommand","undo()","redo()","activationEpoch","rollbackRuntime","currentRuntime","throw error");
        assertThat(text(result,"server/package.json")).contains("@eclipse-glsp/server","@eclipse-glsp/graph","inversify","6.1.3").doesNotContain("server-node");
    }
    @Test void clientUsesTheInversifyLineThatTheGlspClientRequires(){var result=adapter.compile(TechnologyAdapterTestFixtures.glspRequest());
        assertThat(text(result,"client/package.json")).contains("\"inversify\":\"^6.1.3\"").doesNotContain("\"inversify\":\"^7");
        assertThat(text(result,"client/offline/dependency-bundle.json")).contains("inversify@6.1.3").doesNotContain("inversify@7");
        assertThat(text(result,"client/src/hydra-diagram-module.ts")).contains("new ContainerModule((bind,_unbind,isBound)=>","configureDefaultModelElements({bind,isBound})")
                .doesNotContain("TYPES.","({bind,isBound})=>","as never");}
    @Test void typeScriptServerDeclaresNodeTypesForTypeScript6(){var result=adapter.compile(configured(Map.of("serverLanguage","typescript")));
        assertThat(text(result,"server/tsconfig.json")).contains("\"types\":[\"node\"]");assertThat(text(result,"client/tsconfig.json")).doesNotContain("\"types\"");}
    @Test void nodePackagesPinViteSoNpmCanResolveVitest(){var result=adapter.compile(configured(Map.of("serverLanguage","typescript")));
        for(String pkg:List.of("client/package.json","server/package.json"))assertThat(text(result,pkg)).contains("\"vite\":\"8.3.5\"","\"vitest\":\"5.0.5\"").doesNotContain("\"overrides\"","4.1.10","\"vitest\":\"^");}
    @Test void exportsHonestScopedFidelityObligations(){
        var result = adapter.compile(TechnologyAdapterTestFixtures.glspRequest());
        String report = text(result,"hydra-fidelity-obligations.json");
        assertThat(report).contains("\"nativeRuntimeExecuted\":false", "\"functionalParityCertified\":false",
                "GENERATED_DIAGRAM_UNVERIFIED", "\"sourceSha256\"");
        assertThat(report).doesNotContain("CERTIFIED_SCOPED");
    }
    @Test void reportsUnmaterializedRepresentationsInExportedManifest(){
        var source = TechnologyAdapterTestFixtures.glspRequest();
        var editor = EditorDefinitionTestFixtures.editor();
        var table = new RepresentationDefinition("representation:table","Table",RepresentationKind.TABLE,List.of(),List.of(),List.of(),List.of(),true,Map.of());
        var changed = new EditorDefinitionModel(editor.schemaVersion(),editor.id(),editor.metamodelId(),editor.name(),
                List.of(editor.representations().getFirst(),table),editor.palette(),editor.forms(),editor.capabilityManifest(),editor.runtimePolicy(),editor.annotations());
        var artifacts = new ArrayList<>(source.artifacts());
        artifacts.removeIf(value -> value.kind() == IntegrationArtifactKind.EDITOR_DEFINITION);
        artifacts.add(new CanonicalArtifactContent(IntegrationArtifactKind.EDITOR_DEFINITION,changed.id(),"application/json",
                TechnologyAdapterTestFixtures.MAPPER.writeValueAsBytes(changed)));
        String report = text(adapter.compile(new TechnologyCompilationRequest(source.profile(),artifacts,"test",Map.of())),
                "hydra-fidelity-obligations.json");
        assertThat(report).contains("representation:table", "CANONICAL_ONLY");
    }

    private TechnologyCompilationRequest configured(Map<String,String> settings){var source=TechnologyAdapterTestFixtures.glspRequest();var t=source.profile().target();var target=new IntegrationTarget(t.technology(),t.kind(),t.technologyVersion(),t.projectId(),t.moduleName(),t.buildSystem(),t.sourceRoots(),t.configurationFiles(),t.endpoint(),settings);var p=source.profile();var profile=new IntegrationProfileModel(p.schemaVersion(),p.id(),p.metamodelId(),p.name(),target,p.deliveryMode(),p.features(),p.artifacts(),p.mappings(),p.injectionPolicy(),p.requiredCapabilities(),p.annotations());return new TechnologyCompilationRequest(profile,source.artifacts(),"test",Map.of());}
    private List<String> paths(com.example.editor.integration.compiler.TechnologyCompilationResult result){return result.files().stream().map(value->value.relativePath()).toList();}
    @Test void executesNeutralVisualCustomizationInGeneratedGlspClient(){var result=adapter.compile(TechnologyAdapterTestFixtures.glspRequest());assertThat(paths(result)).anyMatch(v->v.endsWith("client/src/hydra-visual-full-stack.ts")).anyMatch(v->v.endsWith("resources/hydra/visual-editor-definition.json")).anyMatch(v->v.endsWith("resources/hydra/visual-full-stack.json"));assertThat(text(result,"hydra-views.ts")).contains("createHydraGlspVisualRuntime","createHydraVisualFullStack");assertThat(text(result,"hydra-client.test.ts")).contains("executes HYDRA conditional visual personalization","resolveNodeAppearance","#fc0","ELLIPSE");assertThat(text(result,"hydra-visual-full-stack.ts")).contains("resolveNodeAppearance","resolveEdgeAppearance","applyModel","maximumConditionalStyles","VISUAL_CONDITION_EVALUATION_FAILED");}

    private String text(com.example.editor.integration.compiler.TechnologyCompilationResult result,String suffix){return result.files().stream().filter(value->value.relativePath().endsWith(suffix)).map(value->new String(value.content(),StandardCharsets.UTF_8)).findFirst().orElseThrow();}
}
