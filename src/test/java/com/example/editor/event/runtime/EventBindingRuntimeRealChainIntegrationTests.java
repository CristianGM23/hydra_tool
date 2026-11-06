package com.example.editor.event.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import com.example.editor.config.StorageProperties;
import com.example.editor.event.CanonicalEvent;
import com.example.editor.event.EventBinding;
import com.example.editor.event.EventBindingErrorPolicy;
import com.example.editor.event.EventBindingModel;
import com.example.editor.event.EventContext;
import com.example.editor.event.EventContextKind;
import com.example.editor.event.EventDefinitionModel;
import com.example.editor.event.EventFieldDefinition;
import com.example.editor.event.EventFieldType;
import com.example.editor.event.EventModelApplicationService;
import com.example.editor.event.EventModelChecker;
import com.example.editor.event.EventModelProperties;
import com.example.editor.event.EventPayloadMapping;
import com.example.editor.event.EventPersistencePolicy;
import com.example.editor.event.EventSource;
import com.example.editor.event.EventSubjectKind;
import com.example.editor.event.EventTypeDefinition;
import com.example.editor.event.bus.InMemoryCanonicalEventBus;
import com.example.editor.event.persistence.JsonEventBindingModelRepository;
import com.example.editor.event.persistence.JsonEventDefinitionModelRepository;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.semantic.expression.BinaryExpressionNode;
import com.example.editor.semantic.expression.BinaryExpressionOperator;
import com.example.editor.semantic.expression.ExpressionDefinition;
import com.example.editor.semantic.expression.ExpressionDependencyAnalyzer;
import com.example.editor.semantic.expression.ExpressionEvaluator;
import com.example.editor.semantic.expression.ExpressionTestFixtures;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
import com.example.editor.semantic.expression.ExpressionValueKind;
import com.example.editor.semantic.expression.ExpressionValueType;
import com.example.editor.semantic.expression.FunctionExpressionNode;
import com.example.editor.semantic.expression.LiteralExpressionNode;
import com.example.editor.semantic.expression.VariableExpressionNode;
import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.validation.expression.ExpressionModelProperties;
import com.example.editor.validation.expression.persistence.JsonExpressionModelRepository;
import com.example.editor.validation.expression.persistence.StoredExpressionModel;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia FUERTE de la cadena real de enlaces de eventos en ejecución: EventBindingRuntimeService
 * con bus real, EventModelApplicationService y repositorios JSON reales, repositorio de expresiones
 * JSON real y ExpressionEvaluator real (con su ExpressionTypeChecker real), sin Mockito. Etapas de
 * events_binding_runtime: d0 (corte a profundidad 32), a1 (solo enlaces activos con ese origen),
 * d1 (condición booleana), a2 (ruta, expresión y valor fijo), a3 (evento derivado publicado) y a4
 * (políticas SKIP, REPORT y DISABLE_BINDING).
 */
class EventBindingRuntimeRealChainIntegrationTests {

    private static final String LIB = ExpressionTestFixtures.METAMODEL_ID;

    private record Stack(JsonMapper mapper, EventModelApplicationService eventService, JsonExpressionModelRepository expressions,
            EventBindingRuntimeService runtime, InMemoryCanonicalEventBus bus, List<CanonicalEvent> received) { }

    private static Stack stack(Path temporary) {
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        StorageProperties storage = new StorageProperties(temporary.resolve("metamodels"), temporary.resolve("models"),
                temporary.resolve("notations"), temporary.resolve("validations"), temporary.resolve("quickfixes"),
                temporary.resolve("generators"), temporary.resolve("assets"), temporary.resolve("exports"), true);
        CanonicalMetamodelApplicationService metamodelService = new CanonicalMetamodelApplicationService(
                new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());
        metamodelService.create(ExpressionTestFixtures.metamodel());
        EventModelProperties eventProperties = new EventModelProperties();
        eventProperties.setDirectory(temporary.resolve("events"));
        JsonExpressionModelRepository expressions = new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper);
        EventModelApplicationService eventService = new EventModelApplicationService(
                new JsonEventDefinitionModelRepository(eventProperties, mapper),
                new JsonEventBindingModelRepository(eventProperties, mapper), expressions, metamodelService, new EventModelChecker());
        InMemoryCanonicalEventBus bus = new InMemoryCanonicalEventBus();
        ExpressionEvaluator evaluator = new ExpressionEvaluator(new ExpressionTypeChecker(new ExpressionDependencyAnalyzer()));
        EventBindingRuntimeService runtime = new EventBindingRuntimeService(bus, eventService, expressions, metamodelService, evaluator);
        List<CanonicalEvent> received = new ArrayList<>();
        bus.subscribe(event -> true, received::add);
        return new Stack(mapper, eventService, expressions, runtime, bus, received);
    }

    private static ExpressionDefinition booleanExpression(String id, String variable, BinaryExpressionOperator operator, Object literal, ExpressionValueKind literalKind) {
        return new ExpressionDefinition(id, id, "", "", ExpressionValueType.scalar(ExpressionValueKind.BOOLEAN),
                new BinaryExpressionNode("node:" + id.replace(':', '-'), operator, new VariableExpressionNode("node:var-" + id.replace(':', '-'), variable),
                        new LiteralExpressionNode("node:lit-" + id.replace(':', '-'), ExpressionValueType.scalar(literalKind), literal)), Map.of());
    }

    private static ExpressionDefinition upperName() {
        return new ExpressionDefinition("expression:upper", "Upper", "", "", ExpressionValueType.scalar(ExpressionValueKind.STRING),
                new FunctionExpressionNode("node:upper", "upper", List.of(new VariableExpressionNode("node:upper-var", "payload.name"))), Map.of());
    }

    private static EventFieldDefinition requiredField(String id) {
        return new EventFieldDefinition(id, id.substring("field:".length()), EventFieldType.STRING, true, false, "", "", "");
    }

    private static EventTypeDefinition type(String id, String name, List<EventFieldDefinition> fields) {
        return new EventTypeDefinition(id, name, "course.library", "", "BINDING", "1.0", fields, Set.of(),
                Set.of(EventSubjectKind.MODEL_ELEMENT), EventPersistencePolicy.TRANSIENT, false, false, true, Map.of());
    }

    private static EventDefinitionModel definitions(EventTypeDefinition... types) {
        return new EventDefinitionModel("1.0", "events", LIB, "course.library", List.of(types), Map.of());
    }

    private static EventBinding binding(String id, String source, String target, String condition, List<EventPayloadMapping> mappings,
            boolean enabled, EventBindingErrorPolicy policy) {
        return new EventBinding(id, id, source, target, condition, mappings, enabled, policy, Map.of());
    }

    private static EventBindingModel bindings(EventBinding... values) {
        return new EventBindingModel("1.0", "event-bindings", LIB, "events", LIB, List.of(values), Map.of());
    }

    private static CanonicalEvent event(String id, String type, Map<String, Object> payload) {
        return new CanonicalEvent(null, id, type, "1.0", Instant.now(), new EventSource("TEST", "", "", ""),
                new EventContext(EventContextKind.SYSTEM, "", "", LIB, "", "", ""), null, null, "", "", "", payload, Map.of());
    }

    private static List<String> derivedBindingIds(List<CanonicalEvent> received) {
        return received.stream().filter(e -> e.metadata().containsKey("hydra.binding.id")).map(e -> e.metadata().get("hydra.binding.id")).toList();
    }

    @Test
    @DisplayName("events_binding_runtime.d1/a1: la condicion BOOLEAN verdadera actua y la falsa no; un enlace desactivado y un origen sin enlace se ignoran")
    void aBooleanConditionActsOnlyWhenTrueAndDisabledOrUnboundSourcesAreIgnored(@TempDir Path temporary) {
        Stack s = stack(temporary);
        s.expressions().create(ExpressionTestFixtures.expressionModel(
                booleanExpression("expression:large", "payload.amount", BinaryExpressionOperator.GREATER_OR_EQUAL, 100, ExpressionValueKind.INTEGER)));
        s.eventService().create(LIB, definitions(type("course.library.flagged", "Flagged", List.of())), bindings(
                binding("binding:large", "core.model.saved", "course.library.flagged", "expression:large", List.of(), true, EventBindingErrorPolicy.REPORT),
                binding("binding:off", "core.model.exported", "course.library.flagged", "", List.of(), false, EventBindingErrorPolicy.REPORT)));
        try {
            s.bus().publish(event("event:big", "core.model.saved", Map.of("amount", 150)));
            assertThat(s.received()).extracting(CanonicalEvent::eventTypeId).containsExactly("core.model.saved", "course.library.flagged");
            assertThat(derivedBindingIds(s.received())).containsExactly("binding:large");

            s.received().clear();
            s.bus().publish(event("event:small", "core.model.saved", Map.of("amount", 50)));
            assertThat(s.received()).extracting(CanonicalEvent::eventTypeId).containsExactly("core.model.saved");

            s.received().clear();
            s.bus().publish(event("event:exported", "core.model.exported", Map.of("amount", 500)));
            s.bus().publish(event("event:created", "core.model.element-created", Map.of("amount", 500)));
            assertThat(s.received()).extracting(CanonicalEvent::eventTypeId).containsExactly("core.model.exported", "core.model.element-created");
        } finally {
            s.runtime().close();
        }
    }

    @Test
    @DisplayName("events_binding_runtime.a2/a3: copia por ruta, por expresion y por valor fijo, y el derivado se publica con causa, correlacion y profundidad")
    void threeKindsOfMappingFillTheDerivedPayloadAndTheDerivedEventIsPublishedWithItsCause(@TempDir Path temporary) {
        Stack s = stack(temporary);
        s.expressions().create(ExpressionTestFixtures.expressionModel(upperName()));
        s.eventService().create(LIB, definitions(type("course.library.copied", "Copied",
                List.of(requiredField("field:route"), requiredField("field:computed"), requiredField("field:fixed")))), bindings(
                binding("binding:copy", "core.model.exported", "course.library.copied", "", List.of(
                        new EventPayloadMapping("field:route", "payload.name", "", null),
                        new EventPayloadMapping("field:computed", "", "expression:upper", null),
                        new EventPayloadMapping("field:fixed", "", "", "constant")), true, EventBindingErrorPolicy.REPORT)));
        try {
            s.bus().publish(event("event:source", "core.model.exported", Map.of("name", "Alice")));

            assertThat(s.received()).extracting(CanonicalEvent::eventTypeId).containsExactly("core.model.exported", "course.library.copied");
            CanonicalEvent derived = s.received().get(1);
            assertThat(derived.payload()).isEqualTo(Map.of("field:route", "Alice", "field:computed", "ALICE", "field:fixed", "constant"));
            assertThat(derived.causationId()).isEqualTo("event:source");
            assertThat(derived.correlationId()).isEqualTo("event:source");
            assertThat(derived.metadata().get("hydra.binding.depth")).isEqualTo("1");
            assertThat(derived.metadata().get("hydra.binding.id")).isEqualTo("binding:copy");
            assertThat(derived.source().component()).isEqualTo("EVENT_BINDING");
            assertThat(derived.source().instanceId()).isEqualTo("binding:copy");
            assertThat(derived.context().metamodelId()).isEqualTo(LIB);
        } finally {
            s.runtime().close();
        }
    }

    @Test
    @DisplayName("events_binding_runtime.d0/a3: dos enlaces A->B y B->A se cortan exactamente a profundidad 32")
    void aCycleBetweenTwoBindingsIsCutExactlyAtDepthThirtyTwo(@TempDir Path temporary) {
        Stack s = stack(temporary);
        s.expressions().create(ExpressionTestFixtures.expressionModel(
                booleanExpression("expression:unused", "payload.x", BinaryExpressionOperator.EQUALS, true, ExpressionValueKind.BOOLEAN)));
        s.eventService().create(LIB, definitions(type("course.library.ping", "Ping", List.of()), type("course.library.pong", "Pong", List.of())),
                bindings(binding("binding:ping-pong", "course.library.ping", "course.library.pong", "", List.of(), true, EventBindingErrorPolicy.REPORT),
                        binding("binding:pong-ping", "course.library.pong", "course.library.ping", "", List.of(), true, EventBindingErrorPolicy.REPORT)));
        try {
            s.bus().publish(event("event:start", "course.library.ping", Map.of()));

            // original (profundidad 0) + 32 derivados (profundidades 1..32); el de profundidad 32 ya no deriva nada
            assertThat(s.received()).hasSize(33);
            for(int index = 0; index < 33; index++) {
                CanonicalEvent current = s.received().get(index);
                assertThat(current.eventTypeId()).isEqualTo(index % 2 == 0 ? "course.library.ping" : "course.library.pong");
                if(index > 0) {
                    assertThat(current.metadata().get("hydra.binding.depth")).isEqualTo(String.valueOf(index));
                    assertThat(current.causationId()).isEqualTo(s.received().get(index - 1).id());
                }
            }
            assertThat(s.received().get(31).metadata().get("hydra.binding.depth")).isEqualTo("31");
            assertThat(s.received().get(32).metadata().get("hydra.binding.depth")).isEqualTo("32");
        } finally {
            s.runtime().close();
        }
    }

    @Test
    @DisplayName("events_binding_runtime.a4: SKIP y REPORT siguen intentando, DISABLE_BINDING deja de actuar hasta refresh(scope) y REPORT avisa en el log")
    void errorPoliciesSkipReportAndDisableBehaveAsConfiguredAndRefreshReactivatesTheDisabledBinding(@TempDir Path temporary) {
        Stack s = stack(temporary);
        ExpressionModel broken = ExpressionTestFixtures.expressionModel(
                booleanExpression("expression:fragile", "payload.flag", BinaryExpressionOperator.EQUALS, true, ExpressionValueKind.BOOLEAN));
        StoredExpressionModel storedExpressions = s.expressions().create(broken);
        s.eventService().create(LIB, definitions(type("course.library.derived", "Derived", List.of())), bindings(
                binding("binding:skip", "core.model.saved", "course.library.derived", "expression:fragile", List.of(), true, EventBindingErrorPolicy.SKIP),
                binding("binding:report", "core.model.exported", "course.library.derived", "expression:fragile", List.of(), true, EventBindingErrorPolicy.REPORT),
                binding("binding:disable", "core.model.element-updated", "course.library.derived", "expression:fragile", List.of(), true,
                        EventBindingErrorPolicy.DISABLE_BINDING)));
        Logger logger = (Logger) LoggerFactory.getLogger(EventBindingRuntimeService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            // la expresion referencia payload.flag, que los eventos no traen: la evaluacion falla de verdad
            s.bus().publish(event("event:s1", "core.model.saved", Map.of()));
            s.bus().publish(event("event:r1", "core.model.exported", Map.of()));
            s.bus().publish(event("event:d1", "core.model.element-updated", Map.of()));
            assertThat(s.received()).extracting(CanonicalEvent::eventTypeId)
                    .containsExactly("core.model.saved", "core.model.exported", "core.model.element-updated");
            List<String> warnings = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
            assertThat(warnings).anyMatch(message -> message.contains("binding:report"));
            assertThat(warnings).anyMatch(message -> message.contains("binding:disable"));
            assertThat(warnings).noneMatch(message -> message.contains("binding:skip"));

            // se arregla el modelo de expresiones guardado: la expresion ahora es siempre verdadera
            s.expressions().replace("library", ExpressionTestFixtures.expressionModel(new ExpressionDefinition("expression:fragile", "Fragile", "", "",
                    ExpressionValueType.scalar(ExpressionValueKind.BOOLEAN),
                    new LiteralExpressionNode("node:fixed", ExpressionValueType.scalar(ExpressionValueKind.BOOLEAN), true), Map.of())),
                    storedExpressions.etag());
            s.received().clear();
            s.bus().publish(event("event:s2", "core.model.saved", Map.of()));
            s.bus().publish(event("event:r2", "core.model.exported", Map.of()));
            s.bus().publish(event("event:d2", "core.model.element-updated", Map.of()));
            assertThat(derivedBindingIds(s.received())).containsExactly("binding:skip", "binding:report");

            s.runtime().refresh(LIB);
            s.received().clear();
            s.bus().publish(event("event:d3", "core.model.element-updated", Map.of()));
            assertThat(derivedBindingIds(s.received())).containsExactly("binding:disable");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            s.runtime().close();
        }
    }
}
