package com.example.editor.metamodel.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.example.editor.grammar.core.GrammarAuthoringMode;
import com.example.editor.grammar.core.GrammarDefinitionModel;
import com.example.editor.grammar.core.GrammarSemanticBindingMap;
import com.example.editor.grammar.core.GrammarSourceDocument;
import com.example.editor.grammar.core.GrammarTechnology;
import com.example.editor.grammar.persistence.GrammarRepository;
import com.example.editor.grammar.visualization.GrammarClassDiagramProjection;
import com.example.editor.grammar.visualization.GrammarClassDiagramProjectionService;
import com.example.editor.grammar.visualization.GrammarDiagramProjectionStatus;
import com.example.editor.metamodel.canonical.CanonicalMetamodelRepository;
import com.example.editor.metamodel.canonical.StoredMetamodel;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.validation.persistence.StoredCanonicalDocument;

class SemanticContextMetamodelResolverTests {

    @Test
    void resolvesGrammarFirstAsStableTransientMetamodelWithoutPersistingOne(){
        GrammarRepository grammars = mock(GrammarRepository.class);
        GrammarClassDiagramProjectionService projection = mock(GrammarClassDiagramProjectionService.class);
        GrammarDefinitionModel grammar = grammar("main-language");
        MetaModel inferred = new MetaModel("2.1", "inferred-main-language", "Generated", List.of(), List.of(), List.of(), List.of(), Map.of("source", "grammar"));
        when(grammars.findById("main-language")).thenReturn(Optional.of(new StoredCanonicalDocument<>(grammar, "grammar-etag", null)));
        when(projection.project(grammar, (com.example.editor.grammar.core.GrammarAnalysisResult)null)).thenReturn(new GrammarClassDiagramProjection(
                grammar.id(), 0, "source-hash", "source-hash", GrammarDiagramProjectionStatus.COMPLETE,
                inferred, List.of(), List.of(), List.of(), "projection-hash", Map.of()));

        SemanticContextMetamodelResolver resolver = new SemanticContextMetamodelResolver(grammars, projection);
        MetamodelLoadResult result = resolver.resolve("grammar.main-language").orElseThrow();

        assertThat(result.metamodel().id()).isEqualTo("grammar.main-language");
        assertThat(result.metamodel().name()).isEqualTo("Main language");
        assertThat(result.metamodel().annotations()).containsEntry("hydra.semanticContext.kind", "GRAMMAR")
                .containsEntry("hydra.semanticContext.transient", "true")
                .containsEntry("hydra.grammar.id", "main-language")
                .containsEntry("hydra.grammar.sourceHash", "source-hash");
        assertThat(result.etag()).isEqualTo("grammar-etag");
        assertThat(resolver.resolve("main-language")).isEmpty();
    }

    @Test
    void canonicalApplicationFallsBackToGrammarProjectionOnlyWhenCanonicalDocumentIsAbsent(){
        CanonicalMetamodelRepository canonical = mock(CanonicalMetamodelRepository.class);
        GrammarRepository grammars = mock(GrammarRepository.class);
        GrammarClassDiagramProjectionService projection = mock(GrammarClassDiagramProjectionService.class);
        GrammarDefinitionModel grammar = grammar("main-language");
        MetaModel inferred = new MetaModel("2.1", "inferred", "Generated", List.of(), List.of(), List.of(), List.of(), Map.of());
        when(canonical.findById("grammar.main-language")).thenReturn(Optional.empty());
        when(grammars.findById("main-language")).thenReturn(Optional.of(new StoredCanonicalDocument<>(grammar, "grammar-etag", null)));
        when(projection.project(grammar, (com.example.editor.grammar.core.GrammarAnalysisResult)null)).thenReturn(new GrammarClassDiagramProjection(
                grammar.id(), 0, "hash", "hash", GrammarDiagramProjectionStatus.COMPLETE,
                inferred, List.of(), List.of(), List.of(), "projection", Map.of()));
        SemanticContextMetamodelResolver resolver = new SemanticContextMetamodelResolver(grammars, projection);
        CanonicalMetamodelApplicationService application = new CanonicalMetamodelApplicationService(
                canonical, new NeutralMetamodelValidator(), null, Optional.of(resolver));

        MetamodelLoadResult result = application.findById("grammar.main-language");

        assertThat(result.metamodel().id()).isEqualTo("grammar.main-language");
        verify(canonical, never()).create(org.mockito.ArgumentMatchers.any());
        verify(canonical, never()).replace(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void persistedCanonicalMetamodelWinsEvenInsideReservedPrefix(){
        CanonicalMetamodelRepository canonical = mock(CanonicalMetamodelRepository.class);
        GrammarRepository grammars = mock(GrammarRepository.class);
        GrammarClassDiagramProjectionService projection = mock(GrammarClassDiagramProjectionService.class);
        MetaModel persisted = new MetaModel("2.1", "grammar.main-language", "Persisted", List.of(), List.of(), List.of(), List.of(), Map.of("kind", "canonical"));
        when(canonical.findById("grammar.main-language")).thenReturn(Optional.of(new StoredMetamodel(persisted, "canonical-etag", null)));
        SemanticContextMetamodelResolver resolver = new SemanticContextMetamodelResolver(grammars, projection);
        CanonicalMetamodelApplicationService application = new CanonicalMetamodelApplicationService(
                canonical, new NeutralMetamodelValidator(), null, Optional.of(resolver));

        MetamodelLoadResult result = application.findById("grammar.main-language");

        assertThat(result.metamodel()).isEqualTo(persisted);
        assertThat(result.etag()).isEqualTo("canonical-etag");
        verify(grammars, never()).findById("main-language");
    }

    private static GrammarDefinitionModel grammar(String id){
        GrammarSourceDocument document = new GrammarSourceDocument("main", "main.langium", "langium",
                "grammar Main entry Model: elements+=Element*; Element: 'element' name=ID; hidden WS; terminal ID;",
                "", true, false);
        return new GrammarDefinitionModel("1.0", id, "Main language", GrammarTechnology.LANGIUM,
                GrammarAuthoringMode.GRAMMAR_FIRST, "", "langium-default", "main", "Model", List.of(document),
                GrammarSemanticBindingMap.empty(), "", Map.of(), Map.of());
    }
}
