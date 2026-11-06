package com.example.editor.event.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.config.StorageProperties;
import com.example.editor.event.EventModelApplicationService;
import com.example.editor.event.EventModelChecker;
import com.example.editor.event.EventModelProperties;
import com.example.editor.event.flow.diagram.EventFlowDiagramDocument;
import com.example.editor.event.flow.diagram.EventFlowDiagramLoadResult;
import com.example.editor.event.flow.diagram.EventFlowDiagramService;
import com.example.editor.event.flow.diagram.EventFlowNodeView;
import com.example.editor.event.flow.diagram.JsonEventFlowDiagramRepository;
import com.example.editor.event.flow.persistence.JsonEventFlowModelRepository;
import com.example.editor.event.persistence.JsonEventBindingModelRepository;
import com.example.editor.event.persistence.JsonEventDefinitionModelRepository;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.semantic.expression.ExpressionTestFixtures;
import com.example.editor.validation.expression.ExpressionModelProperties;
import com.example.editor.validation.expression.persistence.JsonExpressionModelRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia real, sin mocks, de guardar un flujo y su diagrama con control de versiones
 * (events_author_flow.a7 y el artefacto r1 "Flujo guardado"). Servicios y repositorios JSON reales sobre
 * {@code @TempDir}; el oraculo es el JSON crudo del disco y la huella SHA-256 recalculada aqui, que debe
 * coincidir con la version que devuelve el servicio.
 */
class EventFlowSaveVersionControlIntegrationTests {

    private static final String LIB = ExpressionTestFixtures.METAMODEL_ID;
    private static final List<String> TRIGGER = List.of("core.model.saved");

    private static FlowNode node(String id, FlowNodeKind kind) {
        return new FlowNode(id, id, kind, "", "", "", Map.of(), null, null, false, true);
    }

    private static FlowDefinition minimalFlow(String id) {
        return new FlowDefinition(id, id, "", true, TRIGGER, List.of(),
                List.of(node("node:start", FlowNodeKind.START), node("node:end", FlowNodeKind.SUCCESS_END)),
                List.of(new FlowTransition("transition:start-end", "node:start", "node:end", FlowTransitionKind.NEXT, "", 0, "")),
                List.of(), Map.of());
    }

    private static FlowDefinition flowWithoutEnd() {
        return new FlowDefinition("flow:broken", "flow:broken", "", true, TRIGGER, List.of(), List.of(node("node:start", FlowNodeKind.START)),
                List.of(), List.of(), Map.of());
    }

    private static EventFlowModel model(FlowDefinition... flows) {
        return new EventFlowModel(null, "flows-library", LIB, "", "", List.of(flows), Map.of());
    }

    private static String etagOf(Path file) throws Exception {
        return "\"" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))) + "\"";
    }

    @Test
    @DisplayName("events_author_flow.a7/r1: el flujo y su diagrama se guardan con version; una version vieja o un flujo incompleto se rechazan sin tocar el disco")
    void aFlowAndItsDiagramAreSavedWithVersionControl(@TempDir Path temporary) throws Exception {
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        StorageProperties storage = new StorageProperties(temporary.resolve("metamodels"), temporary.resolve("models"),
                temporary.resolve("notations"), temporary.resolve("validations"), temporary.resolve("quickfixes"),
                temporary.resolve("generators"), temporary.resolve("assets"), temporary.resolve("exports"), true);
        CanonicalMetamodelApplicationService metamodelService = new CanonicalMetamodelApplicationService(
                new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());
        metamodelService.create(ExpressionTestFixtures.metamodel());
        EventModelProperties eventProperties = new EventModelProperties();
        eventProperties.setDirectory(temporary.resolve("events"));
        EventModelApplicationService eventService = new EventModelApplicationService(
                new JsonEventDefinitionModelRepository(eventProperties, mapper), new JsonEventBindingModelRepository(eventProperties, mapper),
                new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper), metamodelService, new EventModelChecker());
        EventFlowProperties flowProperties = new EventFlowProperties();
        flowProperties.setDirectory(temporary.resolve("flows"));
        EventFlowApplicationService flowService = new EventFlowApplicationService(new JsonEventFlowModelRepository(flowProperties, mapper),
                metamodelService, eventService, new EventFlowChecker(), new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper));
        EventFlowDiagramService diagramService = new EventFlowDiagramService(new JsonEventFlowDiagramRepository(flowProperties, mapper), flowService);
        Path flowFile = temporary.resolve("flows").resolve("library.event-flows.json");
        Path diagramFile = temporary.resolve("flows").resolve("library.event-flow-diagram.json");

        // Primera version: el flujo guardado es el fichero en disco y su version es la huella del fichero.
        EventFlowSaveResponse first = flowService.create(LIB, model(minimalFlow("flow:smoke")));
        JsonNode rawFirst = mapper.readTree(Files.readString(flowFile, StandardCharsets.UTF_8));
        assertThat(rawFirst.get("id").asString()).isEqualTo("flows-library");
        assertThat(rawFirst.get("flows").size()).isEqualTo(1);
        assertThat(rawFirst.get("flows").get(0).get("id").asString()).isEqualTo("flow:smoke");
        assertThat(rawFirst.get("flows").get(0).get("nodes").size()).isEqualTo(2);
        assertThat(first.etag()).isEqualTo(etagOf(flowFile));

        // Segunda version con la version vigente: cambia el contenido y cambia la version.
        EventFlowSaveResponse second = flowService.replace(LIB, model(minimalFlow("flow:smoke"), minimalFlow("flow:second")), first.etag());
        JsonNode rawSecond = mapper.readTree(Files.readString(flowFile, StandardCharsets.UTF_8));
        assertThat(rawSecond.get("flows").size()).isEqualTo(2);
        assertThat(rawSecond.get("flows").get(1).get("id").asString()).isEqualTo("flow:second");
        assertThat(second.etag()).isNotEqualTo(first.etag()).isEqualTo(etagOf(flowFile));
        assertThat(flowService.find(LIB).etag()).isEqualTo(second.etag());
        assertThat(flowService.find(LIB).eventFlowModel().flows()).extracting(FlowDefinition::id).containsExactly("flow:smoke", "flow:second");

        // Negativos: version caducada y flujo sin final se rechazan y el fichero no cambia ni un byte.
        byte[] before = Files.readAllBytes(flowFile);
        assertThatThrownBy(() -> flowService.replace(LIB, model(minimalFlow("flow:smoke")), first.etag())).isInstanceOfSatisfying(
                EventFlowApplicationException.class, e -> assertThat(e.getReason()).isEqualTo(EventFlowApplicationException.Reason.PRECONDITION_FAILED));
        assertThat(Files.readAllBytes(flowFile)).isEqualTo(before);
        assertThatThrownBy(() -> flowService.replace(LIB, model(minimalFlow("flow:smoke"), flowWithoutEnd()), second.etag())).isInstanceOfSatisfying(
                EventFlowApplicationException.class, e -> assertThat(e.getReason()).isEqualTo(EventFlowApplicationException.Reason.INVALID_REQUEST));
        assertThat(Files.readAllBytes(flowFile)).isEqualTo(before);

        // Diagrama: se guarda aparte, referencia el flujo vigente y tambien esta versionado.
        EventFlowDiagramDocument diagram = new EventFlowDiagramDocument(null, "diagram.flows", LIB, "flows-library",
                List.of(new EventFlowNodeView("node:start", 10, 20, 0, 0, false)), List.of());
        EventFlowDiagramLoadResult savedDiagram = diagramService.save(LIB, diagram, null);
        JsonNode rawDiagram = mapper.readTree(Files.readString(diagramFile, StandardCharsets.UTF_8));
        assertThat(rawDiagram.get("eventFlowModelId").asString()).isEqualTo("flows-library");
        assertThat(rawDiagram.get("nodes").get(0).get("nodeId").asString()).isEqualTo("node:start");
        assertThat(rawDiagram.get("nodes").get(0).get("x").asDouble()).isEqualTo(10.0d);
        assertThat(savedDiagram.etag()).isEqualTo(etagOf(diagramFile));
        byte[] diagramBefore = Files.readAllBytes(diagramFile);
        assertThatThrownBy(() -> diagramService.save(LIB, diagram, "\"stale\"")).isInstanceOfSatisfying(
                EventFlowApplicationException.class, e -> assertThat(e.getReason()).isEqualTo(EventFlowApplicationException.Reason.PRECONDITION_FAILED));
        assertThat(Files.readAllBytes(diagramFile)).isEqualTo(diagramBefore);
    }
}
