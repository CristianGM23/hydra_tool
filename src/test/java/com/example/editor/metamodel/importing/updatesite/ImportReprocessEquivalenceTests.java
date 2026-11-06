package com.example.editor.metamodel.importing.updatesite;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.core.TransformationWarning;
import com.example.editor.metamodel.importing.AutomaticMetamodelDiagramLayoutService;
import com.example.editor.metamodel.importing.MetamodelImportRoutingPolicy;
import com.example.editor.metamodel.importing.ImportedDiagramRelationNormalizer;
import com.example.editor.metamodel.importing.ImportedMetamodelNormalizer;
import com.example.editor.metamodel.importing.MetamodelImportDetector;
import com.example.editor.metamodel.importing.MetamodelImportOptions;
import com.example.editor.metamodel.importing.MetamodelImportPreview;
import com.example.editor.metamodel.importing.MetamodelImportProperties;
import com.example.editor.metamodel.importing.MetamodelImportService;
import com.example.editor.metamodel.importing.importers.NeutralJsonMetamodelImporter;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;

import tools.jackson.databind.json.JsonMapper;

/**
 * T-UPD (OBS-MM-IMPORT-PREVIEW-DUPLICATE-LAYOUT): the update-site confirmation used to re-import the JSON of the neutral preview its analysis
 * had already computed, repeating parsing, normalisation and the whole diagram layout. Persisting the prepared preview instead is only safe if
 * it stores exactly what the re-import stored. This checks that equivalence on the reference battery metamodels (15 to 105 classes) and on a
 * generated 250-class model, with and without a target id that renames the recovered metamodel: the metamodel, the diagram, the target
 * configuration and the warnings of {@code asSaved(first)} are those of the second pass.
 */
class ImportReprocessEquivalenceTests {
    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final MetamodelImportProperties properties = new MetamodelImportProperties();
    private final MetamodelImportService service = new MetamodelImportService(properties, new MetamodelImportDetector(), new ImportedMetamodelNormalizer(),
            new AutomaticMetamodelDiagramLayoutService(null, properties, new MetamodelImportRoutingPolicy(properties)), new ImportedDiagramRelationNormalizer(),
            new NeutralMetamodelValidator(), null, null, null, List.of(new NeutralJsonMetamodelImporter(mapper)));

    @ParameterizedTest
    @CsvSource({"logistore-estres,", "logistore-estres,renamed-target", "urbansense-alto,", "urbansense-alto,renamed-target", "universityflow-medio,", "universityflow-medio,renamed-target"})
    void persistingThePreparedPreviewStoresWhatTheReimportStored(String corpus, String targetId) throws Exception {
        byte[] source;
        try(InputStream in = getClass().getResourceAsStream("/metamodel-corpus/" + corpus + ".metamodel.json")) { source = in.readAllBytes(); }
        assertEquivalent(mapper.readValue(source, MetaModel.class), targetId == null ? "" : targetId);
    }

    @Test
    void aLargeGeneratedModelIsEquivalentToo() throws Exception {
        assertEquivalent(generated(250), "");
        assertEquivalent(generated(250), "renamed-large");
    }

    private void assertEquivalent(MetaModel recovered, String targetId) throws Exception {
        // what UpdateSiteImportApplicationService.previewNeutral computes during the analysis
        MetamodelImportPreview first = service.preview(recovered.id() + ".metamodel.json", "application/json", mapper.writeValueAsBytes(recovered),
                new MetamodelImportOptions("json", targetId.isBlank() ? recovered.id() : targetId, false, true, true, false));
        // what importPrepared stored before T-UPD: the preview's metamodel re-imported under its final id
        MetamodelImportPreview second = service.preview(first.metamodel().id() + ".metamodel.json", "application/json", mapper.writeValueAsBytes(first.metamodel()),
                new MetamodelImportOptions("json", first.metamodel().id(), true, true, true, false));
        MetamodelImportPreview reused = UpdateSiteImportApplicationService.asSaved(first);

        assertThat(mapper.writeValueAsString(reused.metamodel())).as("metamodel").isEqualTo(mapper.writeValueAsString(second.metamodel()));
        assertThat(mapper.writeValueAsString(reused.diagram())).as("diagram").isEqualTo(mapper.writeValueAsString(second.diagram()));
        assertThat(mapper.writeValueAsString(reused.targets())).as("targets").isEqualTo(mapper.writeValueAsString(second.targets()));
        assertThat(reused.warnings()).extracting(TransformationWarning::code).as("warnings").isEqualTo(second.warnings().stream().map(TransformationWarning::code).toList());
    }

    /** A deterministic model with inheritance, containment, references and attributes, as large as the MM-SCALE models of the battery. */
    private static MetaModel generated(int size) {
        List<MetaClass> classes = new ArrayList<>();
        List<MetaRelation> relations = new ArrayList<>();
        for(int i = 0; i < size; i++) {
            String id = "class-" + i;
            List<MetaAttribute> attributes = List.of(new MetaAttribute(id + "-name", "name", MetaTypeReference.primitive(MetaPrimitiveType.STRING), new Multiplicity(0, 1), true, true, "", Map.of()),
                    new MetaAttribute(id + "-size", "size", MetaTypeReference.primitive(MetaPrimitiveType.INTEGER), new Multiplicity(0, 1), true, true, "", Map.of()));
            classes.add(new MetaClass(id, "Type" + i, i % 25 == 0 ? MetaClassKind.ABSTRACT_CLASS : MetaClassKind.CLASS, attributes, List.of(), Map.of()));
            if(i > 0 && i % 25 != 0) relations.add(new MetaRelation("supertype-" + i, "", id, "class-" + (i / 25) * 25, MetaRelationKind.INHERITANCE, null, null, "", true, true, Map.of()));
            if(i > 0) relations.add(new MetaRelation("contains-" + i, "items" + i, "class-" + (i - 1), id, MetaRelationKind.CONTAINMENT, new Multiplicity(0, 1), new Multiplicity(0, null), "", true, true, Map.of()));
            if(i > 2) relations.add(new MetaRelation("refers-" + i, "ref" + i, id, "class-" + (i * 7 % i), MetaRelationKind.REFERENCE, new Multiplicity(0, null), new Multiplicity(0, 1), "", true, true, Map.of()));
        }
        return new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, "generated-" + size, "Generated" + size, classes, List.of(), List.of(), relations, Map.of());
    }
}
