import java.util.*;
import com.example.editor.grammar.GrammarProperties;
import com.example.editor.grammar.application.GrammarSourceAnalyzer;
import com.example.editor.grammar.core.*;
import com.example.editor.grammar.visualization.*;

public final class GrammarDiagramV43297StressHarness {
    private static int checks;
    public static void main(String[] args){
        GrammarProperties properties=new GrammarProperties();
        properties.setMaximumDiagramNodes(6_500);properties.setMaximumDiagramEdges(25_000);properties.setMaximumDiagramFeatures(50_000);properties.setMaximumDiagramCacheEntries(8);
        GrammarClassDiagramProjectionService service=new GrammarClassDiagramProjectionService(properties);
        GrammarSourceAnalyzer analyzer=new GrammarSourceAnalyzer(properties);

        var langium=grammar("langium-diagram",GrammarTechnology.LANGIUM,"main.langium","""
            grammar Company
            entry Model: elements+=Element*;
            Element: Company | Person;
            Company: 'company' name=ID '{' 'employees' employees+=Person* '}';
            Person: 'person' name=ID '{' 'age' age=INT 'state' state=State '}';
            State returns string: 'ACTIVE' | 'INACTIVE';
            """);
        var lp=project(service,analyzer,langium,1);
        check(lp.status()==GrammarDiagramProjectionStatus.COMPLETE,"Langium complete");
        check(lp.nodes().stream().anyMatch(n->n.name().equals("Model")&&n.root()),"root node");
        check(lp.nodes().stream().anyMatch(n->n.name().equals("Element")&&n.kind().equals("ABSTRACT_CLASS")),"abstract class");
        check(lp.nodes().stream().anyMatch(n->n.name().equals("State")&&n.kind().equals("ENUM")),"enum node");
        check(lp.edges().stream().filter(e->e.kind().equals("INHERITANCE")).count()==2,"inheritance edges");
        check(lp.edges().stream().anyMatch(e->e.label().equals("employees")&&e.kind().equals("CONTAINMENT")),"containment edge");
        check(lp.nodes().stream().filter(n->n.name().equals("Person")).flatMap(n->n.features().stream()).anyMatch(f->f.name().equals("age")&&f.source().documentId().equals("main")),"feature navigation");
        check(lp.nodes().stream().filter(n->n.name().equals("Person")).findFirst().orElseThrow().source().range().start().line()>=0,"class source range");
        var lp2=project(service,analyzer,langium,1);check(lp.projectionHash().equals(lp2.projectionHash()),"deterministic projection hash");

        var xtext=grammar("xtext-diagram",GrammarTechnology.XTEXT,"main.xtext","""
            grammar generated.Company with org.eclipse.xtext.common.Terminals
            import "urn:company" as model
            Model: elements+=Element*;
            Element: Company | Person;
            Company returns model::Company: 'company' name=ID '{' 'owner' owner=[model::Person|ID] '}';
            Person returns model::Person: 'person' name=ID '{' '}';
            """);
        var xp=project(service,analyzer,xtext,2);
        check(xp.nodes().stream().anyMatch(n->n.name().equals("Company")),"Xtext company");
        check(xp.edges().stream().anyMatch(e->e.label().equals("owner")&&e.kind().equals("REFERENCE")),"Xtext cross reference");
        check(xp.edges().stream().filter(e->e.kind().equals("INHERITANCE")).count()==2,"Xtext inheritance");

        var antlr=grammar("antlr-diagram",GrammarTechnology.ANTLR,"Company.g4","""
            grammar Company;
            model: element* EOF;
            element: company | person;
            company: 'company' ID '{' ('employees' person)* '}';
            person: 'person' ID '{' ('age' INT)? ('state' state) '}';
            state: 'ACTIVE' | 'INACTIVE';
            ID: [a-zA-Z_] [a-zA-Z_0-9]*;
            INT: [0-9]+;
            """);
        var ap=project(service,analyzer,antlr,3);
        check(ap.nodes().stream().anyMatch(n->n.name().equals("Company")),"ANTLR company");
        check(ap.nodes().stream().anyMatch(n->n.name().equals("Person")),"ANTLR person");
        check(ap.nodes().stream().anyMatch(n->n.kind().equals("ENUM")),"ANTLR enum");
        check(ap.edges().stream().anyMatch(e->e.kind().equals("CONTAINMENT")),"ANTLR containment");


        // A compilation projection must use the exact frozen documents, not an older persisted draft.
        var persisted=grammar("frozen-diagram",GrammarTechnology.LANGIUM,"main.langium","grammar Frozen\nentry Model: old+=Old*;\nOld: 'old' name=ID;\n");
        var frozenDoc=new GrammarSourceDocument("main","main.langium","langium","grammar Frozen\nentry Model: current+=Current*;\nCurrent: 'current' name=ID;\n","",true,false);
        var frozenDocs=List.of(frozenDoc);
        var analyzedFrozen=new GrammarDefinitionModel(persisted.schemaVersion(),persisted.id(),persisted.name(),persisted.technology(),persisted.authoringMode(),persisted.metamodelId(),persisted.toolchainProfileId(),persisted.entryDocumentId(),persisted.entryRule(),frozenDocs,persisted.semanticBindings(),persisted.syntaxStyleDefinitionId(),persisted.settings(),persisted.annotations());
        var frozenRequest=new GrammarAnalysisRequest(analyzedFrozen.id(),6,GrammarAnalysisMode.NORMAL,List.of("main"),frozenDocs,analyzedFrozen.toolchainProfileId());
        var frozenAnalysis=analyzer.analyze(analyzedFrozen,frozenRequest);
        var frozenProjection=service.project(persisted,frozenDocs,frozenAnalysis);
        check(frozenProjection.sourceHash().equals(GrammarHashing.sources(frozenDocs)),"frozen source hash");
        check(frozenProjection.nodes().stream().anyMatch(n->n.name().equals("Current"))&&frozenProjection.nodes().stream().noneMatch(n->n.name().equals("Old")),"frozen documents drive projection");

        // An unscoped diagnostic in one range must not contaminate unrelated classes in the same document.
        var diagnosticGrammar=grammar("diagnostic-diagram",GrammarTechnology.LANGIUM,"main.langium","grammar Diagnostic\nentry Model: companies+=Company* people+=Person*;\nCompany: 'company' name=ID;\nPerson: 'person' name=ID;\n");
        var diagnosticRequest=new GrammarAnalysisRequest(diagnosticGrammar.id(),7,GrammarAnalysisMode.NORMAL,List.of("main"),diagnosticGrammar.documents(),diagnosticGrammar.toolchainProfileId());
        var diagnosticBase=analyzer.analyze(diagnosticGrammar,diagnosticRequest);
        var companySymbol=diagnosticBase.symbols().stream().filter(symbol->symbol.kind().equals("RULE")&&symbol.name().equals("Company")).findFirst().orElseThrow();
        var localDiagnostic=new com.example.editor.grammar.diagnostic.GrammarDiagnostic("LOCAL",GrammarSeverity.ERROR,"Local","Local","","","","main",companySymbol.range(),"","","","","",List.of(),List.of(),Map.of());
        var diagnosticAnalysis=new GrammarAnalysisResult(diagnosticBase.requestId(),diagnosticBase.draftRevision(),diagnosticBase.sourceHash(),diagnosticBase.mode(),diagnosticBase.providerInstanceId(),false,List.of(localDiagnostic),diagnosticBase.symbols(),diagnosticBase.semanticCandidate(),diagnosticBase.providerHealth(),diagnosticBase.codeActions(),diagnosticBase.metadata());
        var diagnosticProjection=service.project(diagnosticGrammar,diagnosticGrammar.documents(),diagnosticAnalysis);
        var companyNode=diagnosticProjection.nodes().stream().filter(n->n.name().equals("Company")).findFirst().orElseThrow();
        var personNode=diagnosticProjection.nodes().stream().filter(n->n.name().equals("Person")).findFirst().orElseThrow();
        check(companyNode.diagnosticCount()>0&&personNode.diagnosticCount()==0,"diagnostic overlay is range-localized");

        // Last-known-good is retained if a later broken draft yields no safe projection.
        var broken=grammarWithId("langium-diagram",GrammarTechnology.LANGIUM,"main.langium","!!!");
        var bp=project(service,analyzer,broken,4);
        check(bp.status()==GrammarDiagramProjectionStatus.LAST_KNOWN_GOOD,"last-known-good fallback");
        check(bp.baseSourceHash().equals(lp.sourceHash()),"fallback base hash");
        check(bp.nodes().size()==lp.nodes().size(),"fallback retains graph");
        check(bp.warnings().stream().anyMatch(w->w.code().equals("GRAMMAR_DIAGRAM_LAST_KNOWN_GOOD")),"fallback evidence");

        // Hard safety limit is a projection failure, never an unbounded response.
        GrammarProperties limited=new GrammarProperties();limited.setMaximumDiagramNodes(2);limited.setMaximumDiagramEdges(4);limited.setMaximumDiagramFeatures(20);
        var limitedService=new GrammarClassDiagramProjectionService(limited);
        var over=project(limitedService,new GrammarSourceAnalyzer(limited),langium,1);
        check(over.status()==GrammarDiagramProjectionStatus.FAILED,"node limit rejects projection");
        check(over.nodes().isEmpty(),"limit publishes no partial oversized graph");
        check(over.warnings().stream().anyMatch(w->w.message().contains("node limit")),"limit evidence");

        // Aggressive scale: 5,000 inferred classes under constrained heap.
        StringBuilder huge=new StringBuilder("grammar Huge\nentry Root: roots+=C0*;\n");
        for(int i=0;i<5_000;i++)huge.append("C").append(i).append(": 'c").append(i).append("' name=ID;\n");
        var hugeGrammar=grammar("huge-diagram",GrammarTechnology.LANGIUM,"huge.langium",huge.toString());
        long start=System.nanoTime();var hugeProjection=project(service,analyzer,hugeGrammar,5);long elapsedMs=(System.nanoTime()-start)/1_000_000L;
        check(hugeProjection.status()==GrammarDiagramProjectionStatus.COMPLETE,"5000 class projection complete");
        check(hugeProjection.nodes().size()>=5_001,"5000 class projection cardinality");
        check(elapsedMs<8_000,"5000 class projection budget: "+elapsedMs+"ms");
        System.out.println("Grammar diagram V4.32.97: "+checks+"/"+checks+" PASS; 5000-class projection="+elapsedMs+" ms; nodes="+hugeProjection.nodes().size());
    }

    private static GrammarClassDiagramProjection project(GrammarClassDiagramProjectionService service,GrammarSourceAnalyzer analyzer,GrammarDefinitionModel grammar,long revision){
        GrammarAnalysisRequest request=new GrammarAnalysisRequest(grammar.id(),revision,GrammarAnalysisMode.NORMAL,grammar.documents().stream().map(GrammarSourceDocument::id).toList(),grammar.documents(),grammar.toolchainProfileId());
        return service.project(grammar,analyzer.analyze(grammar,request));
    }
    private static GrammarDefinitionModel grammar(String id,GrammarTechnology technology,String path,String content){return grammarWithId(id,technology,path,content);}
    private static GrammarDefinitionModel grammarWithId(String id,GrammarTechnology technology,String path,String content){
        String lang=technology.name().toLowerCase(Locale.ROOT);GrammarSourceDocument doc=new GrammarSourceDocument("main",path,lang,content,"",true,false);
        return new GrammarDefinitionModel("1.0",id,id,technology,GrammarAuthoringMode.GRAMMAR_FIRST,"",technology==GrammarTechnology.XTEXT?"xtext-2.44-lsp":technology==GrammarTechnology.ANTLR?"antlr-4.13.2-java":"langium-4.3-browser","main","Model",List.of(doc),GrammarSemanticBindingMap.empty(),"",Map.of(),Map.of());
    }
    private static void check(boolean condition,String name){checks++;if(!condition)throw new AssertionError("FAILED: "+name);}
}
