package com.example.editor.workbench.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.example.editor.event.EventBindingModel;
import com.example.editor.event.EventDefinitionModel;
import com.example.editor.event.persistence.EventBindingModelRepository;
import com.example.editor.event.persistence.EventDefinitionModelRepository;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.web.MetamodelHttpTestEnvironment;

/**
 * DEF-HOME-READINESS-MODULE-CORRUPT-500: the Home readiness of a metamodel answered 500 as soon as one stored document of validations, events,
 * event flows or editors was unreadable, of that metamodel or of any other one, because it listed every document of those modules.
 *
 * Full Spring context on real stores in a temporary directory; the damage is written directly into the files. The oracle is the readiness
 * returned (valid layers still read, one warning per unreadable layer, no server path or file name in it) and the stored files, compared byte
 * for byte and by modification time: reading the readiness changes nothing. With the classes of the jar 16 the first two tests fail with the
 * exception of the store; a corrupt metamodel still fails, as reading the metamodel itself does.
 */
@SpringBootTest
class HydraCapabilityReadinessCorruptLayersTests {
    private static final String CORRUPT = "{ unreadable document written by the test";
    private static final List<String> MODULES = List.of("metamodels", "validations", "events", "event-flows", "editors", "integrations");

    @TempDir
    static Path root;

    @DynamicPropertySource
    static void directories(DynamicPropertyRegistry registry) {
        MetamodelHttpTestEnvironment.register(registry, root);
    }

    @Autowired private HydraCapabilityReadinessService readiness;
    @Autowired private CanonicalMetamodelApplicationService metamodels;
    @Autowired private EventDefinitionModelRepository eventDefinitions;
    @Autowired private EventBindingModelRepository eventBindings;
    private final List<Path> damaged = new ArrayList<>();

    @Test
    void aCorruptDocumentOfAnotherMetamodelDoesNotHideTheValidLayersOfThisOne() throws Exception {
        metamodel("people");
        metamodel("other");
        events("people");
        corrupt("validations/other.validations.json");
        corrupt("events/other.event-definitions.json");
        corrupt("events/other.event-bindings.json");
        corrupt("event-flows/other.event-flows.json");
        Map<String, String> before = snapshot();

        HydraCapabilityReadiness result = readiness.summarize("people");

        assertThat(result.metamodelId()).isEqualTo("people");
        assertThat(layer(result, "EVENTS").artifactCount()).as("the valid events of people are still read").isEqualTo(1);
        assertThat(result.warnings()).as("the documents of another metamodel are not read at all").isEmpty();
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void aCorruptLayerOfThisMetamodelBecomesAWarningWithoutServerDetails() throws Exception {
        metamodel("orders");
        corrupt("validations/orders.validations.json");
        events("orders");
        corrupt("events/orders.event-bindings.json");
        corrupt("event-flows/orders.event-flows.json");
        corrupt("editors/orders-editor.editor.json");
        corrupt("integrations/orders-langium.integration.json");
        Map<String, String> before = snapshot();

        HydraCapabilityReadiness result = readiness.summarize("orders");

        assertThat(result.layers()).extracting(HydraCapabilityLayerSummary::id)
                .containsExactly("METAMODEL", "VALIDATIONS", "EVENTS", "EVENT_FLOWS", "GAMIFICATION", "EDITORS", "INTEGRATIONS");
        assertThat(layer(result, "METAMODEL").configured()).isTrue();
        assertThat(result.warnings()).containsExactly(
                "Validation readiness could not be inspected; the details were recorded in the server log.",
                "Event readiness could not be inspected; the details were recorded in the server log.",
                "Event flow readiness could not be inspected; the details were recorded in the server log.",
                "Editor readiness could not be inspected; the details were recorded in the server log.",
                "Integration readiness could not be inspected; the details were recorded in the server log.");
        assertThat(String.join("\n", result.warnings())).doesNotContain(root.toString(), ".json", "\\", "/", "orders-editor", "Exception");
        assertThat(result.recommendedNextSteps()).as("Home does not ask to configure a layer that exists but cannot be read")
                .doesNotContain("Configure validations and quick fixes", "Configure events and bindings", "Configure event flows",
                        "Configure model editors and representations", "Configure technology integrations");
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void aCorruptMetamodelStillFailsAsReadingTheMetamodelItselfDoes() throws Exception {
        metamodel("broken");
        corrupt("metamodels/broken.metamodel.json");
        Map<String, String> before = snapshot();

        assertThatThrownBy(() -> readiness.summarize("broken")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> metamodels.findById("broken")).isInstanceOf(RuntimeException.class);
        assertThat(snapshot()).isEqualTo(before);
    }

    private void metamodel(String id) {
        MetaClass node = new MetaClass(id + ".Node", "Node", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        metamodels.create(new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, id, id, List.of(node), List.of(), List.of(), List.of(), Map.of()));
    }

    private void events(String metamodelId) {
        eventDefinitions.create(new EventDefinitionModel("", metamodelId, metamodelId, metamodelId, List.of(), Map.of()));
        eventBindings.create(new EventBindingModel("", metamodelId, metamodelId, metamodelId, "", List.of(), Map.of()));
    }

    private void corrupt(String relative) throws Exception {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, CORRUPT, StandardCharsets.UTF_8);
        damaged.add(file);
    }

    /** The tests share one data directory: the damage of one test must not reach the next one (editors and integrations are scanned). */
    @AfterEach
    void removeDamage() throws Exception {
        for (Path file : damaged) Files.deleteIfExists(file);
        damaged.clear();
    }

    private static HydraCapabilityLayerSummary layer(HydraCapabilityReadiness result, String id) {
        return result.layers().stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
    }

    /** SHA-256 and modification time of every file of the modules that the readiness reads. */
    private static Map<String, String> snapshot() throws Exception {
        Map<String, String> files = new TreeMap<>();
        for (String module : MODULES) {
            Path directory = root.resolve(module);
            if (!Files.isDirectory(directory)) continue;
            try (Stream<Path> walk = Files.walk(directory)) {
                for (Path file : walk.filter(Files::isRegularFile).toList()) {
                    String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
                    files.put(root.relativize(file).toString(), digest + "@" + Files.getLastModifiedTime(file).toMillis());
                }
            }
        }
        return files;
    }
}
