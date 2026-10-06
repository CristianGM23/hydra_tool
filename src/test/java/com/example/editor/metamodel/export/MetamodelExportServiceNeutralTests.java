package com.example.editor.metamodel.export;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.emf.ecore.EPackage;
import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.adapter.ecore.EcoreMetamodelAdapter;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.TransformationResult;

import static org.assertj.core.api.Assertions.assertThat;

/** Comprueba que el servicio trabaja con un único contexto canónico. */
class MetamodelExportServiceNeutralTests {

    @Test
    void passesTheSameCanonicalContextToTheRegisteredExporter() {
        NeutralMetamodelExportContext context = CanonicalExportTestFixtures.context();
        CapturingTextExporter exporter = new CapturingTextExporter();
        MetamodelExportService service = new MetamodelExportService(id -> context, new StubEcoreAdapter(), List.of(exporter), List.of());

        MetamodelExportArtifact artifact = service.export("company", "xml");

        assertThat(exporter.received).isSameAs(context);
        assertThat(new String(artifact.content(), StandardCharsets.UTF_8)).isEqualTo("company");
    }

    @Test
    void generatesEcoreFromMetaModelAndTargetConfiguration() {
        NeutralMetamodelExportContext context = CanonicalExportTestFixtures.context();
        StubEcoreAdapter adapter = new StubEcoreAdapter();
        MetamodelExportService service = new MetamodelExportService(id -> context, adapter, List.of(), List.of());

        MetamodelExportArtifact artifact = service.export("company", "ecore");

        assertThat(adapter.received).isSameAs(context.neutralMetamodel());
        assertThat(new String(artifact.content(), StandardCharsets.UTF_8)).isEqualTo("<ecore/>");
    }

    private static class CapturingTextExporter implements MetamodelTextExporter {
        private NeutralMetamodelExportContext received;

        @Override
        public MetamodelExportFormat format() {
            return MetamodelExportFormat.XML;
        }

        @Override
        public String export(NeutralMetamodelExportContext context) {
            received = context;
            return context.neutralMetamodel().id();
        }
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
