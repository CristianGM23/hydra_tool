package com.example.editor.metamodel.importing.updatesite;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.editor.definition.EditorDefinitionApplicationService;
import com.example.editor.definition.EditorDefinitionLoadResult;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UpdateSiteLayerMergeServiceTests {
    @Test
    void requiresAnEtagBeforeAnyPersistentMerge() {
        EditorDefinitionApplicationService editorService = mock(EditorDefinitionApplicationService.class);
        when(editorService.find("library", "library-editor"))
                .thenReturn(new EditorDefinitionLoadResult(null, "etag-current", null));
        UpdateSiteLayerMergeService service = new UpdateSiteLayerMergeService(null, null, editorService,
                new UpdateSiteImportProperties(), JsonMapper.builder().findAndAddModules().build());
        UpdateSiteLayerMergeRequest request = new UpdateSiteLayerMergeRequest("library", "library-editor", "",
                "candidate", false, true, UpdateSiteLayerSelection.all(),
                UpdateSiteMergeDecision.KEEP_EXISTING, Map.of());

        assertThatThrownBy(() -> service.merge(List.of(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expectedEtag");
    }
}
