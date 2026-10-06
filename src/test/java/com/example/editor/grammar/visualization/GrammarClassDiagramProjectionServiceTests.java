package com.example.editor.grammar.visualization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import com.example.editor.grammar.GrammarProperties;
import com.example.editor.grammar.application.GrammarSourceAnalyzer;
import com.example.editor.grammar.core.*;
import com.example.editor.grammar.diagnostic.GrammarDiagnostic;

class GrammarClassDiagramProjectionServiceTests {
    @Test void projectsLangiumStructureWithNavigationAndDeterministicHash() {
        GrammarProperties properties=properties();
        GrammarDefinitionModel grammar=grammar("people", """
                grammar People
                entry Model: people+=Person*;
                Person: 'person' name=ID '{' age=INT manager=[Person|ID] '}';
                """);
        GrammarAnalysisResult analysis=analyze(properties,grammar,3);
        GrammarClassDiagramProjectionService service=new GrammarClassDiagramProjectionService(properties);
        GrammarClassDiagramProjection first=service.project(grammar,grammar.documents(),analysis);
        GrammarClassDiagramProjection second=service.project(grammar,grammar.documents(),analysis);

        assertThat(first.status()).isEqualTo(GrammarDiagramProjectionStatus.COMPLETE);
        GrammarClassDiagramNode person=first.nodes().stream().filter(node->node.name().equals("Person")).findFirst().orElseThrow();
        assertThat(person.source().documentId()).isEqualTo("main");
        assertThat(person.features()).extracting(GrammarDiagramFeature::name).contains("age","manager");
        GrammarClassDiagramEdge manager=first.edges().stream().filter(edge->edge.label().equals("manager")).findFirst().orElseThrow();
        assertThat(manager.kind()).isEqualTo("REFERENCE");
        assertThat(first.projectionHash()).isEqualTo(second.projectionHash()).isNotBlank();
    }

    @Test void usesExactlyFrozenCompilationDocumentsInsteadOfOlderPersistedDraft() {
        GrammarProperties properties=properties();
        GrammarDefinitionModel persisted=grammar("frozen", "grammar Frozen\nentry Model: old+=Old*;\nOld: 'old' name=ID;\n");
        GrammarSourceDocument frozenDoc=new GrammarSourceDocument("main","main.langium","langium",
                "grammar Frozen\nentry Model: current+=Current*;\nCurrent: 'current' name=ID;\n","",true,false);
        List<GrammarSourceDocument> frozen=List.of(frozenDoc);
        GrammarDefinitionModel analyzed=new GrammarDefinitionModel(persisted.schemaVersion(),persisted.id(),persisted.name(),persisted.technology(),persisted.authoringMode(),persisted.metamodelId(),persisted.toolchainProfileId(),persisted.entryDocumentId(),persisted.entryRule(),frozen,persisted.semanticBindings(),persisted.syntaxStyleDefinitionId(),persisted.settings(),persisted.annotations());
        GrammarAnalysisResult analysis=analyze(properties,analyzed,9);
        GrammarClassDiagramProjection projection=new GrammarClassDiagramProjectionService(properties).project(persisted,frozen,analysis);

        assertThat(projection.sourceHash()).isEqualTo(GrammarHashing.sources(frozen));
        assertThat(projection.nodes()).extracting(GrammarClassDiagramNode::name).contains("Current").doesNotContain("Old");
    }

    @Test void doesNotSpreadUnscopedDocumentDiagnosticAcrossUnrelatedClasses() {
        GrammarProperties properties=properties();
        GrammarDefinitionModel grammar=grammar("diagnostics", """
                grammar Diagnostics
                entry Model: companies+=Company* people+=Person*;
                Company: 'company' name=ID;
                Person: 'person' name=ID;
                """);
        GrammarAnalysisResult base=analyze(properties,grammar,1);
        GrammarSymbol company=base.symbols().stream().filter(symbol->symbol.kind().equals("RULE")&&symbol.name().equals("Company")).findFirst().orElseThrow();
        GrammarDiagnostic local=new GrammarDiagnostic("LOCAL",GrammarSeverity.ERROR,"Local","Local","","","","main",company.range(),"","","","","",List.of(),List.of(),Map.of());
        GrammarAnalysisResult withDiagnostic=new GrammarAnalysisResult(base.requestId(),base.draftRevision(),base.sourceHash(),base.mode(),base.providerInstanceId(),false,List.of(local),base.symbols(),base.semanticCandidate(),base.providerHealth(),base.codeActions(),base.metadata());
        GrammarClassDiagramProjection projection=new GrammarClassDiagramProjectionService(properties).project(grammar,grammar.documents(),withDiagnostic);

        GrammarClassDiagramNode companyNode=projection.nodes().stream().filter(node->node.name().equals("Company")).findFirst().orElseThrow();
        GrammarClassDiagramNode personNode=projection.nodes().stream().filter(node->node.name().equals("Person")).findFirst().orElseThrow();
        assertThat(companyNode.diagnosticCount()).isGreaterThan(0);
        assertThat(personNode.diagnosticCount()).isZero();
    }

    @Test void fallsBackToLastKnownGoodAndRejectsOversizedProjection() {
        GrammarProperties properties=properties();
        GrammarClassDiagramProjectionService service=new GrammarClassDiagramProjectionService(properties);
        GrammarDefinitionModel valid=grammar("stable","grammar Stable\nentry Model: nodes+=Node*;\nNode: 'node' name=ID;\n");
        GrammarClassDiagramProjection good=service.project(valid,analyze(properties,valid,1));
        GrammarDefinitionModel broken=grammar("stable","!!!");
        GrammarClassDiagramProjection fallback=service.project(broken,analyze(properties,broken,2));
        assertThat(good.status()).isEqualTo(GrammarDiagramProjectionStatus.COMPLETE);
        assertThat(fallback.status()).isEqualTo(GrammarDiagramProjectionStatus.LAST_KNOWN_GOOD);
        assertThat(fallback.baseSourceHash()).isEqualTo(good.sourceHash());

        GrammarProperties limited=properties();limited.setMaximumDiagramNodes(1);
        GrammarClassDiagramProjection failed=new GrammarClassDiagramProjectionService(limited).project(valid,analyze(limited,valid,1));
        assertThat(failed.status()).isEqualTo(GrammarDiagramProjectionStatus.FAILED);
        assertThat(failed.nodes()).isEmpty();
    }

    @Test void strictInferenceNeverFallsBackToLastKnownGood() {
        GrammarProperties properties=properties();
        GrammarClassDiagramProjectionService service=new GrammarClassDiagramProjectionService(properties);
        GrammarDefinitionModel valid=grammar("strict","grammar Strict\nentry Model: nodes+=Node*;\nNode: 'node' name=ID;\n");
        service.project(valid,analyze(properties,valid,1));
        GrammarDefinitionModel broken=grammar("strict","!!!");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.inferMetamodelStrict(broken,broken.documents()))
                .isInstanceOf(RuntimeException.class);
    }

    private static GrammarProperties properties(){GrammarProperties value=new GrammarProperties();value.setMaximumDiagramNodes(10_000);value.setMaximumDiagramEdges(50_000);value.setMaximumDiagramFeatures(100_000);value.setMaximumDiagramCacheEntries(8);return value;}
    private static GrammarDefinitionModel grammar(String id,String source){GrammarSourceDocument doc=new GrammarSourceDocument("main","main.langium","langium",source,"",true,false);return new GrammarDefinitionModel("1.0",id,id,GrammarTechnology.LANGIUM,GrammarAuthoringMode.GRAMMAR_FIRST,"","langium-4.3-browser","main","Model",List.of(doc),GrammarSemanticBindingMap.empty(),"",Map.of(),Map.of());}
    private static GrammarAnalysisResult analyze(GrammarProperties properties,GrammarDefinitionModel grammar,long revision){GrammarAnalysisRequest request=new GrammarAnalysisRequest(grammar.id(),revision,GrammarAnalysisMode.NORMAL,List.of("main"),grammar.documents(),grammar.toolchainProfileId());return new GrammarSourceAnalyzer(properties).analyze(grammar,request);}
}
