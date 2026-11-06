package com.example.editor.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.editor.config.StorageProperties;
import com.example.editor.event.persistence.JsonEventBindingModelRepository;
import com.example.editor.event.persistence.JsonEventDefinitionModelRepository;
import com.example.editor.event.web.CanonicalEventModelController;
import com.example.editor.event.web.EventModelRequest;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.semantic.expression.ExpressionTestFixtures;
import com.example.editor.validation.expression.ExpressionModelProperties;
import com.example.editor.validation.expression.persistence.JsonExpressionModelRepository;
import com.example.editor.web.ApiExceptionHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia FUERTE de la autoría y persistencia del modelo de eventos (tipos, campos, enlaces y
 * copias de datos) con EventModelApplicationService, repositorios JSON reales sobre {@code @TempDir},
 * EventModelChecker real y el controlador REST real (MockMvc standalone). El oráculo es el JSON
 * crudo leído del disco y un SHA-256 recalculado aquí, nunca la respuesta del código bajo prueba.
 * Etapas: events_define.a1..a6, events_author_form.d1, events_author_form.a8 y
 * events_author_connect.a5.
 */
class EventModelAuthoringPersistenceIntegrationTests {

    private static final String LIB = ExpressionTestFixtures.METAMODEL_ID;

    private record Stack(JsonMapper mapper, EventModelApplicationService service, EventModelChecker checker,
            Path definitionsFile, Path bindingsFile) { }

    private static Stack stack(Path temporary) {
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        StorageProperties storage = new StorageProperties(temporary.resolve("metamodels"), temporary.resolve("models"),
                temporary.resolve("notations"), temporary.resolve("validations"), temporary.resolve("quickfixes"),
                temporary.resolve("generators"), temporary.resolve("assets"), temporary.resolve("exports"), true);
        CanonicalMetamodelApplicationService metamodelService = new CanonicalMetamodelApplicationService(
                new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());
        metamodelService.create(ExpressionTestFixtures.metamodel());
        EventModelProperties properties = new EventModelProperties();
        properties.setDirectory(temporary.resolve("events"));
        EventModelChecker checker = new EventModelChecker();
        EventModelApplicationService service = new EventModelApplicationService(
                new JsonEventDefinitionModelRepository(properties, mapper),
                new JsonEventBindingModelRepository(properties, mapper),
                new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper),
                metamodelService, checker);
        return new Stack(mapper, service, checker,
                temporary.resolve("events").resolve("library.event-definitions.json"),
                temporary.resolve("events").resolve("library.event-bindings.json"));
    }

    private static EventFieldDefinition field(String id, String name, EventFieldType type, boolean required, String enumTypeId) {
        return new EventFieldDefinition(id, name, type, required, false, enumTypeId, "", "");
    }

    private static EventTypeDefinition type(String id, String name, String namespace, List<EventFieldDefinition> fields) {
        return new EventTypeDefinition(id, name, namespace, "", "LOANS", "1.0", fields, Set.of(),
                Set.of(EventSubjectKind.MODEL_ELEMENT), EventPersistencePolicy.AUDIT, false, false, true, Map.of());
    }

    private static EventTypeDefinition loanApproved() {
        return type("course.library.loan-approved", "LoanApproved", "course.library", List.of(
                field("field:loanId", "loanId", EventFieldType.ELEMENT_ID, true, ""),
                field("field:note", "note", EventFieldType.STRING, false, ""),
                field("field:level", "level", EventFieldType.ENUM, false, "enum:level")));
    }

    private static EventDefinitionModel definitions(EventTypeDefinition... types) {
        return new EventDefinitionModel("1.0", "events", LIB, "course.library", List.of(types), Map.of());
    }

    private static EventBinding savedToLoan(List<EventPayloadMapping> mappings) {
        return new EventBinding("binding:saved-to-loan", "Saved to loan", "core.model.saved", "course.library.loan-approved", "",
                mappings, true, EventBindingErrorPolicy.REPORT, Map.of());
    }

    private static EventBindingModel bindings(EventBinding... values) {
        return new EventBindingModel("1.0", "event-bindings", LIB, "events", "", List.of(values), Map.of());
    }

    private static EventBindingModel validBindings() {
        return bindings(savedToLoan(List.of(new EventPayloadMapping("field:loanId", "payload.loanId", "", null))));
    }

    private static List<String> codes(EventModelCheckReport report) {
        return report.diagnostics().stream().map(EventDiagnostic::code).toList();
    }

    /** Oráculo independiente: el ETag es el SHA-256 hexadecimal entrecomillado de los bytes del fichero. */
    private static String etagOf(Path file) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
        return "\"" + HexFormat.of().formatHex(digest) + "\"";
    }

    @Test
    @DisplayName("events_define.a1-a4/a6 + events_author_form.d1: tipos, campos, enlaces y copias quedan en el JSON crudo")
    void validTypesFieldsAndBindingsArePersistedAsRawJson(@TempDir Path temporary) throws Exception {
        Stack s = stack(temporary);

        EventModelSaveResponse saved = s.service().create(LIB, definitions(loanApproved()), validBindings());

        assertThat(saved.checkReport().diagnostics()).isEmpty();
        JsonNode defs = s.mapper().readTree(Files.readString(s.definitionsFile(), StandardCharsets.UTF_8));
        assertThat(defs.get("id").asString()).isEqualTo("events");
        assertThat(defs.get("namespace").asString()).isEqualTo("course.library");
        assertThat(defs.get("eventTypes").size()).isEqualTo(1);
        JsonNode type = defs.get("eventTypes").get(0);
        assertThat(type.get("id").asString()).isEqualTo("course.library.loan-approved");
        assertThat(type.get("name").asString()).isEqualTo("LoanApproved");
        assertThat(type.get("namespace").asString()).isEqualTo("course.library");
        assertThat(type.get("persistencePolicy").asString()).isEqualTo("AUDIT");
        JsonNode fields = type.get("fields");
        assertThat(fields.size()).isEqualTo(3);
        assertThat(fields.get(0).get("id").asString()).isEqualTo("field:loanId");
        assertThat(fields.get(0).get("type").asString()).isEqualTo("ELEMENT_ID");
        assertThat(fields.get(0).get("required").asBoolean()).isTrue();
        assertThat(fields.get(1).get("type").asString()).isEqualTo("STRING");
        assertThat(fields.get(2).get("type").asString()).isEqualTo("ENUM");
        assertThat(fields.get(2).get("enumTypeId").asString()).isEqualTo("enum:level");

        JsonNode binds = s.mapper().readTree(Files.readString(s.bindingsFile(), StandardCharsets.UTF_8));
        JsonNode binding = binds.get("bindings").get(0);
        assertThat(binds.get("eventDefinitionModelId").asString()).isEqualTo("events");
        assertThat(binding.get("id").asString()).isEqualTo("binding:saved-to-loan");
        assertThat(binding.get("sourceEventTypeId").asString()).isEqualTo("core.model.saved");
        assertThat(binding.get("targetEventTypeId").asString()).isEqualTo("course.library.loan-approved");
        assertThat(binding.get("errorPolicy").asString()).isEqualTo("REPORT");
        assertThat(binding.get("enabled").asBoolean()).isTrue();
        assertThat(binding.get("payloadMappings").get(0).get("targetFieldId").asString()).isEqualTo("field:loanId");
        assertThat(binding.get("payloadMappings").get(0).get("sourcePath").asString()).isEqualTo("payload.loanId");

        assertThat(saved.definitionEtag()).isEqualTo(etagOf(s.definitionsFile()));
        assertThat(saved.bindingEtag()).isEqualTo(etagOf(s.bindingsFile()));
        assertThat(s.service().find(LIB).checkReport().diagnostics()).isEmpty();
    }

    @Test
    @DisplayName("events_define.a6: replace con ETag obsoleto conflicta y deja intactos los ficheros; con el ETag correcto persiste")
    void replaceWithStaleEtagConflictsAndLeavesFilesIntactWhileCurrentEtagPersists(@TempDir Path temporary) throws Exception {
        Stack s = stack(temporary);
        EventModelSaveResponse saved = s.service().create(LIB, definitions(loanApproved()), validBindings());
        byte[] definitionsBefore = Files.readAllBytes(s.definitionsFile());
        byte[] bindingsBefore = Files.readAllBytes(s.bindingsFile());
        EventDefinitionModel changed = definitions(loanApproved(),
                type("course.library.loan-rejected", "LoanRejected", "course.library", List.of()));

        assertThatThrownBy(() -> s.service().replace(LIB, changed, "\"stale\"", validBindings(), saved.bindingEtag()))
                .isInstanceOfSatisfying(EventModelApplicationException.class,
                        e -> assertThat(e.getReason()).isEqualTo(EventModelApplicationException.Reason.PRECONDITION_FAILED));
        assertThat(Files.readAllBytes(s.definitionsFile())).isEqualTo(definitionsBefore);
        assertThat(Files.readAllBytes(s.bindingsFile())).isEqualTo(bindingsBefore);

        EventModelSaveResponse replaced = s.service().replace(LIB, changed, saved.definitionEtag(), validBindings(), saved.bindingEtag());

        assertThat(Files.readAllBytes(s.definitionsFile())).isNotEqualTo(definitionsBefore);
        assertThat(Files.readAllBytes(s.bindingsFile())).isEqualTo(bindingsBefore);
        JsonNode defs = s.mapper().readTree(Files.readString(s.definitionsFile(), StandardCharsets.UTF_8));
        assertThat(defs.get("eventTypes").size()).isEqualTo(2);
        assertThat(defs.get("eventTypes").get(1).get("id").asString()).isEqualTo("course.library.loan-rejected");
        assertThat(replaced.definitionEtag()).isEqualTo(etagOf(s.definitionsFile())).isNotEqualTo(saved.definitionEtag());
        assertThatThrownBy(() -> s.service().replace(LIB, changed, saved.definitionEtag(), validBindings(), saved.bindingEtag()))
                .isInstanceOfSatisfying(EventModelApplicationException.class,
                        e -> assertThat(e.getReason()).isEqualTo(EventModelApplicationException.Reason.PRECONDITION_FAILED));
    }

    @Test
    @DisplayName("events_define.a6 + events_author_form.a8: la API REST real guarda con If-Match y rechaza ETag obsoleto con 412")
    void theRealRestControllerPersistsWithIfMatchAndRejectsAStaleEtag(@TempDir Path temporary) throws Exception {
        Stack s = stack(temporary);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new CanonicalEventModelController(s.service()))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        String original = s.mapper().writeValueAsString(new EventModelRequest(definitions(loanApproved()), validBindings()));

        MvcResult created = mockMvc.perform(post("/api/canonical-metamodels/" + LIB + "/events")
                .contentType(MediaType.APPLICATION_JSON).content(original)).andExpect(status().isCreated()).andReturn();
        String definitionEtag = created.getResponse().getHeader("X-Event-Definitions-ETag");
        String bindingEtag = created.getResponse().getHeader("X-Event-Bindings-ETag");
        assertThat(definitionEtag).isEqualTo(etagOf(s.definitionsFile()));
        assertThat(bindingEtag).isEqualTo(etagOf(s.bindingsFile()));
        byte[] definitionsBefore = Files.readAllBytes(s.definitionsFile());

        String changed = s.mapper().writeValueAsString(new EventModelRequest(definitions(loanApproved(),
                type("course.library.loan-rejected", "LoanRejected", "course.library", List.of())), validBindings()));
        mockMvc.perform(put("/api/canonical-metamodels/" + LIB + "/events")
                .header("If-Match-Definitions", "\"stale\"").header("If-Match-Bindings", bindingEtag)
                .contentType(MediaType.APPLICATION_JSON).content(changed)).andExpect(status().isPreconditionFailed());
        assertThat(Files.readAllBytes(s.definitionsFile())).isEqualTo(definitionsBefore);

        MvcResult replaced = mockMvc.perform(put("/api/canonical-metamodels/" + LIB + "/events")
                .header("If-Match-Definitions", definitionEtag).header("If-Match-Bindings", bindingEtag)
                .contentType(MediaType.APPLICATION_JSON).content(changed)).andExpect(status().isOk()).andReturn();
        assertThat(replaced.getResponse().getHeader("X-Event-Definitions-ETag")).isEqualTo(etagOf(s.definitionsFile()));
        assertThat(s.mapper().readTree(Files.readString(s.definitionsFile(), StandardCharsets.UTF_8)).get("eventTypes").size()).isEqualTo(2);
    }

    @Test
    @DisplayName("events_define.a3/a4/a5 + events_author_connect.a5: el comprobador real da los codigos exactos y el guardado se bloquea")
    void theCheckerGivesExactDiagnosticCodesAndBlockingErrorsAreNeverPersisted(@TempDir Path temporary) throws Exception {
        Stack s = stack(temporary);
        EventModelChecker checker = s.checker();
        EventDefinitionModel valid = definitions(loanApproved());
        List<EventPayloadMapping> okMapping = List.of(new EventPayloadMapping("field:loanId", "payload.loanId", "", null));

        // origen desconocido
        assertThat(codes(checker.check(valid, bindings(new EventBinding("binding:a", "A", "unknown.event",
                "course.library.loan-approved", "", okMapping, true, EventBindingErrorPolicy.REPORT, Map.of())), null)))
                .containsExactly("UNKNOWN_SOURCE_EVENT");
        // destino desconocido y destino reservado (el destino debe ser un evento propio)
        assertThat(codes(checker.check(valid, bindings(new EventBinding("binding:b", "B", "core.model.saved",
                "course.library.nothing", "", okMapping, true, EventBindingErrorPolicy.REPORT, Map.of())), null)))
                .containsExactly("UNKNOWN_TARGET_EVENT");
        assertThat(codes(checker.check(valid, bindings(new EventBinding("binding:c", "C", "core.model.saved",
                "core.model.exported", "", okMapping, true, EventBindingErrorPolicy.REPORT, Map.of())), null)))
                .containsExactly("UNKNOWN_TARGET_EVENT");
        // campo obligatorio sin copiar
        assertThat(codes(checker.check(valid, bindings(savedToLoan(List.of())), null)))
                .containsExactly("REQUIRED_FIELD_NOT_MAPPED");
        // copia a un campo inexistente y copia duplicada al mismo campo
        assertThat(codes(checker.check(valid, bindings(savedToLoan(List.of(
                new EventPayloadMapping("field:loanId", "payload.loanId", "", null),
                new EventPayloadMapping("field:loanId", "payload.other", "", null),
                new EventPayloadMapping("field:ghost", "payload.ghost", "", null)))), null)))
                .containsExactlyInAnyOrder("DUPLICATE_TARGET_MAPPING", "UNKNOWN_TARGET_FIELD");
        // tipo reservado redefinido
        assertThat(codes(checker.check(definitions(type("core.model.saved", "Saved", "course.library", List.of())), null, null)))
                .containsExactly("RESERVED_EVENT_TYPE");
        // campos duplicados y tipo duplicado
        EventFieldDefinition dup = field("field:x", "x", EventFieldType.STRING, false, "");
        assertThat(codes(checker.check(definitions(type("course.library.dup", "Dup", "course.library", List.of(dup, dup))), null, null)))
                .containsExactlyInAnyOrder("DUPLICATE_FIELD_ID", "DUPLICATE_FIELD_NAME");
        assertThat(codes(checker.check(definitions(type("course.library.same", "Same", "course.library", List.of()),
                type("course.library.same", "Same", "course.library", List.of())), null, null)))
                .containsExactly("DUPLICATE_EVENT_TYPE");

        // un error bloqueante impide guardar: nada llega a disco
        assertThatThrownBy(() -> s.service().create(LIB, valid, bindings(savedToLoan(List.of()))))
                .isInstanceOfSatisfying(EventModelApplicationException.class,
                        e -> assertThat(e.getReason()).isEqualTo(EventModelApplicationException.Reason.INVALID_REQUEST));
        assertThat(s.definitionsFile()).doesNotExist();
        assertThat(s.bindingsFile()).doesNotExist();

        // un aviso (espacio de nombres ajeno) no bloquea: se guarda y el informe conserva el WARNING
        EventDefinitionModel foreign = definitions(type("other.space.audit", "Audit", "other.space", List.of()));
        EventModelSaveResponse saved = s.service().create(LIB, foreign, bindings());
        assertThat(saved.checkReport().valid()).isTrue();
        assertThat(saved.checkReport().diagnostics()).singleElement().satisfies(d -> {
            assertThat(d.code()).isEqualTo("FOREIGN_NAMESPACE");
            assertThat(d.severity()).isEqualTo(EventDiagnosticSeverity.WARNING);
        });
        assertThat(s.definitionsFile()).exists();
        assertThat(codes(s.service().check(LIB))).containsExactly("FOREIGN_NAMESPACE");
    }

    @Test
    @DisplayName("events_define.a1/a2/a4 + events_author_form.d1: el JSON crudo invalido se rechaza; ENUM con enumTypeId guarda")
    void rawJsonWithInvalidTypesFieldsOrMappingsIsRejectedAndAValidEnumIsSaved(@TempDir Path temporary) throws Exception {
        Stack s = stack(temporary);
        JsonMapper mapper = s.mapper();

        assertThatThrownBy(() -> mapper.readValue(
                "{\"id\":\"course.library.t\",\"name\":\"  \",\"namespace\":\"course.library\",\"abstractType\":false,\"deprecated\":false,\"enabled\":true}", EventTypeDefinition.class))
                .hasStackTraceContaining("eventType.name must not be blank");
        assertThatThrownBy(() -> mapper.readValue(
                "{\"id\":\"course.library.t\",\"name\":\"T\",\"namespace\":\"1course\",\"abstractType\":false,\"deprecated\":false,\"enabled\":true}", EventTypeDefinition.class))
                .hasStackTraceContaining("eventType.namespace is invalid");
        assertThatThrownBy(() -> mapper.readValue(
                "{\"id\":\"events\",\"metamodelId\":\"library\",\"namespace\":\"9library\"}", EventDefinitionModel.class))
                .hasStackTraceContaining("namespace is invalid");
        assertThatThrownBy(() -> mapper.readValue(
                "{\"id\":\"field:level\",\"name\":\"level\",\"type\":\"ENUM\",\"required\":false,\"many\":false}", EventFieldDefinition.class))
                .hasStackTraceContaining("ENUM fields require enumTypeId");
        assertThatThrownBy(() -> mapper.readValue(
                "{\"targetFieldId\":\"field:a\",\"sourcePath\":\"payload.a\",\"fixedValue\":\"x\"}", EventPayloadMapping.class))
                .hasStackTraceContaining("Exactly one mapping source must be configured");

        EventFieldDefinition withEnum = mapper.readValue(
                "{\"id\":\"field:level\",\"name\":\"level\",\"type\":\"ENUM\",\"required\":false,\"many\":false,\"enumTypeId\":\"enum:level\"}", EventFieldDefinition.class);
        assertThat(withEnum.enumTypeId()).isEqualTo("enum:level");
        s.service().create(LIB, definitions(type("course.library.levelled", "Levelled", "course.library", List.of(withEnum))), bindings());
        JsonNode raw = mapper.readTree(Files.readString(s.definitionsFile(), StandardCharsets.UTF_8));
        assertThat(raw.get("eventTypes").get(0).get("fields").get(0).get("enumTypeId").asString()).isEqualTo("enum:level");
    }
}
