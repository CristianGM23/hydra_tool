package com.example.editor.metamodel.export;

import java.io.StringReader;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Comprueba que draw.io utiliza la estructura de la plantilla UML. */
class DrawioMetamodelExporterTemplateTests {

    @Test
    void exportsAWellFormedUmlDrawioDiagram() throws Exception {
        String xml = new DrawioMetamodelExporter().export(CanonicalExportTestFixtures.context());
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(xml)));

        assertThat(document.getDocumentElement().getNodeName()).isEqualTo("mxfile");
        assertThat(xml).contains("swimlane;html=1", "childLayout=stackLayout", "portConstraint=eastwest");
        assertThat(xml).contains("parent=\"class-person\"", "+ name : EString [1]", "+ employer : Company [0..1]");
        assertThat(xml).contains("«interface»", "«enumeration»", "«datatype»");
        assertThat(xml).contains("startArrow=diamond;startFill=1", "startArrow=diamondThin;startFill=0");
        assertThat(xml).contains("dashed=1;endArrow=open", "endArrow=block;endFill=0");
        assertThat(xml).contains("exitX=1;exitY=0.25", "entryX=0;entryY=0.75");
    }
}
