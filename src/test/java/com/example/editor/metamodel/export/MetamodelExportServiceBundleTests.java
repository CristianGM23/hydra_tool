package com.example.editor.metamodel.export;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;

import org.eclipse.emf.ecore.EPackage;
import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.adapter.ecore.EcoreMetamodelAdapter;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.TransformationResult;
import com.example.editor.metamodel.design.MetamodelDesignException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba el ZIP con formatos seleccionados.
 */
class MetamodelExportServiceBundleTests {

    @Test
    void includesOnlySelectedFormats() throws Exception {
        MetamodelExportService service = service();
        byte[] content = service.exportBundle("company", List.of("xml", "plantuml"), MetamodelExportOptions.defaults()).content();

        assertThat(entries(content)).containsExactly("company.xml", "company.puml");
    }

    @Test
    void removesRepeatedFormatsFromBundle() throws Exception {
        MetamodelExportService service = service();
        byte[] content = service.exportBundle("company", List.of("xml", "xml", "plantuml"), MetamodelExportOptions.defaults()).content();

        assertThat(entries(content)).containsExactly("company.xml", "company.puml");
    }


    @Test
    void canonicalizesBundleOrderIndependentlyOfRequestedFormatOrder() throws Exception {
        MetamodelExportService service = service();
        byte[] canonical = service.exportBundle("company", List.of("xml", "plantuml"), MetamodelExportOptions.defaults()).content();
        byte[] reversed = service.exportBundle("company", List.of("plantuml", "xml"), MetamodelExportOptions.defaults()).content();

        assertThat(entries(reversed)).containsExactly("company.xml", "company.puml");
        assertThat(reversed).isEqualTo(canonical);
    }

    @Test
    void rejectsEmptyBundleSelection() {
        MetamodelExportService service = service();

        assertThatThrownBy(() -> service.exportBundle("company", List.of(), MetamodelExportOptions.defaults())).isInstanceOf(MetamodelDesignException.class).hasMessageContaining("At least one export format");
    }

    @Test
    void usesNeutralAdapterForEcore() {
        StubEcoreAdapter adapter = new StubEcoreAdapter();
        NeutralMetamodelExportContext context = CanonicalExportTestFixtures.context();
        MetamodelExportService service = new MetamodelExportService(id -> context, adapter, List.of(), List.of());
        MetamodelExportArtifact artifact = service.export("company", "ecore");

        assertThat(new String(artifact.content(), StandardCharsets.UTF_8)).isEqualTo("<ecore/>");
        assertThat(adapter.received).isSameAs(context.neutralMetamodel());
    }

    private MetamodelExportService service() {
        NeutralMetamodelExportContext context = CanonicalExportTestFixtures.context();
        return new MetamodelExportService(id -> context, new StubEcoreAdapter(), List.of(new NeutralXmlMetamodelExporter(), new PlantUmlMetamodelExporter()), List.of());
    }

    private List<String> entries(byte[] content) throws Exception {
        List<String> result = new ArrayList<>();

        try(ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            var entry = zip.getNextEntry();

            while(entry != null) {
                result.add(entry.getName());
                entry = zip.getNextEntry();
            }
        }

        return result;
    }

    private static class StubEcoreAdapter implements EcoreMetamodelAdapter {

        private MetaModel received;

        @Override
        public TransformationResult<EPackage> toEPackage(MetaModel metamodel) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TransformationResult<byte[]> toBytes(MetaModel metamodel) {
            received = metamodel;
            return new TransformationResult<>("<ecore/>".getBytes(StandardCharsets.UTF_8), List.of());
        }

        @Override
        public TransformationResult<MetaModel> fromPath(Path path) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TransformationResult<MetaModel> fromEPackage(EPackage ePackage, String artifactName) {
            throw new UnsupportedOperationException();
        }
    }
}
