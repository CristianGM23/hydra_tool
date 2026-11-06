package com.example.editor.grammar.provider.langium;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import com.example.editor.grammar.GrammarProperties;
import com.example.editor.grammar.application.GrammarToolchainProfileService;
import com.example.editor.grammar.core.*;

/** Keeps the Node.js requirement each Langium profile declares in line with what its generated project really runs. */
class LangiumToolchainProfileNodeRequirementTests {
    private static final String SOURCE = "grammar Stable\nentry Model: nodes+=Node*;\nNode: 'node' name=ID;\nhidden terminal WS: /\\s+/;\nterminal ID: /[_a-zA-Z][\\w_]*/;\n";
    private static final Pattern VITEST = Pattern.compile("\"vitest\": \"(\\d+)\\.\\d+\\.\\d+\"");
    private final LangiumGrammarProvider provider = new LangiumGrammarProvider(new GrammarProperties());
    private final GrammarToolchainProfileService profiles = new GrammarToolchainProfileService();

    @Test void everyLangiumProfileDeclaresTheNodeMinimumOfTheVitestItsProjectPins(){
        List<GrammarToolchainProfile> langium=profiles.compatible(GrammarTechnology.LANGIUM);
        assertThat(langium).isNotEmpty();
        for(GrammarToolchainProfile profile:langium){
            Matcher vitest=VITEST.matcher(generate(profile.id()).files().get("package.json"));
            assertThat(vitest.find()).as(profile.id()+" pins an exact Vitest").isTrue();
            // Vitest 5 declares engines ^22.12.0 || ^24.0.0 || >=26.0.0, so no Langium profile may advertise an older Node line.
            assertThat(vitest.group(1)).as(profile.id()).isEqualTo("5");
            assertThat(profile.nodeVersion()).as(profile.id()).startsWith("22.12");
        }
    }

    @Test void theDefaultLangiumProfileRecommendsTheNodeRuntimeHydraIsQualifiedWith() throws Exception {
        GrammarToolchainProfile profile=profiles.require("",GrammarTechnology.LANGIUM);
        String toolchain=Files.readString(Path.of("config","hydra-toolchain.json"),StandardCharsets.UTF_8);
        Matcher node=Pattern.compile("\"node\"\\s*:\\s*\"([^\"]+)\"").matcher(toolchain);
        assertThat(node.find()).isTrue();
        assertThat(profile.id()).isEqualTo("langium-4.4-browser");
        assertThat(profile.nodeVersion()).isEqualTo("22.12+");
        assertThat(profile.settings()).containsEntry("recommendedNodeVersion",node.group(1)).containsEntry("recommendedNodeVersion","26.12.1");
    }

    @Test void langium43StopsAtTheLastNodeLineWhereLangiumCli43StillGenerates(){
        GrammarToolchainProfile profile=profiles.require("langium-4.3-browser",GrammarTechnology.LANGIUM);
        assertThat(profile.nodeVersion()).isEqualTo("22.12-24.19");
        assertThat(profile.settings()).doesNotContainKey("recommendedNodeVersion");
    }

    private GeneratedGrammarProject generate(String profileId){
        GrammarSourceDocument document=new GrammarSourceDocument("main","main.langium","langium",SOURCE,"",true,false);
        GrammarDefinitionModel grammar=new GrammarDefinitionModel("1.0","stable","stable",GrammarTechnology.LANGIUM,GrammarAuthoringMode.GRAMMAR_FIRST,"",profileId,"main","Model",
                List.of(document),GrammarSemanticBindingMap.empty(),"",Map.of(),Map.of());
        return provider.generateProject(new GrammarProjectGenerationRequest(grammar,profiles.require(profileId,GrammarTechnology.LANGIUM),List.of(document),Map.of()));
    }
}
