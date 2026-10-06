package com.example.editor.metamodel.importing;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaOperation;
import com.example.editor.metamodel.core.MetaOperationBehaviorKind;
import com.example.editor.metamodel.core.MetaParameter;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;
import com.example.editor.metamodel.export.AntlrMetamodelExporter;
import com.example.editor.metamodel.export.DefaultNeutralMetamodelExportContext;
import com.example.editor.metamodel.export.DrawioMetamodelExporter;
import com.example.editor.metamodel.export.LangiumMetamodelExporter;
import com.example.editor.metamodel.export.MetamodelExportOptions;
import com.example.editor.metamodel.export.NeutralMetamodelExportContext;
import com.example.editor.metamodel.export.NeutralXmlMetamodelExporter;
import com.example.editor.metamodel.export.PlantUmlMetamodelExporter;
import com.example.editor.metamodel.export.XtextMetamodelExporter;
import com.example.editor.metamodel.target.TargetConfigurationDocument;
import com.example.editor.metamodel.importing.importers.AntlrMetamodelImporter;
import com.example.editor.metamodel.importing.importers.DrawioMetamodelImporter;
import com.example.editor.metamodel.importing.importers.LangiumMetamodelImporter;
import com.example.editor.metamodel.importing.importers.NeutralXmlMetamodelImporter;
import com.example.editor.metamodel.importing.importers.PlantUmlMetamodelImporter;
import com.example.editor.metamodel.importing.importers.XtextMetamodelImporter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba viajes completos entre los exportadores e importadores de la herramienta.
 */
class GeneratedFormatRoundTripTests {

    /**
     * Descripción:
     * Exporta e importa el XML neutral conservando la estructura semántica.
     */
    @Test
    void roundTripsNeutralXml() {
        NeutralMetamodelExportContext context = context();
        String exported = new NeutralXmlMetamodelExporter().export(context);
        MetamodelImportOutput imported = new NeutralXmlMetamodelImporter().importMetamodel(source("sample.xml", exported), MetamodelImportOptions.defaults());

        assertSemanticStructure(imported);
        assertOperationStructure(imported);
    }

    /**
     * Descripción:
     * Exporta e importa draw.io conservando también el layout y los puntos de conexión.
     */
    @Test
    void roundTripsDrawioWithLayout() {
        NeutralMetamodelExportContext context = context();
        String exported = new DrawioMetamodelExporter().export(context);
        MetamodelImportOutput imported = new DrawioMetamodelImporter().importMetamodel(source("sample.drawio", exported), MetamodelImportOptions.defaults());

        assertSemanticStructure(imported);
        assertOperationStructure(imported);
        assertThat(imported.diagram()).isNotNull();
        assertThat(imported.diagram().nodes()).hasSize(2);
        assertThat(imported.diagram().edges()).singleElement().satisfies(edge -> {
            assertThat(edge.sourceHandle()).isEqualTo("right-25");
            assertThat(edge.targetHandle()).isEqualTo("left-75");
        });
    }

    /**
     * Descripción:
     * Comprueba las gramáticas Langium y Xtext generadas por la herramienta.
     */
    @Test
    void roundTripsGeneratedTextualGrammars() {
        NeutralMetamodelExportContext context = context();
        MetamodelImportOutput langium = new LangiumMetamodelImporter().importMetamodel(source("sample.langium", new LangiumMetamodelExporter().export(context)), MetamodelImportOptions.defaults());
        MetamodelImportOutput xtext = new XtextMetamodelImporter().importMetamodel(source("sample.xtext", new XtextMetamodelExporter().export(context)), MetamodelImportOptions.defaults());

        assertGeneratedTextualSemanticStructure(langium);
        assertGeneratedTextualSemanticStructure(xtext);
    }

    /**
     * Descripción:
     * Comprueba los formatos documentales PlantUML y ANTLR generados.
     */
    @Test
    void roundTripsGeneratedPlantUmlAndAntlr() {
        NeutralMetamodelExportContext context = context();
        MetamodelImportOutput plantUml = new PlantUmlMetamodelImporter().importMetamodel(source("sample.puml", new PlantUmlMetamodelExporter().export(context)), MetamodelImportOptions.defaults());
        MetamodelImportOutput antlr = new AntlrMetamodelImporter().importMetamodel(source("sample.g4", new AntlrMetamodelExporter().export(context)), MetamodelImportOptions.defaults());

        assertSemanticStructure(plantUml);
        assertOperationStructure(plantUml);
        assertSemanticStructure(antlr);
    }


    private void assertOperationStructure(MetamodelImportOutput imported) {
        assertThat(imported.metamodel().classes().stream().filter(item -> item.name().equals("Person"))).singleElement().satisfies(person -> {
            assertThat(person.attributes()).extracting(MetaAttribute::name).contains("name");
            assertThat(person.operations()).singleElement().satisfies(operation -> {
                assertThat(operation.name()).isEqualTo("greet");
                assertThat(operation.behaviorKind()).isEqualTo(MetaOperationBehaviorKind.QUERY);
                assertThat(operation.returnType()).isEqualTo(MetaTypeReference.primitive(MetaPrimitiveType.STRING));
                assertThat(operation.parameters()).singleElement().satisfies(parameter -> {
                    assertThat(parameter.name()).isEqualTo("prefix");
                    assertThat(parameter.type()).isEqualTo(MetaTypeReference.primitive(MetaPrimitiveType.STRING));
                });
            });
        });
    }

    private void assertGeneratedTextualSemanticStructure(MetamodelImportOutput imported) {
        assertThat(imported.metamodel().classes()).extracting(MetaClass::name)
                .containsExactlyInAnyOrder("Person", "Company")
                .doesNotContain("Model", "Element");
        assertThat(imported.metamodel().relations().stream()
                .filter(relation -> relation.name().equals("employees")
                        && relation.kind() == MetaRelationKind.CONTAINMENT))
                .singleElement();
    }

    private void assertSemanticStructure(MetamodelImportOutput imported) {
        assertThat(imported.metamodel().classes()).extracting(MetaClass::name).containsExactlyInAnyOrder("Person", "Company");
        assertThat(imported.metamodel().relations()).singleElement().satisfies(relation -> {
            assertThat(relation.name()).isEqualTo("employees");
            assertThat(relation.kind()).isEqualTo(MetaRelationKind.CONTAINMENT);
        });
    }

    private MetamodelImportSource source(String fileName, String content) {
        return new MetamodelImportSource(fileName, "text/plain", content.getBytes(StandardCharsets.UTF_8));
    }

    private NeutralMetamodelExportContext context() {
        MetaAttribute name = new MetaAttribute("attribute-name", "name", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.one(), true, true, "", Map.of());
        MetaParameter prefix = new MetaParameter("parameter-prefix", "prefix", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.one(), true, true, Map.of());
        MetaOperation greet = new MetaOperation("operation-greet", "greet", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.one(), true, true, List.of(prefix), List.of(), MetaOperationBehaviorKind.QUERY, Map.of());
        MetaClass person = new MetaClass("class-person", "Person", MetaClassKind.CLASS, List.of(name), List.of(greet), Map.of());
        MetaClass company = new MetaClass("class-company", "Company", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaRelation employees = new MetaRelation("reference-employees", "employees", "class-company", "class-person", MetaRelationKind.CONTAINMENT, Multiplicity.optional(), Multiplicity.many(), "", true, true, Map.of());
        MetaModel model = new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, "sample", "sample", List.of(person, company), List.of(), List.of(), List.of(employees), Map.of());
        List<MetamodelDiagramDocument.NodeDesign> nodes = List.of(new MetamodelDiagramDocument.NodeDesign("class-person", 80, 80, 240, 140), new MetamodelDiagramDocument.NodeDesign("class-company", 420, 80, 240, 140));
        List<MetamodelDiagramDocument.EdgeDesign> edges = List.of(new MetamodelDiagramDocument.EdgeDesign("reference-employees", "right-25", "left-75", "CONTAINMENT", "class-company", "class-person", "employees"));
        MetamodelDiagramDocument diagram = new MetamodelDiagramDocument("sample", nodes, edges);
        TargetConfigurationDocument targets = TargetConfigurationDocument.defaultsFor(model);
        return new DefaultNeutralMetamodelExportContext(model, diagram, targets, List.of(), MetamodelExportOptions.defaults());
    }
}
