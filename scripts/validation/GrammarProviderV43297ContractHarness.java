import java.util.*;
import com.example.editor.grammar.GrammarProperties;
import com.example.editor.grammar.application.GrammarToolchainProfileService;
import com.example.editor.grammar.core.*;
import com.example.editor.grammar.provider.*;
import com.example.editor.grammar.provider.xtext.XtextGrammarProvider;
import com.example.editor.grammar.provider.langium.LangiumGrammarProvider;
import com.example.editor.grammar.provider.antlr.AntlrGrammarProvider;

/** Independent executable contract harness for V4.32.97 provider generation. No Spring context required. */
public final class GrammarProviderV43297ContractHarness {
    private static int checks;
    public static void main(String[] args) {
        GrammarProperties properties=new GrammarProperties();
        GrammarToolchainProfileService profiles=new GrammarToolchainProfileService();
        check("langium-4.3-browser".equals(profiles.require("",GrammarTechnology.LANGIUM).id()),"modern Langium default");
        check("xtext-2.44-lsp".equals(profiles.require("",GrammarTechnology.XTEXT).id()),"modern Xtext default");
        check("antlr-4.13.2-java".equals(profiles.require("",GrammarTechnology.ANTLR).id()),"ANTLR default");
        for(GrammarToolchainProfile p:profiles.list()){
            check(!p.providerVersion().isBlank()&&!p.providerVersion().equalsIgnoreCase("auto"),"provider version pinned "+p.id());
            check(!p.runtimeVersion().isBlank()&&!p.runtimeVersion().equalsIgnoreCase("auto"),"runtime version pinned "+p.id());
        }

        runLangium(new LangiumGrammarProvider(properties),profiles);
        runXtext(new XtextGrammarProvider(properties),profiles);
        runAntlr(new AntlrGrammarProvider(properties),profiles,"antlr-4.13.2-java");
        runAntlr(new AntlrGrammarProvider(properties),profiles,"antlr-4.13.2-typescript");
        System.out.println("GRAMMAR_PROVIDER_V43297_CONTRACT PASS checks="+checks);
    }

    private static void runLangium(GrammarProvider provider, GrammarToolchainProfileService profiles){
        String src="grammar Sample\nentry Model: items+=Item*;\nItem: 'item' name=ID ('parent' parent=[Item])?;\nterminal ID: /[a-zA-Z_][\\w_]*/;\n";
        GrammarSourceDocument doc=doc("main","src/sample.langium","langium",src,true);
        GrammarDefinitionModel g=grammar("samplelang", "Sample Language", GrammarTechnology.LANGIUM,"langium-4.3-browser",doc,"Model");
        GrammarToolchainProfile profile=profiles.require(g.toolchainProfileId(),g.technology());
        GeneratedGrammarProject project=provider.generateProject(new GrammarProjectGenerationRequest(g,profile,g.documents(),Map.of()));
        frozen(project,doc);
        required(project,"package.json","langium\": \"4.3.0","vscode-languageserver","build:browser","esbuild","hydra-language-worker.js");
        required(project,"langium-config.json","SampleLanguage","src/sample.langium");
        required(project,"src/language/samplelang-module.ts","GeneratedModule","createDefaultSharedModule","ServiceRegistry.register");
        required(project,"src/language/main.ts","NodeFileSystem","startLanguageServer","createConnection");
        required(project,"src/language/browser-worker.ts","EmptyFileSystem","BrowserMessageReader","startLanguageServer");
        required(project,"src/cli/main.ts","DocumentBuilder.build","validation: true");
        check(provider.runtimeDescriptor(g,profile).browserWorkerEntry().endsWith("hydra-language-worker.js"),"Langium browser runtime is bundled entry");
        analyze(provider,g,profile);
    }

    private static void runXtext(GrammarProvider provider, GrammarToolchainProfileService profiles){
        String src="grammar hydra.generated.Sample with org.eclipse.xtext.common.Terminals\n\ngenerate sample 'https://hydra/generated/sample'\n\nModel: items+=Item*;\nItem: 'item' name=ID ('parent' parent=[Item])?;\n";
        GrammarSourceDocument doc=doc("main","src/Sample.xtext","xtext",src,true);
        GrammarDefinitionModel g=grammar("samplextext","Sample Xtext",GrammarTechnology.XTEXT,"xtext-2.44-lsp",doc,"Model");
        GrammarToolchainProfile profile=profiles.require(g.toolchainProfileId(),g.technology());
        GeneratedGrammarProject project=provider.generateProject(new GrammarProjectGenerationRequest(g,profile,g.documents(),Map.of()));
        frozen(project,doc);
        required(project,"pom.xml","2.44.0","maven.compiler.release>21","org.eclipse.xtext.xtext.generator.dependencies","Mwe2Launcher");
        required(project,"src/main/mwe2/GenerateLanguage.mwe2","XtextGenerator","StandardProjectConfig","genericIde","eclipsePlugin = { enabled = false }");
        check(project.files().values().stream().noneMatch(v->v.contains("xtext-web")||v.contains("org.eclipse.xtext.web")),"Xtext web stack excluded");
        check(provider.descriptor().metadata().get("xtextWeb").equals("false"),"Xtext descriptor excludes xtext-web");
        analyze(provider,g,profile);
    }

    private static void runAntlr(GrammarProvider provider, GrammarToolchainProfileService profiles,String profileId){
        String src="grammar Sample;\nmodel: item* EOF;\nitem: 'item' ID ('parent' ID)? ';';\nID: [a-zA-Z_][a-zA-Z_0-9]*;\nWS: [ \\t\\r\\n]+ -> skip;\n";
        GrammarSourceDocument doc=doc("main","grammar/Sample.g4","antlr",src,true);
        GrammarDefinitionModel g=grammar("sampleantlr","Sample ANTLR",GrammarTechnology.ANTLR,profileId,doc,"model");
        GrammarToolchainProfile profile=profiles.require(g.toolchainProfileId(),g.technology());
        GeneratedGrammarProject project=provider.generateProject(new GrammarProjectGenerationRequest(g,profile,g.documents(),Map.of()));
        frozen(project,doc);
        if(profileId.endsWith("typescript")){
            required(project,"generator-pom.xml","org.antlr.v4.Tool","-Dlanguage=TypeScript","4.13.2");
            required(project,"package.json","\"antlr4\": \"4.13.2\"","vitest");
            required(project,"tsconfig.json","src/generated/**/*.ts");
        }else{
            required(project,"pom.xml","antlr4-maven-plugin","antlr4-runtime","4.13.2");
        }
        check(project.files().containsKey("src/HydraSemanticBridge.md"),"ANTLR explicit semantic bridge");
        analyze(provider,g,profile);
    }

    private static void analyze(GrammarProvider provider, GrammarDefinitionModel grammar, GrammarToolchainProfile profile){
        GrammarAnalysisRequest request=new GrammarAnalysisRequest("provider-contract:"+provider.descriptor().id(),grammar.id(),grammar.entryDocumentId(),1,GrammarAnalysisMode.NORMAL,List.of(grammar.entryDocumentId()),grammar.documents(),profile.id(),"",Map.of());
        GrammarAnalysisResult result=provider.analyze(grammar,request);
        check(result.draftRevision()==1,"analysis revision "+provider.descriptor().id());
        check(result.sourceHash().equals(GrammarHashing.sources(grammar.documents())),"analysis source hash "+provider.descriptor().id());
        check(!result.symbols().isEmpty(),"symbols produced "+provider.descriptor().id());
        GrammarProviderCapabilities caps=provider.descriptor().capabilityDescriptor();
        check(caps.features().contains(GrammarProviderCapability.PROJECT_GENERATION),"generation capability "+provider.descriptor().id());
        check(caps.supportsLive(GrammarProviderCapability.ANALYZE),"analysis is live "+provider.descriptor().id());
        check(caps.supportsLive(GrammarProviderCapability.CODE_ACTIONS),"code actions are live "+provider.descriptor().id());
        check(caps.supportsLive(GrammarProviderCapability.PROJECT_GENERATION),"project generation is live "+provider.descriptor().id());
        check(caps.supportsLive(GrammarProviderCapability.TEXTUAL_RUNTIME),"textual runtime is live "+provider.descriptor().id());
        if(provider.descriptor().technology()==GrammarTechnology.LANGIUM||provider.descriptor().technology()==GrammarTechnology.XTEXT){
            check(!caps.supportsLive(GrammarProviderCapability.COMPLETION),"completion is not falsely live "+provider.descriptor().id());
            check(caps.supportsGeneratedHost(GrammarProviderCapability.COMPLETION),"completion generated-host capability "+provider.descriptor().id());
            check(caps.supportsGeneratedHost(GrammarProviderCapability.LANGUAGE_SERVER),"language server generated-host capability "+provider.descriptor().id());
        }else{
            check(!caps.declares(GrammarProviderCapability.LANGUAGE_SERVER),"ANTLR does not claim a language server");
            check(caps.supportsGeneratedHost(GrammarProviderCapability.LEXER_PARSER),"ANTLR parser generated-host capability");
        }
        check(provider.descriptor().analysisModes().containsAll(List.of("FAST","NORMAL","FULL")),"analysis modes "+provider.descriptor().id());
    }

    private static GrammarSourceDocument doc(String id,String path,String language,String content,boolean entry){return new GrammarSourceDocument(id,path,language,content,"",entry,false);}
    private static GrammarDefinitionModel grammar(String id,String name,GrammarTechnology technology,String profile,GrammarSourceDocument doc,String entryRule){return new GrammarDefinitionModel("1.0",id,name,technology,GrammarAuthoringMode.GRAMMAR_FIRST,"",profile,doc.id(),entryRule,List.of(doc),GrammarSemanticBindingMap.empty(),"",Map.of(),Map.of());}
    private static void frozen(GeneratedGrammarProject project,GrammarSourceDocument source){check(source.content().equals(project.files().get(source.relativePath())),"frozen source preserved "+source.relativePath());check(project.metadata().get("sourceHash").equals(GrammarHashing.sources(List.of(source))),"provenance source hash");check(project.expandedBytes()>source.content().length(),"generated project has scaffold");for(String path:project.files().keySet())check(!path.startsWith("/")&&!path.contains("..")&&!path.contains("\\"),"portable generated path "+path);}
    private static void required(GeneratedGrammarProject project,String path,String... needles){String value=project.files().get(path);check(value!=null,"generated file "+path);for(String needle:needles)check(value.contains(needle),path+" contains "+needle);}
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError("FAIL["+checks+"] "+message);}
}
