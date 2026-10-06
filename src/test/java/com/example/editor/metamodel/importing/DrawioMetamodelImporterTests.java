package com.example.editor.metamodel.importing;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.importing.importers.DrawioMetamodelImporter;
import com.example.editor.metamodel.importing.canonicalization.ImportedMetamodelCanonicalizer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba la reconstrucción del formato draw.io generado.
 */
class DrawioMetamodelImporterTests {

    private final DrawioMetamodelImporter importer = new DrawioMetamodelImporter();

    @Test
    void importsClassAttributesAndContainment() {
        var output = importer.importMetamodel(source(drawio()), MetamodelImportOptions.defaults());
        assertThat(output.metamodel().classes()).extracting(item -> item.name()).containsExactlyInAnyOrder("Person", "Company");
        assertThat(output.metamodel().classes().stream().filter(item -> item.name().equals("Person")).findFirst().orElseThrow().attributes()).extracting(item -> item.name()).contains("name");
        assertThat(output.metamodel().relations()).singleElement().extracting(item -> item.kind()).isEqualTo(MetaRelationKind.CONTAINMENT);
    }

    @Test
    void importsLayoutStylesAndHandles() {
        var output = importer.importMetamodel(source(drawio()), MetamodelImportOptions.defaults());
        assertThat(output.diagram().nodes()).hasSize(2);
        assertThat(output.diagram().nodes().get(0).style()).isNotNull();
        assertThat(output.diagram().edges()).singleElement().satisfies(edge -> {
            assertThat(edge.sourceHandle()).isEqualTo("right-25");
            assertThat(edge.targetHandle()).isEqualTo("left-75");
        });
    }

    @Test
    void importsEnumerationAndDatatype() {
        String xml = drawio().replace("</root>", """
                <mxCell id="enum-state" parent="1" style="swimlane;startSize=44;fillColor=#ede9fe;swimlaneFillColor=#ffffff;strokeColor=#7c3aed;" value="«enumeration»&#xa;State" vertex="1"><mxGeometry x="80" y="300" width="220" height="100" as="geometry"/></mxCell>
                <mxCell id="enum-state-literal-active" parent="enum-state" style="text;" value="ACTIVE = 1" vertex="1"><mxGeometry y="44" width="220" height="26" as="geometry"/></mxCell>
                <mxCell id="datatype-money" parent="1" style="swimlane;startSize=44;fillColor=#cffafe;swimlaneFillColor=#ffffff;strokeColor=#0891b2;" value="«datatype»&#xa;Money" vertex="1"><mxGeometry x="380" y="300" width="220" height="100" as="geometry"/></mxCell>
                <mxCell id="datatype-money-implementation" parent="datatype-money" style="text;" value="+ implementationTypeName : com.example.Money" vertex="1"><mxGeometry y="44" width="220" height="26" as="geometry"/></mxCell>
                <mxCell id="datatype-money-serializable" parent="datatype-money" style="text;" value="+ serializable : Boolean = true" vertex="1"><mxGeometry y="70" width="220" height="26" as="geometry"/></mxCell>
                </root>""");
        var output = importer.importMetamodel(source(xml), MetamodelImportOptions.defaults());
        assertThat(output.metamodel().enums()).singleElement().satisfies(item -> assertThat(item.literals()).singleElement().extracting(literal -> literal.name()).isEqualTo("ACTIVE"));
        assertThat(output.metamodel().dataTypes()).singleElement().satisfies(item -> assertThat(ImportedMetamodelCanonicalizer.prepare(output.metamodel()).targetConfiguration().ecore().dataType(item.id()).instanceClassName()).isEqualTo("com.example.Money"));
    }

    private MetamodelImportSource source(String xml) {
        return new MetamodelImportSource("company.drawio", "application/xml", xml.getBytes(StandardCharsets.UTF_8));
    }

    private String drawio() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <mxfile><diagram id="company" name="Metamodel"><mxGraphModel><root>
                <mxCell id="0"/><mxCell id="1" parent="0"/>
                <mxCell id="class-company" parent="1" style="swimlane;startSize=44;fillColor=#e2e8f0;swimlaneFillColor=#ffffff;strokeColor=#475569;fontColor=#0f172a;fontFamily=Arial;fontSize=12;strokeWidth=2;" value="Company" vertex="1"><mxGeometry x="80" y="80" width="240" height="140" as="geometry"/></mxCell>
                <mxCell id="class-person" parent="1" style="swimlane;startSize=44;fillColor=#e2e8f0;swimlaneFillColor=#ffffff;strokeColor=#475569;fontColor=#0f172a;fontFamily=Arial;fontSize=12;strokeWidth=2;" value="Person" vertex="1"><mxGeometry x="420" y="80" width="240" height="140" as="geometry"/></mxCell>
                <mxCell id="class-person-attribute-attribute-name" parent="class-person" style="text;" value="+ name : EString [1]" vertex="1"><mxGeometry y="44" width="240" height="26" as="geometry"/></mxCell>
                <mxCell id="class-company-relation-reference-employees" parent="class-company" style="text;" value="+ employees : Person [0..*]" vertex="1"><mxGeometry y="70" width="240" height="26" as="geometry"/></mxCell>
                <mxCell id="reference-employees" edge="1" parent="1" source="class-company" target="class-person" style="edgeStyle=orthogonalEdgeStyle;startArrow=diamond;startFill=1;endArrow=open;exitX=1;exitY=0.25;entryX=0;entryY=0.75;strokeColor=#334155;strokeWidth=2;" value="employees"><mxGeometry relative="1" as="geometry"/></mxCell>
                </root></mxGraphModel></diagram></mxfile>
                """;
    }
}
