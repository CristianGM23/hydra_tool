package com.example.editor.textual.roundtrip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.example.editor.grammar.application.GrammarCompilationService;
import com.example.editor.grammar.core.CompilationStatus;
import com.example.editor.grammar.core.GeneratedGrammarProject;
import com.example.editor.grammar.core.GrammarRuntimeDescriptor;
import com.example.editor.grammar.core.GrammarSemanticBindingMap;
import com.example.editor.grammar.core.GrammarTechnology;
import com.example.editor.grammar.runtime.GrammarCompilationSnapshot;
import com.example.editor.textual.TextualModelProperties;
import com.example.editor.textual.definition.TextualEditorDefinitionService;
import com.example.editor.textual.persistence.TextualModelSessionRepository;
import com.example.editor.textual.runtime.NeutralTextualModelParser;
import com.example.editor.textual.runtime.ParseTextualModelRequest;
import com.example.editor.textual.runtime.TextualModelSession;
import com.example.editor.textual.runtime.TextualModelSessionService;
import com.example.editor.textual.runtime.TextualSharedLayerRuntimeService;
import com.example.editor.textual.source.TextualModelSourceDocument;
import com.example.editor.validation.persistence.StoredCanonicalDocument;

class TextualRuntimeResilienceTests {
    @Test
    void treatsAnExactSameRevisionRetryAsIdempotentWithoutParsingOrWriting(){
        TextualModelSourceDocument document=document("model","model.xtext.model","node n1 {}",7);
        StoredCanonicalDocument<TextualModelSession> stored=stored(session(7,List.of(document)));
        TextualModelSessionRepository repository=mock(TextualModelSessionRepository.class);
        GrammarCompilationService compilations=mock(GrammarCompilationService.class);
        when(repository.find("session")).thenReturn(Optional.of(stored));
        TextualModelSessionService service=service(repository,compilations);

        assertThat(service.parse("session",new ParseTextualModelRequest(7,List.of(document)),stored.etag())).isSameAs(stored);
        verify(compilations,never()).find(org.mockito.ArgumentMatchers.anyString());
        verify(repository,never()).replace(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void rejectsSameRevisionWithDifferentContentAndOlderRevisionsBeforeParsing(){
        TextualModelSourceDocument original=document("model","model.xtext.model","node n1 {}",7);
        TextualModelSourceDocument changed=document("model","model.xtext.model","node n2 {}",7);
        StoredCanonicalDocument<TextualModelSession> stored=stored(session(7,List.of(original)));
        TextualModelSessionRepository repository=mock(TextualModelSessionRepository.class);
        GrammarCompilationService compilations=mock(GrammarCompilationService.class);
        when(repository.find("session")).thenReturn(Optional.of(stored));
        TextualModelSessionService service=service(repository,compilations);

        assertThatThrownBy(()->service.parse("session",new ParseTextualModelRequest(7,List.of(changed)),stored.etag()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("same revision");
        assertThatThrownBy(()->service.parse("session",new ParseTextualModelRequest(6,List.of(original)),stored.etag()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Stale textual draft revision");
        verify(compilations,never()).find(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void textualDocumentsRejectTraversalAndParserHonoursCallerCancellation(){
        assertThatThrownBy(()->new TextualModelSourceDocument("evil","../outside.model","node n1 {}",1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("relative and portable");

        NeutralTextualModelParser parser=new NeutralTextualModelParser();
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(()->parser.parse(1,List.of(document("model","model.xtext.model","node n1 {}",1)),snapshot()))
                    .isInstanceOf(java.util.concurrent.CancellationException.class);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void sessionSnapshotsDefensivelyCopyDocumentsAndMetadata(){
        java.util.ArrayList<TextualModelSourceDocument> documents=new java.util.ArrayList<>();
        documents.add(document("model","model.xtext.model","node n1 {}",1));
        java.util.LinkedHashMap<String,String> metadata=new java.util.LinkedHashMap<>();metadata.put("lastKnownGood","revision:1");
        TextualModelSession session=new TextualModelSession("session","editor","grammar","snapshot",documents,1,1,"hash",null,List.of(),null,0,List.of(),Instant.now(),metadata);
        documents.clear();metadata.put("lastKnownGood","corrupted");
        assertThat(session.documents()).hasSize(1);
        assertThat(session.metadata()).containsEntry("lastKnownGood","revision:1");
        assertThatThrownBy(()->session.metadata().put("x","y")).isInstanceOf(UnsupportedOperationException.class);
    }

    private static TextualModelSessionService service(TextualModelSessionRepository repository,GrammarCompilationService compilations){
        return new TextualModelSessionService(repository,mock(TextualEditorDefinitionService.class),new TextualModelProperties(),compilations,mock(TextualSharedLayerRuntimeService.class));
    }
    private static TextualModelSession session(long revision,List<TextualModelSourceDocument> documents){
        return new TextualModelSession("session","editor","grammar","snapshot",documents,revision,revision,"hash",null,List.of(),null,0,List.of(),Instant.now(),Map.of("lastKnownGood","revision:"+revision));
    }
    private static StoredCanonicalDocument<TextualModelSession> stored(TextualModelSession session){return new StoredCanonicalDocument<>(session,"\"etag-1\"",Path.of("session.json"));}
    private static TextualModelSourceDocument document(String id,String path,String content,long revision){return new TextualModelSourceDocument(id,path,content,revision);}
    private static GrammarCompilationSnapshot snapshot(){
        return new GrammarCompilationSnapshot("snapshot","grammar",1,"source-hash",GrammarTechnology.XTEXT,"provider","toolchain",CompilationStatus.SUCCESS,List.of(),GrammarSemanticBindingMap.empty(),new GrammarRuntimeDescriptor("","",List.of(),"","",Map.of()),new GeneratedGrammarProject("",Map.of(),Map.of()),Map.of(),Instant.now(),Map.of("immutable","true"));
    }
}
