package com.example.editor.metamodel.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.editor.definition.EditorCapability;
import com.example.editor.definition.EditorCapabilityManifest;
import com.example.editor.definition.EditorDefinitionModel;
import com.example.editor.definition.RepresentationDefinition;
import com.example.editor.definition.RepresentationKind;
import com.example.editor.definition.ToolPaletteDefinition;
import com.example.editor.definition.persistence.EditorDefinitionRepository;
import com.example.editor.event.EventBindingModel;
import com.example.editor.event.EventDefinitionModel;
import com.example.editor.event.persistence.EventBindingModelRepository;
import com.example.editor.event.persistence.EventDefinitionModelRepository;
import com.example.editor.gamification.GamificationDefinitionModel;
import com.example.editor.gamification.PointTypeDefinition;
import com.example.editor.gamification.persistence.GamificationDefinitionRepository;
import com.example.editor.integration.InjectionPolicy;
import com.example.editor.integration.InjectionStrategy;
import com.example.editor.integration.IntegrationArtifactKind;
import com.example.editor.integration.IntegrationArtifactSelection;
import com.example.editor.integration.IntegrationBuildSystem;
import com.example.editor.integration.IntegrationDeliveryMode;
import com.example.editor.integration.IntegrationFeature;
import com.example.editor.integration.IntegrationProfileModel;
import com.example.editor.integration.IntegrationTarget;
import com.example.editor.integration.IntegrationTargetKind;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.persistence.IntegrationProfileRepository;
import com.example.editor.metamodel.canonical.CanonicalMetamodelTestFixtures;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;
import com.example.editor.metamodel.design.MetamodelDiagramStorageService;
import com.example.editor.metamodel.web.MetamodelHttpTestEnvironment;
import com.example.editor.validation.validation.ValidationModel;
import com.example.editor.validation.validation.persistence.ValidationModelRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * metamodel_delete_cascade.prep / .prim / .rec / .ab (and x_metamodel_save_and_cascade.prep / .prim / .c_ed / .c_ev /
 * .c_gm / .rec) - cascading deletion of a metamodel and recovery over real HTTP.
 *
 * Full Spring context + MockMvc. Dependents are seeded through the real repositories (validation rules, events and
 * bindings, editor definition, integration profile, gamification definitions, diagram) plus a notation and an export
 * file; nothing is mocked. The oracle is the file system under {@code @TempDir} and the deletion journal JSON on disk.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MetamodelDeleteCascadeHttpIntegrationTests {

    @TempDir
    static Path root;

    @DynamicPropertySource
    static void directories(DynamicPropertyRegistry registry) {
        MetamodelHttpTestEnvironment.register(registry, root);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper mapper;
    @Autowired private ValidationModelRepository validations;
    @Autowired private EventDefinitionModelRepository eventDefinitions;
    @Autowired private EventBindingModelRepository eventBindings;
    @Autowired private EditorDefinitionRepository editors;
    @Autowired private IntegrationProfileRepository integrations;
    @Autowired private GamificationDefinitionRepository gamification;
    @Autowired private MetamodelDiagramStorageService diagrams;
    @Autowired private MetamodelDeletionJournalService journals;

    /** Seeds a metamodel and one artifact of every dependent family; returns the metamodel ETag. */
    private String seed(String id) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/canonical-metamodels").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(CanonicalMetamodelTestFixtures.valid(id))))
                .andExpect(status().isCreated()).andReturn();
        diagrams.save(id, new MetamodelDiagramDocument(id, List.of(new MetamodelDiagramDocument.NodeDesign("class:root", 10, 20, 200, 110)), List.of()));
        seedResidues(id);
        Files.createDirectories(root.resolve("notations"));
        Files.writeString(root.resolve("notations").resolve(id + ".notation.json"), "{\"metamodelId\":\"" + id + "\"}");
        Files.createDirectories(root.resolve("exports"));
        Files.writeString(root.resolve("exports").resolve(id + ".ecore"), "<ecore/>");
        return created.getResponse().getHeader(HttpHeaders.ETAG);
    }

    /** Seeds the dependents that are removable after the canonical document is gone. */
    private void seedResidues(String id) {
        validations.create(new ValidationModel("1.0", id, id, id, "", List.of(), List.of(), List.of(), Map.of()));
        eventDefinitions.create(new EventDefinitionModel("1.0", id + "-events", id, "course." + id, List.of(), Map.of()));
        eventBindings.create(new EventBindingModel("1.0", id + "-event-bindings", id, id + "-events", "", List.of(), Map.of()));
        editors.create(editor(id + "-main-editor", id));
        integrations.create(integration(id + "-plantuml-integration", id));
        gamification.create(new GamificationDefinitionModel("1.0", id + "-gamification", id, "Gamification",
                List.of(new PointTypeDefinition("points:quality", "Quality", "", 0)), List.of(), List.of(), List.of(), List.of(), Map.of()));
    }

    private static List<Path> filesStartingWith(Path directory, String prefix) throws Exception {
        if (!Files.isDirectory(directory)) return List.of();
        try (var stream = Files.list(directory)) {
            return stream.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().startsWith(prefix)).toList();
        }
    }

    /** Paths that must all exist while the metamodel is alive and all vanish with the cascade. */
    private static List<List<Path>> dependents(String id) throws Exception {
        return List.of(
                filesStartingWith(root.resolve("metamodels"), id + "."),
                filesStartingWith(root.resolve("validations"), id + "."),
                filesStartingWith(root.resolve("events"), id + "."),
                filesStartingWith(root.resolve("editors"), id + "-main-editor"),
                filesStartingWith(root.resolve("integrations"), id + "-plantuml-integration"),
                filesStartingWith(root.resolve("gamification"), id + "."),
                filesStartingWith(root.resolve("notations"), id + "."),
                filesStartingWith(root.resolve("exports"), id + "."));
    }

    private static int existingCount(String id) throws Exception {
        return dependents(id).stream().mapToInt(List::size).sum();
    }

    private JsonNode journal(String id) throws Exception {
        Path file = root.resolve("recovery").resolve("metamodel-deletions").resolve(id + ".deletion-journal.json");
        assertThat(file).as("deletion journal").isRegularFile();
        return mapper.readTree(Files.readAllBytes(file));
    }

    @Test
    @DisplayName("metamodel_delete_cascade.prep: the deletion-impact endpoint lists every dependent artifact before anything is deleted")
    void deletionImpactListsTheDependents() throws Exception {
        String id = "del-impact";
        seed(id);

        mockMvc.perform(get("/api/canonical-metamodels/" + id + "/deletion-impact"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canonicalExists").value(true))
                .andExpect(jsonPath("$.diagramExists").value(true))
                .andExpect(jsonPath("$.validationExists").value(true))
                .andExpect(jsonPath("$.eventDefinitionExists").value(true))
                .andExpect(jsonPath("$.eventBindingExists").value(true))
                .andExpect(jsonPath("$.editorDefinitionFiles[0]").value(id + "-main-editor.editor.json"))
                .andExpect(jsonPath("$.integrationProfileFiles[0]").value(id + "-plantuml-integration.integration.json"))
                .andExpect(jsonPath("$.notationFiles[0]").value(id + ".notation.json"));
        // Analysis is read-only.
        assertThat(dependents(id)).allSatisfy(group -> assertThat(group).isNotEmpty());
    }

    @Test
    @DisplayName("metamodel_delete_cascade.ab / prep: DELETE with an obsolete ETag answers 412, the journal is ABORTED and every dependent is intact")
    void obsoleteEtagAbortsTheDeletionAndKeepsEverything() throws Exception {
        String id = "del-abort";
        seed(id);
        int before = existingCount(id);

        mockMvc.perform(delete("/api/canonical-metamodels/" + id + "/lifecycle").header(HttpHeaders.IF_MATCH, "\"obsolete\""))
                .andExpect(status().isPreconditionFailed());

        JsonNode journal = journal(id);
        assertThat(journal.get("status").asString()).isEqualTo("ABORTED");
        assertThat(journal.get("expectedFiles")).isNotEmpty();
        assertThat(existingCount(id)).isEqualTo(before);
        mockMvc.perform(get("/api/canonical-metamodels/" + id)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("metamodel_delete_cascade.prim: DELETE with the valid ETag removes the metamodel and every dependent; the journal is COMPLETED")
    void validEtagDeletesTheMetamodelAndAllDependents() throws Exception {
        String id = "del-cascade";
        String etag = seed(id);
        assertThat(dependents(id)).allSatisfy(group -> assertThat(group).isNotEmpty());

        mockMvc.perform(delete("/api/canonical-metamodels/" + id + "/lifecycle").header(HttpHeaders.IF_MATCH, etag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metamodelId").value(id))
                .andExpect(jsonPath("$.pendingFiles").isEmpty());

        assertThat(dependents(id)).allSatisfy(group -> assertThat(group).isEmpty());
        mockMvc.perform(get("/api/canonical-metamodels/" + id)).andExpect(status().isNotFound());
        JsonNode journal = journal(id);
        assertThat(journal.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(journal.get("pendingFiles")).isEmpty();
        assertThat(journal.get("expectedFiles")).isNotEmpty();
    }

    @Test
    @DisplayName("metamodel_delete_cascade.rec: after a crash (PREPARED journal, canonical absent) the recovery endpoint removes the residues and completes the journal")
    void recoveryAfterACrashCleansTheResidues() throws Exception {
        String id = "del-crash";
        String etag = seed(id);
        mockMvc.perform(delete("/api/canonical-metamodels/" + id + "/lifecycle").header(HttpHeaders.IF_MATCH, etag)).andExpect(status().isOk());
        assertThat(existingCount(id)).isZero();

        // Simulate the crash: residues are back and the journal says the deletion never moved past PREPARED.
        seedResidues(id);
        journals.begin(id, true, true, List.of("validations/" + id + ".validations.json",
                "editors/" + id + "-main-editor.editor.json", "integrations/" + id + "-plantuml-integration.integration.json",
                "events/" + id + ".event-definitions.json"));
        assertThat(existingCount(id)).isPositive();
        assertThat(journal(id).get("status").asString()).isEqualTo("PREPARED");

        mockMvc.perform(post("/api/canonical-metamodels/" + id + "/deletion-recovery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingFiles").isEmpty());

        assertThat(existingCount(id)).isZero();
        assertThat(journal(id).get("status").asString()).isEqualTo("COMPLETED");
        mockMvc.perform(get("/api/canonical-metamodels/" + id)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("metamodel_delete_cascade.rec: a RECOVERY_REQUIRED journal with pending files is resumed to COMPLETED without repeating the primary deletion")
    void recoveryRequiredJournalIsResumedToCompleted() throws Exception {
        String id = "del-resume";
        String etag = seed(id);
        mockMvc.perform(delete("/api/canonical-metamodels/" + id + "/lifecycle").header(HttpHeaders.IF_MATCH, etag)).andExpect(status().isOk());

        seedResidues(id);
        List<String> pending = List.of("validations/" + id + ".validations.json", "editors/" + id + "-main-editor.editor.json",
                "integrations/" + id + "-plantuml-integration.integration.json");
        MetamodelDeletionJournal prepared = journals.begin(id, true, true, pending);
        journals.markRecoveryRequired(journals.markPrimaryDeleted(prepared), pending);
        assertThat(journal(id).get("status").asString()).isEqualTo("RECOVERY_REQUIRED");

        mockMvc.perform(post("/api/canonical-metamodels/" + id + "/deletion-recovery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingFiles").isEmpty());

        assertThat(existingCount(id)).isZero();
        assertThat(journal(id).get("status").asString()).isEqualTo("COMPLETED");
        // A completed deletion cannot be "recovered" again: nothing to repeat.
        mockMvc.perform(post("/api/canonical-metamodels/" + id + "/deletion-recovery")).andExpect(status().isBadRequest());
    }

    private static IntegrationProfileModel integration(String id, String metamodelId) {
        IntegrationTarget target = new IntegrationTarget(IntegrationTechnology.PLANTUML,
                IntegrationTargetKind.DIAGRAM_PROJECTION, "", metamodelId + "-docs", "", IntegrationBuildSystem.NONE,
                List.of(), List.of(), "", Map.of());
        InjectionPolicy policy = new InjectionPolicy(InjectionStrategy.GENERATE_PROJECTION, "docs/diagrams", "",
                true, true, true, false, false, true);
        return new IntegrationProfileModel("1.0", id, metamodelId, "PlantUML", target,
                IntegrationDeliveryMode.PROJECTION_ONLY, Set.of(IntegrationFeature.PROJECT_EXPORT),
                List.of(new IntegrationArtifactSelection(IntegrationArtifactKind.METAMODEL, metamodelId, true)),
                List.of(), policy, Set.of(), Map.of());
    }

    private static EditorDefinitionModel editor(String id, String metamodelId) {
        var representation = new RepresentationDefinition("representation:" + id, "Main", RepresentationKind.DIAGRAM,
                List.of(), List.of(), List.of(), List.of(), true, Map.of());
        return new EditorDefinitionModel("1.0", id, metamodelId, id, List.of(representation),
                new ToolPaletteDefinition(List.of(), List.of()), List.of(),
                new EditorCapabilityManifest(Set.of(EditorCapability.MODEL_EDITING)), Map.of());
    }
}
