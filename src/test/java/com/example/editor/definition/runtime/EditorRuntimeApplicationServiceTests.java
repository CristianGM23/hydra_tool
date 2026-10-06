package com.example.editor.definition.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.editor.definition.EditorDefinitionApplicationService;
import com.example.editor.definition.EditorDefinitionCheckReport;
import com.example.editor.definition.EditorDefinitionLoadResult;
import com.example.editor.definition.EditorDefinitionTestFixtures;

@ExtendWith(MockitoExtension.class)
class EditorRuntimeApplicationServiceTests {
    @Mock
    private EditorDefinitionApplicationService definitions;
    private EditorRuntimeApplicationService service;

    @BeforeEach
    void setUp() {
        service = new EditorRuntimeApplicationService(definitions, new EditorDefinitionCompiler());
    }

    @Test
    void compilesSavedDefinitionOnFirstRead() {
        arrange("\"etag-1\"");
        EditorDefinitionCompilationResult result = service.find("people", "people-editor");
        assertThat(result.cacheHit()).isFalse();
        assertThat(result.plan().sourceEtag()).isEqualTo("\"etag-1\"");
    }

    @Test
    void reusesPlanWhenSourceEtagHasNotChanged() {
        arrange("\"etag-1\"");
        service.find("people", "people-editor");
        EditorDefinitionCompilationResult result = service.find("people", "people-editor");
        assertThat(result.cacheHit()).isTrue();
        assertThat(service.cacheSize()).isEqualTo(1);
    }

    @Test
    void forceCompilationDoesNotReportCacheHit() {
        arrange("\"etag-1\"");
        service.find("people", "people-editor");
        EditorDefinitionCompilationResult result = service.compile("people", "people-editor", true);
        assertThat(result.cacheHit()).isFalse();
    }

    @Test
    void changedEtagReplacesStaleCachedPlan() {
        var editor = EditorDefinitionTestFixtures.editor();
        var report = new EditorDefinitionCheckReport(editor.id(), List.of());
        when(definitions.find("people", "people-editor"))
                .thenReturn(new EditorDefinitionLoadResult(editor, "\"etag-1\"", report))
                .thenReturn(new EditorDefinitionLoadResult(editor, "\"etag-2\"", report));
        service.find("people", "people-editor");
        EditorDefinitionCompilationResult result = service.find("people", "people-editor");
        assertThat(result.plan().sourceEtag()).isEqualTo("\"etag-2\"");
        assertThat(service.cacheSize()).isEqualTo(1);
    }

    @Test
    void invalidationRemovesAllVersionsOfOneEditor() {
        arrange("\"etag-1\"");
        service.find("people", "people-editor");
        service.invalidate("people", "people-editor");
        assertThat(service.cacheSize()).isZero();
    }

    @Test
    void delegatesEveryLookupToCanonicalDefinitionService() {
        arrange("\"etag-1\"");
        service.find("people", "people-editor");
        service.find("people", "people-editor");
        verify(definitions, times(2)).find("people", "people-editor");
    }

    private void arrange(String etag) {
        var editor = EditorDefinitionTestFixtures.editor();
        var report = new EditorDefinitionCheckReport(editor.id(), List.of());
        when(definitions.find("people", "people-editor"))
                .thenReturn(new EditorDefinitionLoadResult(editor, etag, report));
    }
}
