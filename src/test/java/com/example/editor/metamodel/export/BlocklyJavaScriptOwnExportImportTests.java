package com.example.editor.metamodel.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.importing.MetamodelImportOptions;
import com.example.editor.metamodel.importing.importers.BlocklyJavaScriptMetamodelImporter;
import com.example.editor.metamodel.importing.importers.BlocklyJsonMetamodelImporter;

import tools.jackson.databind.ObjectMapper;

/** FIND-INT-BLOCKLY-JS-OWN-EXPORT: blockly-javascript is a declared round-trip format, so the file HYDRA itself exports (its block definitions
 *  live in the HYDRA_BLOCKLY_LAYER literal, registered through a variable) must import back like the equivalent blockly-json. Nothing else in
 *  a script is interpreted: the JavaScript is still never executed. */
class BlocklyJavaScriptOwnExportImportTests {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void theBlocklyJavaScriptHydraExportsImportsBackLikeItsJson() {
        var context = BlocklyExportFingerprint.context(BlocklyExportFingerprint.model());
        String javascript = new BlocklyJavaScriptMetamodelExporter(mapper).export(context);
        String json = new BlocklyJsonMetamodelExporter(mapper).export(context);

        MetaModel fromJavaScript = new BlocklyJavaScriptMetamodelImporter(mapper).importMetamodel(BlocklyExportFingerprint.source("model.blockly.js", javascript), MetamodelImportOptions.defaults()).metamodel();
        MetaModel fromJson = new BlocklyJsonMetamodelImporter(mapper).importMetamodel(BlocklyExportFingerprint.source("model.blockly.json", json), MetamodelImportOptions.defaults()).metamodel();

        assertThat(fromJavaScript.classes()).extracting(MetaClass::name).containsExactlyElementsOf(fromJson.classes().stream().map(MetaClass::name).toList());
        assertThat(fromJavaScript.classes()).extracting(MetaClass::annotations).containsExactlyElementsOf(fromJson.classes().stream().map(MetaClass::annotations).toList());
        assertThat(fromJavaScript.relations()).hasSameSizeAs(fromJson.relations());
        assertThat(fromJavaScript.annotations()).containsEntry("hydra:importFormat", "blockly-javascript").containsEntry("hydra:blockly:javascriptExecuted", "false");
    }

    @Test void aLayerLiteralThatIsNotTheHydraBlocklyLayerIsNotInterpreted() {
        String script = "const layer = {\"kind\":\"SOMETHING_ELSE\",\"blockDefinitions\":[{\"type\":\"item\",\"message0\":\"Item\",\"previousStatement\":null}]};\nregister(layer.blockDefinitions);\n";
        assertThatThrownBy(() -> new BlocklyJavaScriptMetamodelImporter(mapper).importMetamodel(BlocklyExportFingerprint.source("other.blockly.js", script), MetamodelImportOptions.defaults()))
                .isInstanceOf(MetamodelDesignException.class).hasMessageContaining("No static Blockly block definitions");
    }
}
