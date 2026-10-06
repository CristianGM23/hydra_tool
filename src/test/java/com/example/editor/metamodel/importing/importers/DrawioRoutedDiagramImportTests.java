package com.example.editor.metamodel.importing.importers;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.importing.MetamodelImportOptions;
import com.example.editor.metamodel.importing.MetamodelImportSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba que draw.io recupera los puertos dinámicos y los waypoints.
 */
class DrawioRoutedDiagramImportTests {

    @Test
    void importsDynamicPortsAndOrthogonalWaypoints() {
        String source = """
                <?xml version="1.0" encoding="UTF-8"?>
                <mxfile><diagram id="sample"><mxGraphModel><root><mxCell id="0"/><mxCell id="1" parent="0"/>
                <mxCell id="a" parent="1" style="swimlane;fillColor=#fff;strokeColor=#000;" value="A" vertex="1"><mxGeometry x="20" y="40" width="180" height="120" as="geometry"/></mxCell>
                <mxCell id="b" parent="1" style="swimlane;fillColor=#fff;strokeColor=#000;" value="B" vertex="1"><mxGeometry x="500" y="240" width="180" height="120" as="geometry"/></mxCell>
                <mxCell id="r" edge="1" parent="1" source="a" target="b" style="edgeStyle=orthogonalEdgeStyle;exitX=1;exitY=0.143;entryX=0;entryY=0.75;endArrow=open;" value="link"><mxGeometry relative="1" as="geometry"><Array as="points"><mxPoint x="300" y="57.2"/><mxPoint x="300" y="330"/></Array></mxGeometry></mxCell>
                </root></mxGraphModel></diagram></mxfile>
                """;
        var result = new DrawioMetamodelImporter().importMetamodel(new MetamodelImportSource("sample.drawio", "application/xml", source.getBytes(StandardCharsets.UTF_8)), MetamodelImportOptions.defaults());
        var edge = result.diagram().edges().get(0);

        assertThat(edge.sourceHandle()).isEqualTo("right-14.3");
        assertThat(edge.targetHandle()).isEqualTo("left-75");
        assertThat(edge.routePoints()).hasSize(4);
        assertThat(edge.routePoints().get(1).x()).isEqualTo(300);
        assertThat(edge.routePoints().get(2).y()).isEqualTo(330);
    }
}
