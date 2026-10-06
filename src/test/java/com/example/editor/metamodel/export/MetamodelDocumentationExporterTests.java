package com.example.editor.metamodel.export;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Comprueba los exportadores de documentación y diagramas. */
class MetamodelDocumentationExporterTests {

    @Test
    void exportsDocumentationAndDiagramFormats() {
        NeutralMetamodelExportContext context = CanonicalExportTestFixtures.context();

        String plantUml = new PlantUmlMetamodelExporter().export(context);
        String markdown = new MarkdownMetamodelExporter().export(context);
        String drawio = new DrawioMetamodelExporter().export(context);
        String svg = new SvgMetamodelExporter().export(context);

        assertThat(plantUml).contains("@startuml", "Person", "*--", "employer");
        assertThat(markdown).contains("# Metamodel: Company", "## Classes", "Person");
        assertThat(drawio).contains("<mxGraphModel", "class-person", "reference-employer");
        assertThat(svg).contains("<svg", "<path", "Person");
    }
}
