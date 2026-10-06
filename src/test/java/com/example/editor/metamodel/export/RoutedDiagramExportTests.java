package com.example.editor.metamodel.export;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Comprueba que las exportaciones visuales conservan rutas ortogonales. */
class RoutedDiagramExportTests {

    @Test
    void drawioExportsWaypointsAndDynamicPorts() {
        String result = new DrawioMetamodelExporter().export(CanonicalExportTestFixtures.context());

        assertThat(result).contains("<Array as=\"points\">", "<mxPoint x=\"340\" y=\"250\"/>", "exitX=1;exitY=0.5", "entryX=0;entryY=0.5");
    }

    @Test
    void svgExportsTheStoredPolylineAndLabel() {
        String result = new SvgMetamodelExporter().export(CanonicalExportTestFixtures.context());

        assertThat(result).contains("L 340.0 250.0", "L 420.0 250.0", "employer");
    }

    @Test
    void pngExportsAValidImageFromTheStoredRoute() {
        byte[] result = new PngMetamodelExporter().export(CanonicalExportTestFixtures.context());

        assertThat(result).hasSizeGreaterThan(8);
        assertThat(result[0] & 0xff).isEqualTo(0x89);
        assertThat(result[1]).isEqualTo((byte) 0x50);
        assertThat(result[2]).isEqualTo((byte) 0x4e);
        assertThat(result[3]).isEqualTo((byte) 0x47);
    }
}
