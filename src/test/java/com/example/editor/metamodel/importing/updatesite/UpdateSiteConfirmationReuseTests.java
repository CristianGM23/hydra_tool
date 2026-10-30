package com.example.editor.metamodel.importing.updatesite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.TransformationWarning;
import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.importing.AutomaticMetamodelDiagramLayoutService;
import com.example.editor.metamodel.importing.MetamodelImportRoutingPolicy;
import com.example.editor.metamodel.importing.ImportedDiagramRelationNormalizer;
import com.example.editor.metamodel.importing.ImportedMetamodelNormalizer;
import com.example.editor.metamodel.importing.MetamodelImportDetector;
import com.example.editor.metamodel.importing.MetamodelImportOptions;
import com.example.editor.metamodel.importing.MetamodelImportPreview;
import com.example.editor.metamodel.importing.MetamodelImportProperties;
import com.example.editor.metamodel.importing.MetamodelImportSaveResult;
import com.example.editor.metamodel.importing.MetamodelImportService;
import com.example.editor.metamodel.importing.importers.NeutralJsonMetamodelImporter;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;

import tools.jackson.databind.json.JsonMapper;

/**
 * T-UPD (OBS-MM-IMPORT-PREVIEW-DUPLICATE-LAYOUT): confirming an update-site import persists the preview its analysis already computed instead
 * of re-importing it (which repeated parsing, normalisation and the whole layout), keeps the source-file annotation the re-import wrote, and
 * applies the strict rule to the preview's warnings exactly as the re-import did.
 */
class UpdateSiteConfirmationReuseTests {
    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final MetamodelImportProperties properties = new MetamodelImportProperties();
    private final MetamodelImportService real = new MetamodelImportService(properties, new MetamodelImportDetector(), new ImportedMetamodelNormalizer(),
            new AutomaticMetamodelDiagramLayoutService(null, properties, new MetamodelImportRoutingPolicy(properties)), new ImportedDiagramRelationNormalizer(),
            new NeutralMetamodelValidator(), null, null, null, List.of(new NeutralJsonMetamodelImporter(mapper)));

    @Test
    void theConfirmationPersistsTheAnalysedPreviewWithoutReimportingIt() throws Exception {
        MetamodelImportService imports = spy(real);
        MetamodelImportSaveResult saved = new MetamodelImportSaveResult("renamed-target", "\"1\"", null);
        doReturn(saved).when(imports).importPrepared(any(MetamodelImportPreview.class), anyBoolean());
        doReturn(saved).when(imports).importAndSave(anyString(), anyString(), any(byte[].class), any(MetamodelImportOptions.class));
        MetamodelImportPreview preview = preview("renamed-target", List.of());

        service(imports).importPrepared(analysis(preview), new UpdateSiteImportOptions("renamed-target", false, false, "", true, true, true, false));

        verify(imports, never()).importAndSave(anyString(), anyString(), any(byte[].class), any(MetamodelImportOptions.class));
        ArgumentCaptor<MetamodelImportPreview> persisted = ArgumentCaptor.forClass(MetamodelImportPreview.class);
        verify(imports).importPrepared(persisted.capture(), eq(false));
        assertThat(persisted.getValue().metamodel().annotations()).containsEntry("import:sourceFile", "renamed-target.metamodel.json");
        assertThat(persisted.getValue().diagram()).isSameAs(preview.diagram());
    }

    @Test
    void strictModeRejectsALossyPreviewLikeTheReimportDidAndSavesNothing() throws Exception {
        MetamodelImportService imports = spy(real);
        List<TransformationWarning> lossy = List.of(new TransformationWarning("IMPORT_LOSSY_GRAMMAR_FEATURE_FLAGS", "$.classes", "flags were defaulted"));
        MetamodelImportPreview preview = preview("", lossy);

        assertThatThrownBy(() -> service(imports).importPrepared(analysis(preview), new UpdateSiteImportOptions("", false, true, "", true, true, true, false)))
                .isInstanceOf(MetamodelDesignException.class).hasMessageContaining("Strict metamodel import rejected semantic loss").hasMessageContaining("IMPORT_LOSSY_GRAMMAR_FEATURE_FLAGS");
        verify(imports, never()).importPrepared(any(MetamodelImportPreview.class), anyBoolean());
    }

    private MetamodelImportPreview preview(String targetId, List<TransformationWarning> extraWarnings) throws Exception {
        byte[] source;
        try(InputStream in = getClass().getResourceAsStream("/metamodel-corpus/universityflow-medio.metamodel.json")) { source = in.readAllBytes(); }
        MetaModel recovered = mapper.readValue(source, MetaModel.class);
        MetamodelImportPreview preview = real.preview(recovered.id() + ".metamodel.json", "application/json", source,
                new MetamodelImportOptions("json", targetId.isBlank() ? recovered.id() : targetId, false, true, true, false));
        List<TransformationWarning> warnings = new ArrayList<>(preview.warnings());
        warnings.addAll(extraWarnings);
        return new MetamodelImportPreview(preview.detectedFormat(), preview.supportLevel(), preview.metamodel(), preview.targets(), preview.diagram(), warnings, preview.statistics());
    }

    private static UpdateSiteImportAnalysis analysis(MetamodelImportPreview preview) {
        UpdateSiteImportAnalysis analysis = mock(UpdateSiteImportAnalysis.class);
        when(analysis.importable()).thenReturn(true);
        when(analysis.metamodelPreview()).thenReturn(preview);
        when(analysis.analysisId()).thenReturn("p2-test");
        return analysis;
    }

    private UpdateSiteImportApplicationService service(MetamodelImportService imports) {
        return new UpdateSiteImportApplicationService(null, null, null, null, null, null, null, null, imports, null, null, null, null, null, mapper);
    }
}
