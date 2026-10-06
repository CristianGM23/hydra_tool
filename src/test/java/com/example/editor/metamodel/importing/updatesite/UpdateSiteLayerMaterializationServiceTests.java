package com.example.editor.metamodel.importing.updatesite;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.editor.definition.EditorDefinitionChecker;
import com.example.editor.definition.EditorDefinitionLoadResult;
import com.example.editor.project.recovery.ExternalProjectMaterializationResult;
import com.example.editor.project.recovery.ExternalProjectSemanticMaterializationService;
import com.example.editor.metamodel.importing.MetamodelImportSaveResult;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.importing.MetamodelImportPreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdateSiteLayerMaterializationServiceTests {
    private final UpdateSiteLayerMaterializationService service = new UpdateSiteLayerMaterializationService(
            null, new EditorDefinitionChecker(), null, null);

    @Test
    void previewsSelectedRecoveredLayersAsACanonicalEditorWithoutPersisting() {
        MetaModel metamodel = new MetaModel("2.1", "library", "Library",
                List.of(new MetaClass("Book", "Book", MetaClassKind.CLASS, List.of(), List.of(), Map.of())),
                List.of(), List.of(), List.of(), Map.of());
        MetamodelImportPreview metamodelPreview = new MetamodelImportPreview("ECORE", "FULL", metamodel,
                null, null, List.of(), null);
        UpdateSitePaletteToolDraft tool = new UpdateSitePaletteToolDraft("create-book", "Create book", "NODE",
                "Book", "library.gmftool", UpdateSiteConfidence.DERIVED);
        UpdateSitePaletteGroupDraft group = new UpdateSitePaletteGroupDraft("objects", "Objects", 0,
                List.of(tool), "library.gmftool", UpdateSiteConfidence.DERIVED);
        UpdateSiteRepresentationDraft representation = new UpdateSiteRepresentationDraft("main", "Library diagram",
                "GMF", "DIAGRAM", List.of("Book"), "library.gmfgraph", UpdateSiteConfidence.DERIVED, List.of());
        UpdateSiteMappingDraft mapping = new UpdateSiteMappingDraft("book-node", "Book", "BookFigure", "NODE",
                "library.gmfmap", UpdateSiteConfidence.DERIVED, "");
        UpdateSiteImportAnalysis analysis = new UpdateSiteImportAnalysis("analysis", Instant.now(), new UpdateSiteRepositorySummary("site", "f", true, true, 1, 10, List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of()), List.of(), "", metamodelPreview, List.of(), new UpdateSiteRecoveredLayers(List.of(group), List.of(representation), List.of(mapping), List.of()), true, List.of(), UpdateSiteImportDashboard.empty(), List.of(), List.of(), List.of(), UpdateSiteReconciliationReport.empty(), UpdateSiteReleaseComparison.empty(), UpdateSiteSemanticRecovery.empty(), UpdateSiteAnalysisPerformance.empty());
        UpdateSiteLayerMaterializationRequest request = new UpdateSiteLayerMaterializationRequest("library", "",
                "library-editor", "Library editor", false, false, UpdateSiteLayerSelection.all());

        UpdateSiteLayerMaterializationPreview preview = service.preview(analysis, request);

        assertThat(preview.applicable()).isTrue();
        assertThat(preview.editorDefinition().id()).isEqualTo("library-editor");
        assertThat(preview.selectedPaletteGroups()).isEqualTo(1);
        assertThat(preview.selectedTools()).isEqualTo(1);
        assertThat(preview.selectedRepresentations()).isEqualTo(1);
        assertThat(preview.editorDefinition().palette().tools()).singleElement().satisfies(editorTool ->
                assertThat(editorTool.label()).isEqualTo("Create book"));
        assertThat(preview.editorDefinition().representations()).singleElement().satisfies(value ->
                assertThat(value.nodeMappings()).extracting(item -> item.targetClassId()).contains("Book"));
    }
    @Test
    void materializesOnlyExplicitlySelectedPaletteTools() {
        MetaModel metamodel = new MetaModel("2.1", "library", "Library",
                List.of(new MetaClass("Book", "Book", MetaClassKind.CLASS, List.of(), List.of(), Map.of()),
                        new MetaClass("Author", "Author", MetaClassKind.CLASS, List.of(), List.of(), Map.of())),
                List.of(), List.of(), List.of(), Map.of());
        MetamodelImportPreview metamodelPreview = new MetamodelImportPreview("ECORE", "FULL", metamodel,
                null, null, List.of(), null);
        UpdateSitePaletteToolDraft book = new UpdateSitePaletteToolDraft("create-book", "Create book", "NODE",
                "Book", "library.gmftool", UpdateSiteConfidence.DERIVED);
        UpdateSitePaletteToolDraft author = new UpdateSitePaletteToolDraft("create-author", "Create author", "NODE",
                "Author", "library.gmftool", UpdateSiteConfidence.DERIVED);
        UpdateSitePaletteGroupDraft group = new UpdateSitePaletteGroupDraft("objects", "Objects", 0,
                List.of(book, author), "library.gmftool", UpdateSiteConfidence.DERIVED);
        UpdateSiteRepresentationDraft representation = new UpdateSiteRepresentationDraft("main", "Library diagram",
                "GMF", "DIAGRAM", List.of("Book"), "library.gmfgraph", UpdateSiteConfidence.DERIVED, List.of());
        UpdateSiteImportAnalysis analysis = new UpdateSiteImportAnalysis("analysis", Instant.now(), new UpdateSiteRepositorySummary("site", "f", true, true, 1, 10, List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of()), List.of(), "", metamodelPreview, List.of(), new UpdateSiteRecoveredLayers(List.of(group), List.of(representation), List.of(), List.of()), true, List.of(), UpdateSiteImportDashboard.empty(), List.of(), List.of(), List.of(), UpdateSiteReconciliationReport.empty(), UpdateSiteReleaseComparison.empty(), UpdateSiteSemanticRecovery.empty(), UpdateSiteAnalysisPerformance.empty());
        UpdateSiteLayerSelection selection = new UpdateSiteLayerSelection(List.of("objects"),
                List.of("create-book"), List.of("main"), List.of(), List.of(), true);
        UpdateSiteLayerMaterializationRequest request = new UpdateSiteLayerMaterializationRequest("library", "",
                "library-editor", "Library editor", false, false, selection);

        UpdateSiteLayerMaterializationPreview preview = service.preview(analysis, request);

        assertThat(preview.selectedPaletteGroups()).isEqualTo(1);
        assertThat(preview.selectedTools()).isEqualTo(1);
        assertThat(preview.editorDefinition().palette().tools()).singleElement().satisfies(editorTool ->
                assertThat(editorTool.label()).isEqualTo("Create book"));
    }

    @Test
    void keepsAnExplicitlyEmptySelectionEmptyWithoutGeneratingADefaultRepresentation() {
        MetaModel metamodel = new MetaModel("2.1", "library", "Library",
                List.of(new MetaClass("Book", "Book", MetaClassKind.CLASS, List.of(), List.of(), Map.of())),
                List.of(), List.of(), List.of(), Map.of());
        MetamodelImportPreview metamodelPreview = new MetamodelImportPreview("ECORE", "FULL", metamodel,
                null, null, List.of(), null);
        UpdateSitePaletteToolDraft tool = new UpdateSitePaletteToolDraft("create-book", "Create book", "NODE",
                "Book", "library.gmftool", UpdateSiteConfidence.DERIVED);
        UpdateSitePaletteGroupDraft group = new UpdateSitePaletteGroupDraft("objects", "Objects", 0,
                List.of(tool), "library.gmftool", UpdateSiteConfidence.DERIVED);
        UpdateSiteImportAnalysis analysis = new UpdateSiteImportAnalysis("analysis", Instant.now(), new UpdateSiteRepositorySummary("site", "f", true, true, 1, 10, List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of()), List.of(), "", metamodelPreview, List.of(), new UpdateSiteRecoveredLayers(List.of(group), List.of(), List.of(), List.of()), true, List.of(), UpdateSiteImportDashboard.empty(), List.of(), List.of(), List.of(), UpdateSiteReconciliationReport.empty(), UpdateSiteReleaseComparison.empty(), UpdateSiteSemanticRecovery.empty(), UpdateSiteAnalysisPerformance.empty());
        UpdateSiteLayerMaterializationRequest request = new UpdateSiteLayerMaterializationRequest("library", "",
                "library-editor", "Library editor", false, false,
                new UpdateSiteLayerSelection(List.of("objects"), List.of(), List.of(), List.of(), List.of(), true));

        UpdateSiteLayerMaterializationPreview preview = service.preview(analysis, request);

        assertThat(preview.selectedTools()).isZero();
        assertThat(preview.selectedRepresentations()).isZero();
        assertThat(preview.editorDefinition().palette().groups()).isEmpty();
    }

    @Test
    void persistedMaterializationDelegatesToTheDeepExternalPipelineInsteadOfSavingTheLossyDraftEditor() {
        MetaModel metamodel = new MetaModel("2.1", "library", "Library",
                List.of(new MetaClass("Book", "Book", MetaClassKind.CLASS, List.of(), List.of(), Map.of())),
                List.of(), List.of(), List.of(), Map.of());
        MetamodelImportPreview metamodelPreview = new MetamodelImportPreview("ECORE", "FULL", metamodel,
                null, null, List.of(), null);
        UpdateSiteRepresentationDraft representation = new UpdateSiteRepresentationDraft("main", "Library diagram",
                "SIRIUS", "DIAGRAM", List.of("Book"), "design/library.odesign", UpdateSiteConfidence.DERIVED, List.of());
        UpdateSiteImportAnalysis analysis = new UpdateSiteImportAnalysis("analysis", Instant.now(),
                new UpdateSiteRepositorySummary("site", "f", true, true, 1, 10, List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of()), List.of(), "", metamodelPreview, List.of(),
                new UpdateSiteRecoveredLayers(List.of(), List.of(representation), List.of(), List.of()), true, List.of(),
                UpdateSiteImportDashboard.empty(), List.of(), List.of(), List.of(), UpdateSiteReconciliationReport.empty(),
                UpdateSiteReleaseComparison.empty(), UpdateSiteSemanticRecovery.empty(), UpdateSiteAnalysisPerformance.empty());

        UpdateSiteImportApplicationService imports = mock(UpdateSiteImportApplicationService.class);
        com.example.editor.definition.EditorDefinitionApplicationService editors = mock(com.example.editor.definition.EditorDefinitionApplicationService.class);
        com.example.editor.metamodel.application.CanonicalMetamodelApplicationService metamodels = mock(com.example.editor.metamodel.application.CanonicalMetamodelApplicationService.class);
        UpdateSiteArchiveReader reader = mock(UpdateSiteArchiveReader.class);
        ExternalProjectSemanticMaterializationService external = mock(ExternalProjectSemanticMaterializationService.class);
        when(imports.analyze(any(), any())).thenReturn(analysis);
        when(imports.importPrepared(eq(analysis), any())).thenReturn(new UpdateSiteImportResult("analysis",
                new MetamodelImportSaveResult("library", "m-etag", metamodelPreview), "", analysis.recoveredLayers()));
        when(reader.read(any())).thenReturn(new UpdateSiteRepositoryContent(Map.of("design/library.odesign", "odesign".getBytes()), "repo", 7));
        when(external.materializeExisting(eq(metamodel), any(), isNull())).thenReturn(new ExternalProjectMaterializationResult(
                false, "library", List.of("rich-sirius-editor"), 3, 4, 5, 1,
                List.of("design/library.odesign", "validation/library.evl"), List.of("deep pipeline used")));
        var richEditor = new com.example.editor.definition.EditorDefinitionModel("1.0", "rich-sirius-editor", "library",
                "Rich Sirius editor", List.of(), null, List.of(), null, Map.of());
        when(editors.find("library", "rich-sirius-editor")).thenReturn(new EditorDefinitionLoadResult(richEditor, "e-etag",
                new com.example.editor.definition.EditorDefinitionCheckReport("rich-sirius-editor", List.of())));

        UpdateSiteLayerMaterializationService materializer = new UpdateSiteLayerMaterializationService(imports,
                new EditorDefinitionChecker(), editors, metamodels, reader, external);
        UpdateSiteLayerMaterializationResult result = materializer.materialize(
                List.of(new UpdateSiteInputFile("site.zip", new byte[]{1})),
                new UpdateSiteLayerMaterializationRequest("library", "", "ignored-draft-editor", "Recovered", false, true,
                        UpdateSiteLayerSelection.all()));

        assertThat(result.editorId()).isEqualTo("rich-sirius-editor");
        assertThat(result.editorEtag()).isEqualTo("e-etag");
        assertThat(result.diagnostics()).contains("deep pipeline used",
                "Layer materialization: 7 validation rule(s), 5 quick fix(es), 1 rich editor(s).");
        verify(external).materializeExisting(eq(metamodel), any(), isNull());
        verify(editors, never()).create(eq("library"), any());
        verify(imports).importPrepared(eq(analysis), any());
    }

    @Test
    void refusesLossyFallbackWhenASelectedSiriusDefinitionCannotBeMaterializedDeeply() {
        MetaModel metamodel = new MetaModel("2.1", "library", "Library",
                List.of(new MetaClass("Book", "Book", MetaClassKind.CLASS, List.of(), List.of(), Map.of())),
                List.of(), List.of(), List.of(), Map.of());
        MetamodelImportPreview metamodelPreview = new MetamodelImportPreview("ECORE", "FULL", metamodel,
                null, null, List.of(), null);
        UpdateSiteRepresentationDraft representation = new UpdateSiteRepresentationDraft("main", "Library diagram",
                "SIRIUS_DESKTOP", "DIAGRAM", List.of("Book"), "design/library.odesign", UpdateSiteConfidence.DERIVED, List.of());
        UpdateSiteImportAnalysis analysis = new UpdateSiteImportAnalysis("analysis", Instant.now(),
                new UpdateSiteRepositorySummary("site", "f", true, true, 1, 10, List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of()), List.of(), "", metamodelPreview, List.of(),
                new UpdateSiteRecoveredLayers(List.of(), List.of(representation), List.of(), List.of()), true, List.of(),
                UpdateSiteImportDashboard.empty(), List.of(), List.of(), List.of(), UpdateSiteReconciliationReport.empty(),
                UpdateSiteReleaseComparison.empty(), UpdateSiteSemanticRecovery.empty(), UpdateSiteAnalysisPerformance.empty());

        UpdateSiteImportApplicationService imports = mock(UpdateSiteImportApplicationService.class);
        com.example.editor.definition.EditorDefinitionApplicationService editors = mock(com.example.editor.definition.EditorDefinitionApplicationService.class);
        com.example.editor.metamodel.application.CanonicalMetamodelApplicationService metamodels = mock(com.example.editor.metamodel.application.CanonicalMetamodelApplicationService.class);
        UpdateSiteArchiveReader reader = mock(UpdateSiteArchiveReader.class);
        ExternalProjectSemanticMaterializationService external = mock(ExternalProjectSemanticMaterializationService.class);
        when(imports.analyze(any(), any())).thenReturn(analysis);
        when(imports.importPrepared(eq(analysis), any())).thenReturn(new UpdateSiteImportResult("analysis",
                new MetamodelImportSaveResult("library", "m-etag", metamodelPreview), "", analysis.recoveredLayers()));
        when(reader.read(any())).thenReturn(new UpdateSiteRepositoryContent(Map.of("design/library.odesign", "odesign".getBytes()), "repo", 7));
        when(external.materializeExisting(eq(metamodel), any(), isNull())).thenReturn(ExternalProjectMaterializationResult.empty(List.of("deep adapter rejected fixture")));
        when(editors.list("library")).thenReturn(List.of());

        UpdateSiteLayerMaterializationService materializer = new UpdateSiteLayerMaterializationService(imports,
                new EditorDefinitionChecker(), editors, metamodels, reader, external);

        assertThatThrownBy(() -> materializer.materialize(List.of(new UpdateSiteInputFile("site.zip", new byte[]{1})),
                new UpdateSiteLayerMaterializationRequest("library", "", "generic-editor", "Recovered", false, true,
                        UpdateSiteLayerSelection.all())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("full fidelity");
        verify(editors, never()).create(eq("library"), any());
        verify(metamodels).delete("library", "m-etag");
    }

    @Test
    void rollbackRemovesEveryCanonicalLayerBeforeDeletingTheImportedMetamodel() {
        UpdateSiteImportApplicationService imports = mock(UpdateSiteImportApplicationService.class);
        com.example.editor.definition.EditorDefinitionApplicationService editors = mock(com.example.editor.definition.EditorDefinitionApplicationService.class);
        com.example.editor.metamodel.application.CanonicalMetamodelApplicationService metamodels = mock(com.example.editor.metamodel.application.CanonicalMetamodelApplicationService.class);
        com.example.editor.validation.validation.ValidationModelApplicationService validations = mock(com.example.editor.validation.validation.ValidationModelApplicationService.class);
        com.example.editor.validation.action.ActionModelApplicationService actions = mock(com.example.editor.validation.action.ActionModelApplicationService.class);
        com.example.editor.validation.expression.ExpressionModelApplicationService expressions = mock(com.example.editor.validation.expression.ExpressionModelApplicationService.class);
        when(editors.list("library")).thenReturn(List.of(
                new com.example.editor.definition.EditorDefinitionSummary("editor-a", "library", "A", 1, 0, 0, "ea"),
                new com.example.editor.definition.EditorDefinitionSummary("editor-b", "library", "B", 2, 1, 0, "eb")));
        when(validations.find("library")).thenReturn(new com.example.editor.validation.validation.ValidationModelLoadResult(null, "vv"));
        when(actions.find("library")).thenReturn(new com.example.editor.validation.action.ActionModelLoadResult(null, "aa"));
        when(expressions.find("library")).thenReturn(new com.example.editor.validation.expression.ExpressionModelLoadResult(null, "xx"));

        UpdateSiteLayerMaterializationService materializer = new UpdateSiteLayerMaterializationService(imports,
                new EditorDefinitionChecker(), editors, metamodels, null, null, validations, actions, expressions);
        UpdateSiteLayerMaterializationRollbackResult result = materializer.rollback(
                new UpdateSiteLayerMaterializationRollbackRequest("library", "mm", "", ""));

        assertThat(result.editorDeleted()).isTrue();
        assertThat(result.metamodelDeleted()).isTrue();
        verify(editors).delete("library", "editor-a", "ea");
        verify(editors).delete("library", "editor-b", "eb");
        verify(validations).delete("library", "vv");
        verify(actions).delete("library", "aa");
        verify(expressions).delete("library", "xx");
        verify(metamodels).delete("library", "mm");
    }

}
