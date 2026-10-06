package com.example.editor.integration.verification;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.example.editor.grammar.GrammarProperties;
import com.example.editor.grammar.application.GrammarGeneratedProjectArchiveService;
import com.example.editor.grammar.core.*;
import com.example.editor.grammar.provider.antlr.AntlrGrammarProvider;
import com.example.editor.integration.IntegrationArtifactKind;
import com.example.editor.integration.IntegrationSupportLevel;
import com.example.editor.integration.compiler.TechnologyAdapterTestFixtures;
import com.example.editor.integration.projection.CanonicalArtifactContent;
import com.example.editor.integration.projection.canonical.PlantUmlCanonicalProjectionAdapter;

/** Destinations that do not use the classic TechnologyAdapter project pipeline: ANTLR and PlantUML. */
class AggressiveSpecialIntegrationArtifactTests {
    @Test void antlrJavaAndTypeScriptProjectsPreserveTheSameGrammarButExposeDifferentExecutableScaffolds(){
        GrammarProperties props=new GrammarProperties(); AntlrGrammarProvider provider=new AntlrGrammarProvider(props);
        String grammarText="grammar People; model: person* EOF; person: 'person' ID; ID:[a-zA-Z]+; WS:[ \\t\\r\\n]+->skip;";
        GrammarSourceDocument doc=new GrammarSourceDocument("main","People.g4","antlr",grammarText,"",true,false);
        GrammarDefinitionModel grammar=new GrammarDefinitionModel("1.0","people","People",GrammarTechnology.ANTLR,GrammarAuthoringMode.GRAMMAR_FIRST,"","antlr",doc.id(),"model",List.of(doc),GrammarSemanticBindingMap.empty(),"",Map.of(),Map.of());
        GeneratedGrammarProject javaProject=provider.generateProject(new GrammarProjectGenerationRequest(grammar,profile("java","Java","maven"),List.of(doc),Map.of()));
        GeneratedGrammarProject tsProject=provider.generateProject(new GrammarProjectGenerationRequest(grammar,profile("ts","TypeScript","npm"),List.of(doc),Map.of()));
        assertEquals(grammarText,javaProject.files().get("People.g4")); assertEquals(grammarText,tsProject.files().get("People.g4"));
        assertTrue(javaProject.files().containsKey("pom.xml")); assertTrue(javaProject.files().keySet().stream().anyMatch(p->p.endsWith("HydraAntlrGenerationSmokeTest.java")));
        assertTrue(tsProject.files().containsKey("package.json")); assertTrue(tsProject.files().containsKey("generator-pom.xml")); assertTrue(tsProject.files().containsKey("test/generation.test.ts"));
        assertFalse(javaProject.files().containsKey("package.json")); assertFalse(tsProject.files().containsKey("pom.xml"));
        GrammarGeneratedProjectArchiveService archive=new GrammarGeneratedProjectArchiveService(props); assertArrayEquals(archive.archive(javaProject),archive.archive(javaProject)); assertArrayEquals(archive.archive(tsProject),archive.archive(tsProject));
        assertTrue(javaProject.files().get("HYDRA_PROVENANCE.properties").contains("generator=ANTLR4_OFFICIAL")); assertTrue(tsProject.files().get("src/HydraSemanticBridge.md").contains("GrammarSemanticBindingMap"));
    }

    @Test void plantUmlMultiArtifactProjectionRecoversEachCanonicalLayerAfterOrderPermutation(){
        assertEquals(com.example.editor.integration.IntegrationTechnology.PLANTUML.name(),"PLANTUML");
        PlantUmlCanonicalProjectionAdapter adapter=new PlantUmlCanonicalProjectionAdapter(TechnologyAdapterTestFixtures.MAPPER);
        List<CanonicalArtifactContent> input=List.of(artifact(IntegrationArtifactKind.VALIDATIONS,"validation","rules"),artifact(IntegrationArtifactKind.GAMIFICATION_DEFINITIONS,"gamification","points"),artifact(IntegrationArtifactKind.EVENT_FLOWS,"events","flows"));
        var one=adapter.generate(input); List<CanonicalArtifactContent> reversed=new ArrayList<>(input); Collections.reverse(reversed); var two=adapter.generate(reversed);
        assertTrue(one.diagnostics().isEmpty()); assertTrue(two.diagnostics().isEmpty()); assertEquals(3,one.artifacts().size());
        Map<String,byte[]> expected=new HashMap<>(); input.forEach(a->expected.put(a.documentId(),a.content()));
        for(var generated:one.artifacts()){var imported=adapter.importProjection(generated.relativePath(),generated.content()); assertEquals(IntegrationSupportLevel.FULL,imported.supportLevel()); assertEquals(1,imported.recoveredArtifacts().size()); var recovered=imported.recoveredArtifacts().get(0); assertArrayEquals(expected.get(recovered.documentId()),recovered.content());}
        assertEquals(one.artifacts().stream().map(a->a.relativePath()).sorted().toList(),two.artifacts().stream().map(a->a.relativePath()).sorted().toList());
    }

    private static GrammarToolchainProfile profile(String id,String target,String build){return new GrammarToolchainProfile(id,GrammarTechnology.ANTLR,"4.13.2","4.13.2","21","22",build,target,true,2_000,20_000,256L*1024L*1024L,GrammarExecutionMode.INTERNAL,Map.of("visitor","true","listener","true"));}
    private static CanonicalArtifactContent artifact(IntegrationArtifactKind kind,String id,String field){return new CanonicalArtifactContent(kind,id,"application/json",("{\"schemaVersion\":\"1.0\",\"id\":\""+id+"\",\""+field+"\":[{\"id\":\"one\",\"name\":\"One\"}]}").getBytes(StandardCharsets.UTF_8));}
}
