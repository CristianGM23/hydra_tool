package com.example.editor.textual.runtime;

import java.time.Instant;
import java.util.*;
import com.example.editor.grammar.core.*;
import com.example.editor.grammar.runtime.GrammarCompilationSnapshot;
import com.example.editor.textual.source.TextualModelSourceDocument;

/** Focal R8 stress harness: neutral grammar matching, aliases, diagnostic caps and large-model stack safety. */
public final class R8NeutralTextualParserStressHarness {
    public static void main(String[] args) {
        runTechnology(GrammarTechnology.LANGIUM,"main.langium","grammar Main\nentry Model: elements+=Element*;\nElement: 'element' name=ID;\nterminal ID: /[a-zA-Z_][a-zA-Z0-9_]*/;","Model");
        runTechnology(GrammarTechnology.XTEXT,"Main.xtext","grammar demo.Main with org.eclipse.xtext.common.Terminals\nModel: elements+=Element*;\nElement: 'element' name=ID;","Model");
        runTechnology(GrammarTechnology.ANTLR,"Main.g4","grammar Main;\nmodel: element* EOF;\nelement: 'element' ID;\nID: [a-zA-Z_][a-zA-Z0-9_]*;\nWS: [ \\t\\r\\n]+ -> skip;","model");
        diagnosticCap();
        largeModel();
        largeInvalidModel();
        screenshotInvalidModelIsRejected();
        compiledGrammarRefreshAndDetailedDiagnostic();
        unavailableContractFailsClosed();
        deeplyNestedGrammarDoesNotOverflow();
        System.out.println("R8_NEUTRAL_TEXTUAL_PARSER_STRESS=PASS");
    }

    private static void runTechnology(GrammarTechnology technology,String path,String grammar,String entry){
        GrammarCompilationSnapshot snapshot=snapshot(technology,path,grammar,entry);
        NeutralTextualModelParser parser=new NeutralTextualModelParser();
        var valid=parser.parse(1,List.of(new TextualModelSourceDocument("main","main.model","element foo",1)),snapshot,20);
        require(valid.valid(),technology+" valid model rejected: "+valid.diagnostics());
        require(valid.semanticModel()!=null,technology+" did not create canonical model");
        require(valid.semanticModel().elements().stream().anyMatch(e->e.id().equals("foo")&&e.classId().equals("Element")),technology+" literal-to-rule alias was not preserved");
        var invalid=parser.parse(2,List.of(new TextualModelSourceDocument("main","main.model","nonsense foo",2)),snapshot,20);
        require(!invalid.valid(),technology+" arbitrary syntax was accepted");
        require(invalid.diagnostics().stream().anyMatch(d->d.code().equals("TEXTUAL_GRAMMAR_MISMATCH")),technology+" did not emit TEXTUAL_GRAMMAR_MISMATCH");
    }

    private static void diagnosticCap(){
        GrammarCompilationSnapshot snapshot=snapshot(GrammarTechnology.LANGIUM,"main.langium","grammar Main\nentry Model: elements+=Element*;\nElement: 'element' name=ID;\nterminal ID: /[a-z]+/;","Model");
        StringBuilder source=new StringBuilder();for(int i=0;i<100;i++)source.append("garbage broken").append(i).append('\n');
        var result=new NeutralTextualModelParser().parse(3,List.of(new TextualModelSourceDocument("main","main.model",source.toString(),3)),snapshot,7);
        require(result.diagnostics().size()<=7,"maximumDiagnostics was exceeded");
        require("7".equals(result.metadata().get("maximumDiagnostics")),"configured diagnostic limit was not propagated");
    }

    private static void largeModel(){
        GrammarCompilationSnapshot snapshot=snapshot(GrammarTechnology.LANGIUM,"main.langium","grammar Main\nentry Model: elements+=Element*;\nElement: 'element' name=ID;\nterminal ID: /[a-zA-Z_][a-zA-Z0-9_]*/;","Model");
        StringBuilder source=new StringBuilder();for(int i=0;i<20_000;i++)source.append("element e").append(i).append('\n');
        long started=System.nanoTime();
        var result=new NeutralTextualModelParser().parse(4,List.of(new TextualModelSourceDocument("main","main.model",source.toString(),4)),snapshot,25);
        long millis=(System.nanoTime()-started)/1_000_000L;
        require(result.valid(),"20k declaration model rejected: "+result.diagnostics().stream().limit(3).toList());
        require(result.semanticModel()!=null&&result.semanticModel().elements().size()==20_000,"20k declaration canonicalization incomplete");
        require(result.metadata().getOrDefault("grammarValidationStrategy","").contains("INCREMENTAL"),"large model did not use incremental validation");
        System.out.println("large_model_20k_ms="+millis+" strategy="+result.metadata().get("grammarValidationStrategy"));
    }


    private static void largeInvalidModel(){
        GrammarCompilationSnapshot snapshot=snapshot(GrammarTechnology.LANGIUM,"main.langium","grammar Main\nentry Model: elements+=Element*;\nElement: 'element' name=ID;\nterminal ID: /[a-zA-Z_][a-zA-Z0-9_]*/;","Model");
        StringBuilder source=new StringBuilder();
        for(int i=0;i<12_000;i++){
            if(i==6_000)source.append("nonsense broken\n");
            else source.append("element e").append(i).append('\n');
        }
        var result=new NeutralTextualModelParser().parse(5,List.of(new TextualModelSourceDocument("main","main.model",source.toString(),5)),snapshot,25);
        require(!result.valid(),"large model with an invalid declaration was accepted");
        require(result.diagnostics().stream().anyMatch(d->d.code().equals("TEXTUAL_GRAMMAR_MISMATCH")),"large invalid model did not emit TEXTUAL_GRAMMAR_MISMATCH");
        require(result.metadata().getOrDefault("grammarValidationStrategy","").contains("INCREMENTAL"),"large invalid model did not use incremental validation");
    }

    private static void screenshotInvalidModelIsRejected(){
        GrammarCompilationSnapshot snapshot=snapshot(GrammarTechnology.LANGIUM,"main.langium","grammar Main\nentry Model: elements+=Element*;\nElement: 'element' name=ID;\nhidden terminal WS: /\\s+/;\nterminal ID: /[_a-zA-Z][\\w_]*/;","Model");
        String source="Element example {\n hjas;\n aspj'sa oiasnipaspi\n}\n";
        var result=new NeutralTextualModelParser().parse(7,List.of(new TextualModelSourceDocument("main","main.model",source,7)),snapshot,20);
        require(!result.valid(),"screenshot-style invalid model was accepted");
        require(result.diagnostics().stream().anyMatch(d->d.code().equals("TEXTUAL_GRAMMAR_MISMATCH")),"screenshot-style invalid model did not report the active grammar mismatch");
    }

    private static void compiledGrammarRefreshAndDetailedDiagnostic(){
        String grammar="grammar Main\nentry Model: elements+=Element*;\nElement: 'elementSsss' name=ID surname=ID;\nhidden terminal WS: /\\s+/;\nterminal ID: /[_a-zA-Z][\\w_]*/;";
        GrammarCompilationSnapshot snapshot=snapshot(GrammarTechnology.LANGIUM,"main.langium",grammar,"Model");
        NeutralTextualModelParser parser=new NeutralTextualModelParser();
        var valid=parser.parse(9,List.of(new TextualModelSourceDocument("main","main.model","elementSsss example surname1",9)),snapshot,20);
        require(valid.valid(),"freshly compiled grammar rejected its own valid model: "+valid.diagnostics());
        require(valid.semanticModel()!=null&&valid.semanticModel().elements().stream().anyMatch(e->e.id().equals("example")&&e.classId().equals("Element")),"flat grammar syntax did not materialize the semantic Element");
        var missingSurname=parser.parse(10,List.of(new TextualModelSourceDocument("main","main.model","elementSsss example",10)),snapshot,20);
        var diagnostic=missingSurname.diagnostics().stream().filter(d->d.code().equals("TEXTUAL_GRAMMAR_MISMATCH")).findFirst().orElseThrow(()->new AssertionError("missing surname was not diagnosed"));
        require("Element".equals(diagnostic.metadata().get("ruleContext")),"diagnostic did not identify the failing Element rule: "+diagnostic.metadata());
        require(diagnostic.metadata().getOrDefault("expectedHuman","").contains("identificador"),"diagnostic did not explain the expected identifier: "+diagnostic.metadata());
        require(!diagnostic.metadata().getOrDefault("quickFixReplacement","").isBlank(),"deterministic missing token did not expose a quick fix");
    }

    private static void unavailableContractFailsClosed(){
        GrammarRuntimeDescriptor runtime=new GrammarRuntimeDescriptor("PARSER_BRIDGE","Model",List.of("model"),"","",Map.of());
        GeneratedGrammarProject project=new GeneratedGrammarProject("missing-grammar-source",Map.of("README.txt","generated host without retained grammar source"),Map.of());
        GrammarCompilationSnapshot snapshot=new GrammarCompilationSnapshot("snapshot-unavailable","grammar-unavailable",1L,"source-hash-unavailable",GrammarTechnology.LANGIUM,"r8-provider","r8-profile",CompilationStatus.SUCCESS,List.of(),new GrammarSemanticBindingMap("1.0",List.of(),Map.of()),runtime,project,Map.of(),Instant.parse("2026-09-11T16:00:00Z"),Map.of("immutable","true"));
        var result=new NeutralTextualModelParser().parse(8,List.of(new TextualModelSourceDocument("main","main.model","anything at all",8)),snapshot,20);
        require(!result.valid(),"snapshot without a provable grammar contract was accepted");
        require(result.diagnostics().stream().anyMatch(d->d.code().equals("TEXTUAL_GRAMMAR_VALIDATION_UNAVAILABLE")),"missing syntax contract did not fail closed");
    }

    private static void deeplyNestedGrammarDoesNotOverflow(){
        StringBuilder body=new StringBuilder();
        for(int i=0;i<240;i++)body.append('(');
        body.append("'element' ID");
        for(int i=0;i<240;i++)body.append(')');
        String grammar="grammar Main\nentry Model: "+body+";\nterminal ID: /[a-z]+/;";
        GrammarCompilationSnapshot snapshot=snapshot(GrammarTechnology.LANGIUM,"main.langium",grammar,"Model");
        var result=new NeutralTextualModelParser().parse(6,List.of(new TextualModelSourceDocument("main","main.model","element safe",6)),snapshot,10);
        require(result.diagnostics().size()<=10,"deep grammar exceeded diagnostic budget");
        require(!result.metadata().getOrDefault("grammarValidationStrategy","").isBlank(),"deep grammar did not report validation strategy");
    }

    private static GrammarCompilationSnapshot snapshot(GrammarTechnology technology,String path,String grammar,String entry){
        GrammarSemanticBinding binding=new GrammarSemanticBinding("rule:"+entry+":Element","RULE","Element","CLASS",1d,"R8_TEST",Map.of("grammarName","Element"));
        GrammarSemanticBindingMap bindings=new GrammarSemanticBindingMap("1.0",List.of(binding),Map.of());
        GrammarRuntimeDescriptor runtime=new GrammarRuntimeDescriptor("PARSER_BRIDGE",entry,List.of("model"),"","",Map.of());
        GeneratedGrammarProject project=new GeneratedGrammarProject("r8-"+technology.name().toLowerCase(Locale.ROOT),Map.of(path,grammar),Map.of("technology",technology.name()));
        return new GrammarCompilationSnapshot("snapshot-r8-"+technology.name().toLowerCase(Locale.ROOT),"grammar-r8",1L,"source-hash-r8",technology,"r8-provider","r8-profile",CompilationStatus.SUCCESS,List.of(),bindings,runtime,project,Map.of(),Instant.parse("2026-09-11T16:00:00Z"),Map.of("immutable","true"));
    }

    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
