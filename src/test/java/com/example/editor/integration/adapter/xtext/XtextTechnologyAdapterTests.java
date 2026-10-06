package com.example.editor.integration.adapter.xtext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.editor.integration.IntegrationArtifactKind;
import com.example.editor.integration.IntegrationTarget;
import com.example.editor.integration.IntegrationProfileModel;
import com.example.editor.integration.compiler.TechnologyAdapterTestFixtures;
import com.example.editor.integration.compiler.TechnologyCompilationRequest;
import com.example.editor.integration.projection.CanonicalArtifactContent;

class XtextTechnologyAdapterTests {
    private final XtextTechnologyAdapter adapter=new XtextTechnologyAdapter(TechnologyAdapterTestFixtures.MAPPER);
    @Test void targetsXtext(){assertThat(adapter.technology().name()).isEqualTo("XTEXT");}
    @Test void generatesRuntimeIdeAndUiReactor(){var paths=paths(adapter.compile(TechnologyAdapterTestFixtures.xtextRequest()));assertThat(paths).anyMatch(value->value.endsWith("pom.xml")).anyMatch(value->value.contains(".ide/pom.xml")).anyMatch(value->value.contains(".ui/pom.xml"));}
    @Test void generatesGrammarFromCanonicalClassesAndRelations(){String grammar=text(adapter.compile(TechnologyAdapterTestFixtures.xtextRequest()),".xtext");assertThat(grammar).contains("grammar","generate","Person","Company","employer=[Company|ID]");}
    @Test void generatesValidationAndQuickFixBoundaries(){var paths=paths(adapter.compile(TechnologyAdapterTestFixtures.xtextRequest()));assertThat(paths).anyMatch(value->value.endsWith("Validator.java")).anyMatch(value->value.endsWith("QuickfixProvider.java")).anyMatch(value->value.endsWith("HydraIssueCodes.java"));}
    @Test void generatesOperationAndConfirmedEventBridges(){var result=adapter.compile(TechnologyAdapterTestFixtures.xtextRequest());assertThat(text(result,"HydraOperationBridge.java")).contains("invoke");assertThat(text(result,"HydraEventBridge.java")).contains("publishAfterCommit");}
    @Test void generatesTransactionalLanguageHostRuntime(){var result=adapter.compile(TechnologyAdapterTestFixtures.xtextRequest());assertThat(paths(result)).anyMatch(value->value.endsWith("HydraSemanticRuntime.java")).anyMatch(value->value.endsWith("HydraXtextRuntime.java")).anyMatch(value->value.endsWith("HydraLanguageServer.java"));String runtime=text(result,"HydraXtextRuntime.java");assertThat(runtime).contains("ChangeRecorder","ChangeDescription","changes.apply()","lifecycle.restore(before)","XTEXT_QUICK_FIX","XTEXT_OPERATION","validateHost");String server=text(result,"HydraLanguageServer.java");assertThat(server).contains("didSave","SaveOptions","XTEXT_DID_SAVE","HydraXtextRuntime.synchronize","didClose");assertThat(text(result,"QuickfixProvider.java")).contains("HydraXtextRuntime.applyQuickFix").doesNotContain("issue -> {} ");}
    @Test void generatedBridgesAreExecutableClasses(){var result=adapter.compile(TechnologyAdapterTestFixtures.xtextRequest());assertThat(text(result,"HydraOperationBridge.java")).contains("class HydraOperationBridge").doesNotContain("interface HydraOperationBridge");assertThat(text(result,"HydraEventBridge.java")).contains("class HydraEventBridge").doesNotContain("interface HydraEventBridge");assertThat(text(result,"Validator.java")).contains("HydraXtextRuntime.validateHost");}
    @Test void embedsCanonicalDocuments(){assertThat(paths(adapter.compile(TechnologyAdapterTestFixtures.xtextRequest()))).anyMatch(value->value.endsWith("src/main/resources/hydra/metamodel.json"));}
    @Test void declaresXtextDependencies(){assertThat(adapter.compile(TechnologyAdapterTestFixtures.xtextRequest()).dependencies()).extracting(value->value.coordinate()).contains("org.eclipse.xtext:org.eclipse.xtext","org.eclipse.xtext:org.eclipse.xtext.ide","org.eclipse.xtext:org.eclipse.xtext.ui","org.eclipse.emf:org.eclipse.emf.ecore.change");}
    @Test void verifiesWithMaven(){assertThat(adapter.compile(TechnologyAdapterTestFixtures.xtextRequest()).verificationSteps()).singleElement().satisfies(value->assertThat(value.command()).containsExactly("mvn","-q","verify"));}
    @Test void generationIsDeterministic(){assertThat(adapter.compile(TechnologyAdapterTestFixtures.xtextRequest()).files()).usingRecursiveComparison().isEqualTo(adapter.compile(TechnologyAdapterTestFixtures.xtextRequest()).files());}
    @Test void honorsConfiguredLanguageAndPackage(){var result=adapter.compile(configured("org.example.people.dsl","PeopleLanguage"));assertThat(result.metadata()).containsEntry("languagePackage","org.example.people.dsl.peoplelanguage").containsEntry("languageName","PeopleLanguage");assertThat(paths(result)).anyMatch(value->value.contains("org/example/people/dsl/peoplelanguage"));}
    @Test void compilesGrammarFirstWithoutMetamodel(){var result=adapter.compile(TechnologyAdapterTestFixtures.xtextGrammarFirstRequest());assertThat(text(result,".xtext")).contains("grammar org.example.Main","Model: elements+=Element*");assertThat(text(result,"runtime-activation.json")).contains("\"semanticScopeId\": \"grammar.main-language\"");assertThat(text(result,"Validator.java")).doesNotContain("MODEL__ELEMENTS");}
    @Test void rejectsMissingMetamodel(){var source=TechnologyAdapterTestFixtures.xtextRequest();assertThatThrownBy(()->adapter.compile(new TechnologyCompilationRequest(source.profile(),List.of(),"test",Map.of()))).hasMessageContaining("METAMODEL");}
    @Test void reportsUnsupportedEditorDefinition(){var source=TechnologyAdapterTestFixtures.xtextRequest();var artifacts=new ArrayList<>(source.artifacts());artifacts.add(new CanonicalArtifactContent(IntegrationArtifactKind.EDITOR_DEFINITION,"people-editor","application/json","{}".getBytes(StandardCharsets.UTF_8)));assertThat(adapter.compile(new TechnologyCompilationRequest(source.profile(),artifacts,"test",Map.of())).diagnostics()).extracting(value->value.code()).contains("UNSUPPORTED_ARTIFACT");}
    @Test void documentsCanonicalSourceOfTruth(){assertThat(text(adapter.compile(TechnologyAdapterTestFixtures.xtextRequest()),"README.md")).contains("MetaModel 2.1","does not make Xtext or Ecore the HYDRA source of truth");}

    @Test void consumesNeutralTextualCustomizationInXtextUi(){
        var result=adapter.compile(TechnologyAdapterTestFixtures.xtextCustomizedRequest());
        assertThat(paths(result)).anyMatch(value->value.endsWith("HydraHighlightingConfiguration.java")).anyMatch(value->value.endsWith("HydraHoverProvider.java")).anyMatch(value->value.endsWith("hydra/textual-editor-customization.json"));
        assertThat(text(result,"HydraHighlightingConfiguration.java")).contains("new FontData(FONT,SIZE,swt)","private static final String FONT=\"Consolas\"","private static final int SIZE=17","new RGB(18,52,86)","new RGB(248,250,252)");
        assertThat(text(result,"HydraHoverProvider.java")).contains("\"Person\".equals(type)","Persona","Elemento persona del lenguaje.");
        String languageName=result.metadata().get("languageName");
        assertThat(text(result,languageName+"UiModule.java")).contains("HydraHighlightingConfiguration.class","HydraHoverProvider.class");
    }
    @Test void textualUiModuleFollowsConfiguredLanguageName(){
        var result=adapter.compile(TechnologyAdapterTestFixtures.withTextualCustomization(configured("org.example.people.dsl","PeopleLanguage")));
        assertThat(paths(result)).anyMatch(value->value.endsWith("PeopleLanguageUiModule.java"));
        assertThat(text(result,"PeopleLanguageUiModule.java")).contains("class PeopleLanguageUiModule extends AbstractPeopleLanguageUiModule","HydraHighlightingConfiguration.class","HydraHoverProvider.class");
    }
    private TechnologyCompilationRequest configured(String basePackage,String languageName){var source=TechnologyAdapterTestFixtures.xtextRequest();var t=source.profile().target();var target=new IntegrationTarget(t.technology(),t.kind(),t.technologyVersion(),t.projectId(),t.moduleName(),t.buildSystem(),t.sourceRoots(),t.configurationFiles(),t.endpoint(),Map.of("basePackage",basePackage,"languageName",languageName));var p=source.profile();var profile=new IntegrationProfileModel(p.schemaVersion(),p.id(),p.metamodelId(),p.name(),target,p.deliveryMode(),p.features(),p.artifacts(),p.mappings(),p.injectionPolicy(),p.requiredCapabilities(),p.annotations());return new TechnologyCompilationRequest(profile,source.artifacts(),"test",Map.of());}
    private List<String> paths(com.example.editor.integration.compiler.TechnologyCompilationResult result){return result.files().stream().map(value->value.relativePath()).toList();}
    private String text(com.example.editor.integration.compiler.TechnologyCompilationResult result,String suffix){return result.files().stream().filter(value->value.relativePath().endsWith(suffix)).map(value->new String(value.content(),StandardCharsets.UTF_8)).findFirst().orElseThrow(()->new AssertionError("Missing generated file ending with '"+suffix+"'. Generated files: "+paths(result)));}
}
