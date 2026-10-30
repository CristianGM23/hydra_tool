package com.example.editor.metamodel.export;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;
import com.example.editor.metamodel.importing.MetamodelImportOptions;
import com.example.editor.metamodel.importing.MetamodelImportSource;
import com.example.editor.metamodel.importing.importers.BlocklyJavaScriptMetamodelImporter;
import com.example.editor.metamodel.importing.importers.BlocklyJsonMetamodelImporter;
import com.example.editor.metamodel.target.EcoreProjectionConfiguration;
import com.example.editor.metamodel.target.TargetConfigurationDocument;

import tools.jackson.databind.ObjectMapper;

/** Exports one metamodel with several annotations to blockly-json and blockly-javascript, imports both back and prints the SHA-256 of every
 *  result. Run in independent JVMs by {@link BlocklyMetamodelDeterminismTests}: the output must not depend on the JVM instance
 *  (OBS-DET-BLOCKLY-ANNOTATIONS-ORDER). */
public final class BlocklyExportFingerprint {
    private BlocklyExportFingerprint() { }

    public static void main(String[] args) throws Exception {
        System.out.println(String.join(" ", fingerprints()));
    }

    static List<String> fingerprints() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Outputs out = outputs(mapper);
        return List.of(sha256(out.json()), sha256(out.javascript()), sha256(mapper.writeValueAsString(out.fromJson())), sha256(mapper.writeValueAsString(out.fromJavaScript())));
    }

    record Outputs(String json, String javascript, MetaModel fromJson, MetaModel fromJavaScript) { }

    static Outputs outputs(ObjectMapper mapper) {
        NeutralMetamodelExportContext context = context(model());
        String json = new BlocklyJsonMetamodelExporter(mapper).export(context);
        String javascript = new BlocklyJavaScriptMetamodelExporter(mapper).export(context);
        MetaModel fromJson = new BlocklyJsonMetamodelImporter(mapper).importMetamodel(source("model.blockly.json", json), MetamodelImportOptions.defaults()).metamodel();
        MetaModel fromJavaScript = new BlocklyJavaScriptMetamodelImporter(mapper).importMetamodel(source("model.blockly.js", javascript), MetamodelImportOptions.defaults()).metamodel();
        return new Outputs(json, javascript, fromJson, fromJavaScript);
    }

    static MetaModel model() {
        MetaAttribute code = new MetaAttribute("attribute-code", "code", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.optional(), true, true, "SKU",
                Map.of("doc", "stock keeping unit"));
        MetaAttribute quantity = new MetaAttribute("attribute-quantity", "quantity", MetaTypeReference.primitive(MetaPrimitiveType.INTEGER), Multiplicity.optional(), true, true, "1", Map.of());
        MetaAttribute fragile = new MetaAttribute("attribute-fragile", "fragile", MetaTypeReference.primitive(MetaPrimitiveType.BOOLEAN), Multiplicity.optional(), true, true, "false", Map.of());
        MetaClass item = new MetaClass("class-item", "Item", MetaClassKind.CLASS, List.of(code, quantity, fragile), List.of(), Map.of());
        MetaClass shipment = new MetaClass("class-shipment", "Shipment", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaClass warehouse = new MetaClass("class-warehouse", "Warehouse", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaRelation contains = new MetaRelation("relation-items", "items", "class-shipment", "class-item", MetaRelationKind.CONTAINMENT, null, Multiplicity.many(), "", true, true, Map.of());
        MetaRelation origin = new MetaRelation("relation-origin", "origin", "class-shipment", "class-warehouse", MetaRelationKind.REFERENCE, null, Multiplicity.optional(), "", true, true, Map.of());
        return new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, "logistics", "Logistics", List.of(item, shipment, warehouse), List.of(), List.of(), List.of(contains, origin), Map.of());
    }

    static NeutralMetamodelExportContext context(MetaModel model) {
        TargetConfigurationDocument targets = new TargetConfigurationDocument(TargetConfigurationDocument.CURRENT_SCHEMA_VERSION, model.id(),
                EcoreProjectionConfiguration.defaultsFor(model), Map.of());
        return new DefaultNeutralMetamodelExportContext(model, new MetamodelDiagramDocument(model.id(), List.of(), List.of()), targets, List.of(), MetamodelExportOptions.defaults());
    }

    static MetamodelImportSource source(String name, String content) {
        return new MetamodelImportSource(name, "text/plain", content.getBytes(StandardCharsets.UTF_8));
    }

    static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
