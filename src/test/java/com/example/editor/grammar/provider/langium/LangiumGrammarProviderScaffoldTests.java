package com.example.editor.grammar.provider.langium;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.editor.grammar.GrammarProperties;
import com.example.editor.grammar.application.GrammarToolchainProfileService;
import com.example.editor.grammar.core.*;

/** Offline guard for the Langium scaffold that native validation builds with npm install, langium generate, tsc and esbuild. */
class LangiumGrammarProviderScaffoldTests {
    private static final String SOURCE = "grammar Stable\nentry Model: nodes+=Node*;\nNode: 'node' name=ID;\nhidden terminal WS: /\\s+/;\nterminal ID: /[_a-zA-Z][\\w_]*/;\n";
    private final LangiumGrammarProvider provider = new LangiumGrammarProvider(new GrammarProperties());
    private final GrammarToolchainProfileService profiles = new GrammarToolchainProfileService();

    @Test void configDeclaresTheOutputDirectoryThatTheServiceModuleImports(){
        var files=generate("langium-4.4-browser").files();
        assertThat(files.get("langium-config.json")).contains("\"out\": \"src/generated\"");
        assertThat(files.get("src/language/stable-module.ts")).contains("from '../generated/module.js';");
    }
    @Test void importsLanguageServerServicesFromLangiumLsp(){
        var files=generate("langium-4.4-browser").files();
        assertThat(files.get("src/language/stable-module.ts")).contains("import { inject, type Module } from 'langium';",
                "type LangiumSharedServices, type PartialLangiumServices } from 'langium/lsp';");
        assertThat(files.get("src/language/main.ts")).contains("import { startLanguageServer } from 'langium/lsp';", "from 'vscode-languageserver/node';");
        assertThat(files.get("src/language/browser-worker.ts")).contains("import { EmptyFileSystem } from 'langium';",
                "import { startLanguageServer } from 'langium/lsp';", "from 'vscode-languageserver/browser';");
        assertThat(files.get("src/cli/main.ts")).contains("const document = await shared.workspace.LangiumDocuments.getOrCreateDocument(");
    }
    @Test void namesTheLanguageModuleAfterTheDeclaredGrammarAndTheSharedModuleAfterTheProject(){
        var files=generate("langium-4.4-browser","t2m-saved-items",SOURCE.replace("grammar Stable","grammar Other")).files();
        assertThat(files.get("src/language/t2msaveditems-module.ts")).contains("import { OtherGeneratedModule, T2mSavedItemsGeneratedSharedModule } from '../generated/module.js';",
                "inject(createDefaultModule({ shared }), OtherGeneratedModule, T2mSavedItemsModule)").doesNotContain("T2mSavedItemsGeneratedModule");
        assertThat(files.get("test/generated-hosts.test.ts")).contains("toContain('OtherGeneratedModule').toContain('T2mSavedItemsGeneratedSharedModule')");
    }
    @Test void pinsTheLanguageServerLineOfEachLangiumProfile(){
        assertThat(generate("langium-4.4-browser").files().get("package.json")).contains("\"langium\": \"4.4.0\"", "\"langium-cli\": \"4.4.0\"",
                "\"vscode-languageserver\": \"10.1.2\"");
        assertThat(generate("langium-4.3-browser").files().get("package.json")).contains("\"langium\": \"4.3.0\"", "\"langium-cli\": \"4.3.0\"",
                "\"vscode-languageserver\": \"10.0.1\"");
    }
    private GeneratedGrammarProject generate(String profileId){return generate(profileId,"stable",SOURCE);}
    private GeneratedGrammarProject generate(String profileId,String id,String source){
        GrammarSourceDocument document=new GrammarSourceDocument("main","main.langium","langium",source,"",true,false);
        GrammarDefinitionModel grammar=new GrammarDefinitionModel("1.0",id,id,GrammarTechnology.LANGIUM,GrammarAuthoringMode.GRAMMAR_FIRST,"",profileId,"main","Model",
                List.of(document),GrammarSemanticBindingMap.empty(),"",Map.of(),Map.of());
        return provider.generateProject(new GrammarProjectGenerationRequest(grammar,profiles.require(profileId,GrammarTechnology.LANGIUM),List.of(document),Map.of()));
    }
}
