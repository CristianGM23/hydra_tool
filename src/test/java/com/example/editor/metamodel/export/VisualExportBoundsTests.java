package com.example.editor.metamodel.export;

import java.io.ByteArrayInputStream;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.design.MetamodelDiagramDocument;

import static org.assertj.core.api.Assertions.assertThat;

/** Comprueba que SVG y PNG contienen el rectángulo completo del metamodelo. */
class VisualExportBoundsTests {

    @Test
    void svgUsesMinimumAndMaximumCoordinatesInsteadOfStartingAtZero() {
        NeutralMetamodelExportContext context = contextWithNodesOutsideTheInitialViewport();

        String svg = new SvgMetamodelExporter().export(context);

        assertThat(svg).contains("viewBox=\"-340.0 -260.0 1680.0 1230.0\"");
        assertThat(svg).contains("x=\"-300.0\" y=\"-220.0\"");
        assertThat(svg).contains("x=\"1100.0\" y=\"820.0\"");
    }

    @Test
    void pngTranslatesNegativeCoordinatesAndUsesTheCompleteRectangle() throws Exception {
        NeutralMetamodelExportContext context = contextWithNodesOutsideTheInitialViewport();

        var image = ImageIO.read(new ByteArrayInputStream(new PngMetamodelExporter().export(context)));

        assertThat(image.getWidth()).isEqualTo(3360);
        assertThat(image.getHeight()).isEqualTo(2460);
        assertThat(image.getRGB(100, 100)).isNotEqualTo(0xffffffff);
        assertThat(image.getRGB(2900, 2180)).isNotEqualTo(0xffffffff);
    }

    private NeutralMetamodelExportContext contextWithNodesOutsideTheInitialViewport() {
        NeutralMetamodelExportContext source = CanonicalExportTestFixtures.context();
        List<MetamodelDiagramDocument.NodeDesign> nodes = source.diagram().nodes().stream().map(node -> {
            if(node.elementId().equals("interface-identifiable")) {
                return new MetamodelDiagramDocument.NodeDesign(node.elementId(), -300, -220, node.width(), node.height(), node.style());
            }

            if(node.elementId().equals("class-audit")) {
                return new MetamodelDiagramDocument.NodeDesign(node.elementId(), 1100, 820, node.width(), node.height(), node.style());
            }

            return node;
        }).toList();
        MetamodelDiagramDocument diagram = new MetamodelDiagramDocument(source.diagram().metamodelId(), nodes, List.of());
        return new DefaultNeutralMetamodelExportContext(source.neutralMetamodel(), diagram, source.targets(), source.warnings(), source.options());
    }
}
