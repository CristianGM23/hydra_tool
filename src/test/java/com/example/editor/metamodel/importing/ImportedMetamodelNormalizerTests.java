package com.example.editor.metamodel.importing;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.MetaTypeReferenceKind;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.importing.canonicalization.ImportedMetamodelCanonicalizer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba identidad y tipos externos editables.
 */
class ImportedMetamodelNormalizerTests {

    @Test
    void convertsExternalTypesIntoCustomDataTypes() {
        MetaAttribute amount = new MetaAttribute("attribute-amount", "amount", MetaTypeReference.external("com.example.Money"), Multiplicity.one(), true, true, "", Map.of());
        MetaClass invoice = new MetaClass("class-invoice", "Invoice", MetaClassKind.CLASS, List.of(amount), List.of(), Map.of());
        MetaModel model = new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, "invoice", "Invoice", List.of(invoice), List.of(), List.of(), List.of(), Map.of());
        var result = new ImportedMetamodelNormalizer().normalize(new MetamodelImportOutput(model, null, List.of()), new MetamodelImportSource("invoice.json", "application/json", new byte[] { 1 }), MetamodelImportFormat.JSON, MetamodelImportOptions.defaults());
        assertThat(result.metamodel().dataTypes()).singleElement().satisfies(item -> assertThat(item.name()).isEqualTo("Money"));
        var prepared = ImportedMetamodelCanonicalizer.prepare(result.metamodel());
        String dataTypeId = result.metamodel().dataTypes().get(0).id();
        assertThat(prepared.targetConfiguration().ecore().dataType(dataTypeId).instanceClassName()).isEqualTo("com.example.Money");
        assertThat(result.metamodel().classes().get(0).attributes().get(0).type().kind()).isEqualTo(MetaTypeReferenceKind.DATATYPE);
        assertThat(result.warnings()).extracting(item -> item.code()).contains("IMPORT_EXTERNAL_TYPE_TO_DATATYPE");
    }

    @Test
    void appliesRequestedTargetIdentity() {
        MetaModel model = new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, "old", "Old", List.of(new MetaClass("class-root", "Root", MetaClassKind.CLASS, List.of(), List.of(), Map.of())), List.of(), List.of(), List.of(), Map.of());
        var options = new MetamodelImportOptions("json", "New Domain", false, true, true, false);
        var result = new ImportedMetamodelNormalizer().normalize(new MetamodelImportOutput(model, null, List.of()), new MetamodelImportSource("old.json", "application/json", new byte[] { 1 }), MetamodelImportFormat.JSON, options);
        assertThat(result.metamodel().id()).isEqualTo("new-domain");
        assertThat(ImportedMetamodelCanonicalizer.prepare(result.metamodel()).targetConfiguration().ecore().fileName()).isEqualTo("new-domain.ecore");
    }
}
