package com.example.editor.integration.adapter.langium;

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

class LangiumTechnologyAdapterTests {
    private final LangiumTechnologyAdapter adapter=new LangiumTechnologyAdapter(TechnologyAdapterTestFixtures.MAPPER);
    @Test void targetsLangium(){assertThat(adapter.technology().name()).isEqualTo("LANGIUM");}
    @Test void generatesNpmAndLangiumConfiguration(){var paths=paths(adapter.compile(TechnologyAdapterTestFixtures.langiumRequest()));assertThat(paths).anyMatch(value->value.endsWith("package.json")).anyMatch(value->value.endsWith("langium-config.json")).anyMatch(value->value.endsWith("tsconfig.json"));}
    @Test void generatesGrammarFromCanonicalMetamodel(){assertThat(text(adapter.compile(TechnologyAdapterTestFixtures.langiumRequest()),".langium")).contains("grammar People","entry Model","Person","Company","employer=[Company:ID]");}
    @Test void generatesValidationRegistry(){assertThat(text(adapter.compile(TechnologyAdapterTestFixtures.langiumRequest()),"-validator.ts")).contains("ValidationChecks","ValidationRegistry.register","checkCanonicalModel");}
    @Test void generatesCodeActionsOperationsAndEvents(){var result=adapter.compile(TechnologyAdapterTestFixtures.langiumRequest());assertThat(text(result,"hydra-code-action-provider.ts")).contains("Apply HYDRA quick fix");assertThat(text(result,"hydra-operation-service.ts")).contains("invoke");assertThat(text(result,"hydra-event-bridge.ts")).contains("publishAfterCommit");}
    @Test void generatesTransactionalDocumentLifecycle(){var result=adapter.compile(TechnologyAdapterTestFixtures.langiumRequest());assertThat(paths(result)).anyMatch(value->value.endsWith("hydra-semantic-runtime.ts")).anyMatch(value->value.endsWith("hydra-language-runtime.ts")).anyMatch(value->value.endsWith("hydra-document-update-handler.ts"));String handler=text(result,"hydra-document-update-handler.ts");assertThat(handler).contains("didChangeContent","LANGIUM_DRAFT_VALIDATE","didSaveDocument","LANGIUM_DID_SAVE","didCloseDocument");String runtime=text(result,"hydra-language-runtime.ts");assertThat(runtime).contains("new Map<string,Session>()","runtime.restore(before)","LANGIUM_QUICK_FIX","LANGIUM_OPERATION","outcome.state","await adapter.applyQuickFix");assertThat(text(result,"-module.ts")).contains("HydraDocumentUpdateHandler","DocumentUpdateHandler");}
    @Test void generatedBridgesAreExecutableClasses(){var result=adapter.compile(TechnologyAdapterTestFixtures.langiumRequest());assertThat(text(result,"hydra-operation-service.ts")).contains("class HydraOperationService").doesNotContain("interface HydraOperationService");assertThat(text(result,"hydra-event-bridge.ts")).contains("class HydraEventBridge").doesNotContain("interface HydraEventBridge");}
    @Test void generatesLanguageServerAndExtensionEntrypoints(){var paths=paths(adapter.compile(TechnologyAdapterTestFixtures.langiumRequest()));assertThat(paths).anyMatch(value->value.endsWith("src/main.ts")).anyMatch(value->value.endsWith("src/extension.ts"));}
    @Test void embedsCanonicalDocuments(){assertThat(paths(adapter.compile(TechnologyAdapterTestFixtures.langiumRequest()))).anyMatch(value->value.endsWith("resources/hydra/metamodel.json"));}
    @Test void declaresNativeDependencies(){assertThat(adapter.compile(TechnologyAdapterTestFixtures.langiumRequest()).dependencies()).extracting(value->value.coordinate()).contains("langium","vscode-languageserver","typescript");}
    @Test void verifiesWithNpmCheck(){assertThat(adapter.compile(TechnologyAdapterTestFixtures.langiumRequest()).verificationSteps()).hasSize(2).satisfiesExactly(
            install -> assertThat(install.command()).containsExactly("npm","install","--ignore-scripts"),
            check -> assertThat(check.command()).containsExactly("npm","run","check"));}
    @Test void generationIsDeterministic(){assertThat(adapter.compile(TechnologyAdapterTestFixtures.langiumRequest()).files()).usingRecursiveComparison().isEqualTo(adapter.compile(TechnologyAdapterTestFixtures.langiumRequest()).files());}
    @Test void honorsPackageLanguageAndExtension(){var result=adapter.compile(configured());assertThat(result.metadata()).containsEntry("packageName","@example/people-language").containsEntry("languageName","PeopleLanguage").containsEntry("fileExtension","ppl");assertThat(text(result,"package.json")).contains(".ppl");}
    @Test void compilesGrammarFirstWithoutMetamodel(){var result=adapter.compile(TechnologyAdapterTestFixtures.langiumGrammarFirstRequest());assertThat(text(result,".langium")).contains("grammar Main","Element: 'element' name=ID");assertThat(text(result,"runtime-activation.json")).contains("\"semanticScopeId\": \"grammar.main-language\"");assertThat(text(result,"README.md")).contains("Grammar-first");}
    @Test void rejectsMissingMetamodel(){var source=TechnologyAdapterTestFixtures.langiumRequest();assertThatThrownBy(()->adapter.compile(new TechnologyCompilationRequest(source.profile(),List.of(),"test",Map.of()))).hasMessageContaining("METAMODEL");}
    @Test void reportsUnsupportedEditorDefinition(){var source=TechnologyAdapterTestFixtures.langiumRequest();var artifacts=new ArrayList<>(source.artifacts());artifacts.add(new CanonicalArtifactContent(IntegrationArtifactKind.EDITOR_DEFINITION,"people-editor","application/json","{}".getBytes(StandardCharsets.UTF_8)));assertThat(adapter.compile(new TechnologyCompilationRequest(source.profile(),artifacts,"test",Map.of())).diagnostics()).extracting(value->value.code()).contains("UNSUPPORTED_ARTIFACT");}
    @Test void documentsCanonicalSourceOfTruth(){assertThat(text(adapter.compile(TechnologyAdapterTestFixtures.langiumRequest()),"README.md")).contains("MetaModel 2.1","do not replace the HYDRA metamodel");}

    @Test void contributesCustomizedTextMateGrammarOnlyWhenGrammarIsSelected(){
        var customized=adapter.compile(TechnologyAdapterTestFixtures.langiumCustomizedRequest());
        assertThat(paths(customized)).anyMatch(value->value.endsWith("syntaxes/hydra-custom.tmLanguage.json")).anyMatch(value->value.endsWith("hydra/textual-editor-customization.json"));
        assertThat(text(customized,"package.json")).contains("./syntaxes/hydra-custom.tmLanguage.json");
        assertThat(text(customized,"hydra-custom.tmLanguage.json")).contains("person","company","keyword.control.hydra");
        var plain=adapter.compile(TechnologyAdapterTestFixtures.langiumRequest());
        assertThat(paths(plain)).noneMatch(value->value.endsWith("syntaxes/hydra-custom.tmLanguage.json"));
        assertThat(text(plain,"package.json")).doesNotContain("hydra-custom.tmLanguage.json");
    }
    @Test void pinsAConsistentLangium4ToolchainThatNpmCanResolve(){
        String pkg=text(adapter.compile(TechnologyAdapterTestFixtures.langiumRequest()),"package.json");
        assertThat(pkg).contains("\"langium\": \"4.4.0\"","\"langium-cli\": \"4.4.0\"","\"vscode-languageserver\": \"10.1.2\"","\"vscode-languageserver-textdocument\": \"1.0.16\"",
                "\"typescript\": \"6.0.2\"","\"vitest\": \"5.0.5\"").doesNotContain("\"^","4.1.10","\"overrides\"");
    }
    @Test void keepsTheLanguageServerLineOfEarlierLangium4Releases(){
        var result=adapter.compile(configured(Map.of("langiumVersion","4.3.0")));
        assertThat(text(result,"package.json")).contains("\"langium\": \"4.3.0\"","\"langium-cli\": \"4.3.0\"","\"vscode-languageserver\": \"10.0.1\"");
    }
    @Test void keepsTheLanguageServerLineOfLangium3(){
        var result=adapter.compile(configured(Map.of("langiumVersion","3.3.1")));
        assertThat(text(result,"package.json")).contains("\"langium\": \"3.3.1\"","\"vscode-languageserver\": \"9.0.1\"");
        assertThat(text(result,"src/main.ts")).contains("from 'vscode-languageserver/node.js';");
    }
    @Test void importsLangiumServicesFromTheirEntryPoints(){
        var result=adapter.compile(TechnologyAdapterTestFixtures.langiumRequest());
        assertThat(text(result,"-module.ts")).contains("import { inject, type Module } from 'langium';","type LangiumServices, type LangiumSharedServices, type PartialLangiumServices } from 'langium/lsp';",
                "createPeopleServices(context:DefaultSharedModuleContext):{shared:LangiumSharedServices;People:PeopleServices}").doesNotContain("inject } from 'langium/lsp'","context:any");
        assertThat(text(result,"src/main.ts")).contains("from 'vscode-languageserver/node';").doesNotContain("node.js");
    }
    private TechnologyCompilationRequest configured(){return configured(Map.of("packageName","@example/people-language","languageName","PeopleLanguage","fileExtension","ppl"));}
    private TechnologyCompilationRequest configured(Map<String,String> settings){var source=TechnologyAdapterTestFixtures.langiumRequest();var t=source.profile().target();var target=new IntegrationTarget(t.technology(),t.kind(),t.technologyVersion(),t.projectId(),t.moduleName(),t.buildSystem(),t.sourceRoots(),t.configurationFiles(),t.endpoint(),settings);var p=source.profile();var profile=new IntegrationProfileModel(p.schemaVersion(),p.id(),p.metamodelId(),p.name(),target,p.deliveryMode(),p.features(),p.artifacts(),p.mappings(),p.injectionPolicy(),p.requiredCapabilities(),p.annotations());return new TechnologyCompilationRequest(profile,source.artifacts(),"test",Map.of());}
    private List<String> paths(com.example.editor.integration.compiler.TechnologyCompilationResult result){return result.files().stream().map(value->value.relativePath()).toList();}
    private String text(com.example.editor.integration.compiler.TechnologyCompilationResult result,String suffix){return result.files().stream().filter(value->value.relativePath().endsWith(suffix)).map(value->new String(value.content(),StandardCharsets.UTF_8)).findFirst().orElseThrow();}
}
