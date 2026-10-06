package com.example.editor.integration.adapter.besser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.editor.integration.IntegrationArtifactKind;
import com.example.editor.integration.IntegrationProfileModel;
import com.example.editor.integration.IntegrationTarget;
import com.example.editor.integration.compiler.TechnologyAdapterTestFixtures;
import com.example.editor.integration.compiler.TechnologyCompilationRequest;
import com.example.editor.integration.projection.CanonicalArtifactContent;

class BesserSdkTechnologyAdapterTests {
    private final BesserSdkTechnologyAdapter adapter=new BesserSdkTechnologyAdapter(TechnologyAdapterTestFixtures.MAPPER);
    @Test void targetsBesserSdk(){assertThat(adapter.technology().name()).isEqualTo("BESSER_SDK");}
    @Test void generatesInstallablePythonPackage(){var paths=paths(adapter.compile(TechnologyAdapterTestFixtures.besserSdkRequest()));assertThat(paths).anyMatch(value->value.endsWith("pyproject.toml")).anyMatch(value->value.endsWith("__init__.py")).anyMatch(value->value.endsWith("semantic_runtime.py")).anyMatch(value->value.endsWith("tests/test_generated_model.py"));}
    @Test void translatesClassesPropertiesAndDomainModel(){assertThat(text(adapter.compile(TechnologyAdapterTestFixtures.besserSdkRequest()),"model.py")).contains("DomainModel","Class(name='Person'","Property(name='name'","hydra_domain_model");}
    @Test void translatesAssociationsAndMultiplicities(){assertThat(text(adapter.compile(TechnologyAdapterTestFixtures.besserSdkRequest()),"model.py")).contains("BinaryAssociation","Multiplicity(0,1)","employer");}
    @Test void generatesRuntimeBridges(){var paths=paths(adapter.compile(TechnologyAdapterTestFixtures.besserSdkRequest()));assertThat(paths).anyMatch(value->value.endsWith("validation.py")).anyMatch(value->value.endsWith("quickfix.py")).anyMatch(value->value.endsWith("operations.py")).anyMatch(value->value.endsWith("events.py")).anyMatch(value->value.endsWith("runtime.py"));}
    @Test void executesTransactionalPythonLifecycleInsteadOfPassiveStubs(){var result=adapter.compile(TechnologyAdapterTestFixtures.besserSdkRequest());assertThat(text(result,"semantic_runtime.py")).contains("class HydraSemanticRuntime","def validate_host","def synchronize","checkpoint_baseline","history_limit");assertThat(text(result,"/runtime.py")).contains("class HydraBesserRuntime","HydraSemanticRuntime(_Engine","BESSER_SDK_DRAFT_VALIDATE","BESSER_SDK_COMMIT","self.lifecycle.restore(before)");assertThat(text(result,"validation.py")).contains("self.runtime.validate");assertThat(text(result,"events.py")).contains("publish_after_commit","self.runtime.commit");}
    @Test void generatedPytestSuiteProtectsTransactionalLifecycle(){String tests=text(adapter.compile(TechnologyAdapterTestFixtures.besserSdkRequest()),"tests/test_generated_model.py");assertThat(tests).contains("test_draft_validation_is_side_effect_free_and_commit_is_confirmed","test_failed_commit_restores_complete_checkpoint","test_reactivation_rebaselines_without_replaying_offline_changes","runtime.checkpoint()==before","runtime_activation.set_enabled(False)");}
    @Test void embedsCanonicalDocumentsInsidePackage(){assertThat(paths(adapter.compile(TechnologyAdapterTestFixtures.besserSdkRequest()))).anyMatch(value->value.contains("/hydra/metamodel.json"));}
    @Test void declaresBesserAndPytestDependencies(){assertThat(adapter.compile(TechnologyAdapterTestFixtures.besserSdkRequest()).dependencies()).extracting(value->value.coordinate()).contains("besser","pytest");}
    @Test void verifiesWithPytest(){assertThat(adapter.compile(TechnologyAdapterTestFixtures.besserSdkRequest()).verificationSteps()).hasSize(2).satisfiesExactly(
            install -> assertThat(install.command()).containsExactly("python","-m","pip","install","-e",".[test]"),
            test -> assertThat(test.command()).containsExactly("python","-m","pytest","-q"));}
    @Test void generationIsDeterministic(){assertThat(adapter.compile(TechnologyAdapterTestFixtures.besserSdkRequest()).files()).usingRecursiveComparison().isEqualTo(adapter.compile(TechnologyAdapterTestFixtures.besserSdkRequest()).files());}
    @Test void honorsConfiguredPythonModule(){var result=adapter.compile(configured("org_people_model"));assertThat(result.metadata()).containsEntry("pythonModule","org_people_model");assertThat(paths(result)).anyMatch(value->value.contains("src/org_people_model/model.py"));}
    @Test void rejectsMissingMetamodel(){var source=TechnologyAdapterTestFixtures.besserSdkRequest();assertThatThrownBy(()->adapter.compile(new TechnologyCompilationRequest(source.profile(),List.of(),"test",Map.of()))).hasMessageContaining("METAMODEL");}
    @Test void supportsEditorDefinitionAsPortableVisualBridge(){var result=adapter.compile(TechnologyAdapterTestFixtures.besserSdkVisualRequest());assertThat(result.diagnostics()).extracting(value->value.code()).doesNotContain("UNSUPPORTED_ARTIFACT");assertThat(paths(result)).anyMatch(v->v.endsWith("visual_editor.py")).anyMatch(v->v.endsWith("hydra/visual-editor-definition.json"));}
    @Test void documentsBumlAsProjectionAndUiSeparation(){assertThat(text(adapter.compile(TechnologyAdapterTestFixtures.besserSdkRequest()),"README.md")).contains("MetaModel 2.1","B-UML","does not replace the HYDRA source of truth","separate BESSER Web Modeling Editor adapter");}
    @Test void consumesFullTextualStackIncludingCustomizationAndNeutralLayers(){var result=adapter.compile(TechnologyAdapterTestFixtures.besserSdkFullTextualRequest());var paths=paths(result);assertThat(paths).anyMatch(v->v.endsWith("textual_editor.py")).anyMatch(v->v.endsWith("hydra/textual-editor-customization.json")).anyMatch(v->v.endsWith("hydra/textual-full-stack.json"));assertThat(text(result,"textual_editor.py")).contains("CUSTOMIZATION","validation':True","quick_fixes':True","events':True","event_flows':True","gamification':True","after_commit");assertThat(text(result,"textual-full-stack.json")).contains("\"validation\": true","\"quickFixes\": true","\"eventFlows\": true","\"gamification\": true");}
    private TechnologyCompilationRequest configured(String module){var source=TechnologyAdapterTestFixtures.besserSdkRequest();var t=source.profile().target();var target=new IntegrationTarget(t.technology(),t.kind(),t.technologyVersion(),t.projectId(),t.moduleName(),t.buildSystem(),t.sourceRoots(),t.configurationFiles(),t.endpoint(),Map.of("pythonModule",module));var p=source.profile();var profile=new IntegrationProfileModel(p.schemaVersion(),p.id(),p.metamodelId(),p.name(),target,p.deliveryMode(),p.features(),p.artifacts(),p.mappings(),p.injectionPolicy(),p.requiredCapabilities(),p.annotations());return new TechnologyCompilationRequest(profile,source.artifacts(),"test",Map.of());}
    private List<String> paths(com.example.editor.integration.compiler.TechnologyCompilationResult result){return result.files().stream().map(value->value.relativePath()).toList();}
    @Test void executesNeutralVisualCustomizationInGeneratedBesserSdkPackage(){var result=adapter.compile(TechnologyAdapterTestFixtures.besserSdkVisualRequest());assertThat(text(result,"visual_editor.py")).contains("resolve_node_appearance","resolve_edge_appearance","apply_model","apply_node_appearance","maximum_conditional_styles","VISUAL_CONDITION_EVALUATION_FAILED");assertThat(text(result,"test_generated_model.py")).contains("test_visual_customization_executes_in_besser_sdk_bridge","resolve_node_appearance","#fc0","ELLIPSE");}

    private String text(com.example.editor.integration.compiler.TechnologyCompilationResult result,String suffix){return result.files().stream().filter(value->value.relativePath().endsWith(suffix)).map(value->new String(value.content(),StandardCharsets.UTF_8)).findFirst().orElseThrow();}
}
