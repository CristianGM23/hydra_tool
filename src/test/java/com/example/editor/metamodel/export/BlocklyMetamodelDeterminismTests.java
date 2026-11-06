package com.example.editor.metamodel.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.importing.MetamodelImportOptions;
import com.example.editor.metamodel.importing.importers.BlocklyJsonMetamodelImporter;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** OBS-DET-BLOCKLY-ANNOTATIONS-ORDER: the Blockly export (and the metamodel recovered from it) of the same metamodel must be byte-identical
 *  whatever the JVM instance. Map.of iterates in an order seeded per JVM, so these tests pin the declared order and compare independent JVMs. */
class BlocklyMetamodelDeterminismTests {
    private static final int JVMS = 12;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void envelopeAnnotationsAreWrittenInTheirDeclaredOrder() {
        JsonNode envelope = mapper.readTree(BlocklyExportFingerprint.outputs(mapper).json());
        assertThat(names(envelope.get("annotations"))).containsExactly("hydra.blockly.integration", "hydra.blockly.execution");
        assertThat(envelope.get("annotations").get("hydra.blockly.integration").asString()).isEqualTo("independent-layer");
        assertThat(envelope.get("annotations").get("hydra.blockly.execution").asString()).isEqualTo("declarative");
        assertThat(names(envelope)).containsExactly("schemaVersion", "kind", "layer", "metamodelId", "payload", "blockDefinitions", "annotations");
    }

    @Test void recoveredMetamodelAnnotationsAreKeptInTheirDeclaredOrder() {
        BlocklyExportFingerprint.Outputs out = BlocklyExportFingerprint.outputs(mapper);
        for(MetaModel recovered : List.of(out.fromJson(), out.fromJavaScript())) {
            assertThat(recovered.annotations().keySet()).containsExactly("hydra:importFormat", "hydra:blockly:blockCount", "hydra:blockly:fieldCount",
                    "hydra:blockly:inputCount", "hydra:blockly:connectionConstraintCount", "hydra:blockly:javascriptExecuted");
            MetaClass item = recovered.classes().stream().filter(c -> c.name().equalsIgnoreCase("item")).findFirst().orElseThrow();
            assertThat(item.attributes()).isNotEmpty().allSatisfy(a -> assertThat(a.annotations().keySet()).containsExactly("blockly:fieldType", "blockly:source"));
            List<MetaRelation> inputs = recovered.relations().stream().filter(r -> r.kind() != MetaRelationKind.INHERITANCE).toList();
            assertThat(inputs).hasSize(2).allSatisfy(r -> assertThat(r.annotations().keySet()).containsExactly("blockly:inputType", "blockly:check", "blockly:source"));
        }
        assertThat(out.fromJson().annotations().get("hydra:importFormat")).isEqualTo("blockly-json");
        assertThat(out.fromJavaScript().annotations().get("hydra:importFormat")).isEqualTo("blockly-javascript");
        assertThat(out.fromJson().annotations()).containsEntry("hydra:blockly:blockCount", "3").containsEntry("hydra:blockly:fieldCount", "3")
                .containsEntry("hydra:blockly:inputCount", "2").containsEntry("hydra:blockly:javascriptExecuted", "false");
    }

    @Test void repeatedExportsInTheSameJvmAreIdentical() throws Exception {
        List<String> first = BlocklyExportFingerprint.fingerprints();
        for(int i = 0; i < 5; i++) assertThat(BlocklyExportFingerprint.fingerprints()).isEqualTo(first);
    }

    /** Blockly is a STRUCTURAL format: the round trip keeps the blocks, their field and input kinds and names and the connection checks, and
     *  converges from the second generation on. Field default texts are not part of that contract (OBS-INT-BLOCKLY-FIELD-INPUT-DEFAULT). */
    @Test void roundTripKeepsTheBlockStructureAndConvergesFromTheSecondGeneration() {
        BlocklyExportFingerprint.Outputs out = BlocklyExportFingerprint.outputs(mapper);
        List<MetaClass> blocks = out.fromJson().classes().stream().filter(c -> !"true".equals(c.annotations().get("blockly:base"))).toList();
        assertThat(blocks).extracting(MetaClass::name).containsExactly("Item", "Shipment", "Warehouse");
        assertThat(blocks.get(0).attributes()).extracting(MetaAttribute::name).containsExactly("CODE", "QUANTITY", "FRAGILE");
        String second = new BlocklyJsonMetamodelExporter(mapper).export(BlocklyExportFingerprint.context(out.fromJson()));
        assertThat(structure(mapper.readTree(second))).isEqualTo(structure(mapper.readTree(out.json())));
        MetaModel secondModel = new BlocklyJsonMetamodelImporter(mapper).importMetamodel(BlocklyExportFingerprint.source("second.blockly.json", second), MetamodelImportOptions.defaults()).metamodel();
        String third = new BlocklyJsonMetamodelExporter(mapper).export(BlocklyExportFingerprint.context(secondModel));
        assertThat(mapper.readTree(third).get("blockDefinitions")).isEqualTo(mapper.readTree(second).get("blockDefinitions"));
    }

    private static List<String> structure(JsonNode envelope) {
        List<String> result = new ArrayList<>();
        for(JsonNode block : envelope.get("blockDefinitions")) {
            StringBuilder b = new StringBuilder(block.get("type").asString());
            if(block.has("args0")) for(JsonNode arg : block.get("args0")) b.append(' ').append(arg.get("type").asString()).append(':').append(arg.get("name").asString()).append(arg.has("check") ? "->" + checks(arg.get("check")) : "");
            result.add(b.toString());
        }
        return result;
    }

    @Test void independentJvmsProduceIdenticalExportsAndRecoveredMetamodels() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("java.class.path");
        Set<String> distinct = new LinkedHashSet<>();
        List<String> all = new ArrayList<>();
        for(int i = 0; i < JVMS; i++) {
            Process process = new ProcessBuilder(java, "-cp", classpath, BlocklyExportFingerprint.class.getName()).redirectErrorStream(true).start();
            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            if(!finished) { process.destroyForcibly(); }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            assertThat(finished).as("JVM %d finished", i).isTrue();
            assertThat(process.exitValue()).as(output).isZero();
            all.add(output);
            distinct.add(output);
        }
        assertThat(distinct).as("fingerprints of %d independent JVMs (classpath entries: %d): %s", JVMS, classpath.split(File.pathSeparator).length, all).hasSize(1);
        assertThat(distinct.iterator().next()).isEqualTo(String.join(" ", BlocklyExportFingerprint.fingerprints()));
    }

    /** In Blockly a connection check is a type name or an array of type names; a single name and a one-element array are the same check. */
    private static List<String> checks(JsonNode check) {
        List<String> result = new ArrayList<>();
        if(check.isArray()) check.forEach(item -> result.add(item.asString())); else result.add(check.asString());
        return result;
    }

    private static List<String> names(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.propertyNames().forEach(names::add);
        return names;
    }
}
