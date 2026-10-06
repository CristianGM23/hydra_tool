package com.example.editor.integration.adapter.vscode;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import com.example.editor.integration.compiler.TechnologyAdapterTestFixtures;
import com.example.editor.integration.compiler.TechnologyCompilationResult;

class VsCodeExtensionTechnologyAdapterTests {
    private final VsCodeExtensionTechnologyAdapter adapter = new VsCodeExtensionTechnologyAdapter(TechnologyAdapterTestFixtures.MAPPER);

    @Test void generatesInstallableModelingExtensionWithHostBridges(){
        var result=adapter.compile(TechnologyAdapterTestFixtures.vscodeExtensionRequest());
        assertThat(paths(result)).anyMatch(p->p.endsWith("package.json")).anyMatch(p->p.endsWith("src/extension.ts"))
                .anyMatch(p->p.endsWith("src/languageBridge.ts")).anyMatch(p->p.endsWith("src/portableRuntimeBridge.ts"))
                .anyMatch(p->p.endsWith("src/hydra-semantic-runtime.ts")).anyMatch(p->p.endsWith("src/runtimeSessions.ts"))
                .anyMatch(p->p.endsWith("tests/runtime.test.ts")).anyMatch(p->p.endsWith("resources/hydra-host-bridges.json"));
        assertThat(text(result,"package.json")).contains("@eclipse-glsp/vscode-integration","langium","vscode-languageclient","runtime.toggle","customEditors","vitest run","npm run check && npm test && npm run bundle");
        assertThat(text(result,"hydra-host-bridges.json")).contains("langium","eclipse-glsp","xtext","hydra-canonical-runtime");
        assertThat(text(result,"package.json")).contains("onCustomEditor:hydra.people.modelEditor","workspaceContains:**/*.hydra")
                .doesNotContain("onStartupFinished");
    }

    @Test void neverStartsXtextWhileHydraRuntimeIsDisabled(){
        String extension=text(adapter.compile(TechnologyAdapterTestFixtures.vscodeExtensionRequest()),"src/extension.ts");
        assertThat(extension).contains("if(activation.isEnabled())await languages.startXtext()","if(value){await languages.startXtext();}else{await languages.stop();}")
                .doesNotContain("refresh(); await languages.startXtext();");
    }

    @Test void persistsActivationPerWorkspaceAndSemanticScope(){
        var result=adapter.compile(TechnologyAdapterTestFixtures.vscodeExtensionRequest());
        String activation=text(result,"src/runtimeActivation.ts");
        assertThat(activation).contains("workspaceState","HYDRA_SEMANTIC_SCOPE_ID","people","get<boolean>(key,false)","hydraRuntimeActivation.setEnabled");
        assertThat(text(result,"src/hydra-runtime-activation.ts")).contains("HYDRA_SEMANTIC_SCOPE_ID","epoch=0","setEnabled");
    }

    @Test void validatesDraftsButCommitsOnlySavedDocuments(){
        var result=adapter.compile(TechnologyAdapterTestFixtures.vscodeExtensionRequest());
        String sessions=text(result,"src/runtimeSessions.ts");
        String extension=text(result,"src/extension.ts");
        assertThat(sessions).contains("VSCODE_DRAFT_VALIDATE","VSCODE_SAVE","checkpoint()","activationEpoch===hydraRuntimeActivation.epoch()")
                .contains("snapshots:Map<string,HydraRuntimeSnapshot>","while(session.order.length>32)");
        assertThat(extension).contains("onDidChangeTextDocument(event=>sessions.validate(event.document))","onDidSaveTextDocument(document=>sessions.commit(document))")
                .contains("sessions.rebaselineOpenDocuments()");
        assertThat(text(result,"src/portableRuntimeBridge.ts")).contains("class HydraPortableRuntimeBridge","new HydraSemanticRuntime(engine)","neutralEvents","runtime.restore(before)");
    }

    @Test void generatedVitestSuiteProtectsPortableLifecycle(){String tests=text(adapter.compile(TechnologyAdapterTestFixtures.vscodeExtensionRequest()),"tests/runtime.test.ts");assertThat(tests).contains("keeps draft validation side-effect free and commits only saved states","restores the complete checkpoint after a failed confirmed capability","rebaselines after OFF changes without replaying events","runtime.checkpoint()).toEqual(before)","hydraRuntimeActivation.setEnabled(false)");}

    @Test void keepsGenerationDeterministic(){assertThat(adapter.compile(TechnologyAdapterTestFixtures.vscodeExtensionRequest()).files()).usingRecursiveComparison().isEqualTo(adapter.compile(TechnologyAdapterTestFixtures.vscodeExtensionRequest()).files());}


    @Test void consumesNeutralTextualCustomizationInVsCodeRuntime(){
        var result=adapter.compile(TechnologyAdapterTestFixtures.vscodeCustomizedRequest());
        assertThat(paths(result)).anyMatch(p->p.endsWith("src/hydraTextualCustomization.ts")).anyMatch(p->p.endsWith("hydra/textual-editor-customization.json"));
        assertThat(text(result,"package.json")).contains("editor.fontFamily","Consolas, monospace","editor.fontSize",":17","editor.lineHeight",":25");
        assertThat(text(result,"src/hydraTextualCustomization.ts")).contains("#123456|1|0","Persona","SYMBOL_STYLES","RULE:Person","TERMINAL:ID","symbolDecorations","MAX_DECORATIONS=50000","registerHoverProvider","setTimeout(()=>refreshNow(editor),120)");
        assertThat(text(result,"hydra/textual-editor-customization.json")).contains("symbolStyles","RULE:Person","TERMINAL:ID");
    }

    @Test void acceptsGrammarAsAutonomousSemanticRoot(){
        var request=TechnologyAdapterTestFixtures.vscodeGrammarFirstRequest();
        assertThat(adapter.semanticRootKinds()).contains(com.example.editor.integration.IntegrationArtifactKind.GRAMMAR);
        var result=adapter.compile(request);
        assertThat(paths(result)).anyMatch(p->p.endsWith("src/extension.ts")).anyMatch(p->p.endsWith("hydra/grammar.json"));
        assertThat(text(result,"src/hydra-runtime-activation.ts")).contains("grammar.main-language");
        assertThat(text(result,"README.md")).contains("semantic context `grammar.main-language`");
    }
    private java.util.List<String> paths(TechnologyCompilationResult result){return result.files().stream().map(f->f.relativePath()).toList();}
    @Test void consumesNeutralVisualCustomizationInVsCodeCustomEditor(){var result=adapter.compile(TechnologyAdapterTestFixtures.vscodeExtensionRequest());assertThat(paths(result)).anyMatch(p->p.endsWith("src/hydraVisualFullStack.ts")).anyMatch(p->p.endsWith("resources/hydra/visual-editor-definition.json")).anyMatch(p->p.endsWith("resources/hydra/visual-full-stack.json"));assertThat(text(result,"src/modelEditorProvider.ts")).contains("createHydraVisualFullStack","resolveNodeAppearance","enableScripts:false","maximumElements:2000");assertThat(text(result,"src/hydraVisualFullStack.ts")).contains("conditionalStyles","resolveNodeAppearance","resolveEdgeAppearance","applyModel","VISUAL_CONDITION_EVALUATION_FAILED");}

    private String text(TechnologyCompilationResult result,String suffix){return result.files().stream().filter(f->f.relativePath().endsWith(suffix)).map(f->new String(f.content(),StandardCharsets.UTF_8)).findFirst().orElseThrow();}
}
