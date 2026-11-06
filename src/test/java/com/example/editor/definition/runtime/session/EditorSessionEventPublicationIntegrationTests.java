package com.example.editor.definition.runtime.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.definition.EditorCapability;
import com.example.editor.definition.EditorRuntimePolicy;
import com.example.editor.definition.runtime.session.persistence.JsonEditorModelSessionRepository;
import com.example.editor.event.CanonicalEvent;
import com.example.editor.event.EventContextKind;
import com.example.editor.event.EventSubjectKind;
import com.example.editor.event.bus.InMemoryCanonicalEventBus;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;

import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia real de la publicacion de eventos de una sesion de edicion: sesion, publicador, buffer
 * de eventos pendientes, repositorio JSON en disco y bus en memoria reales, con suscriptores reales
 * (uno de ellos falla). Etapas: events_publish_session.d1/a1/d2/a3/a4/a8 y
 * x_validate_fix_revalidate_reward.sv (los eventos retenidos solo salen tras guardar).
 */
class EditorSessionEventPublicationIntegrationTests {
    private static final String M = EditorSessionRealStackFixtures.METAMODEL;
    private static final String NAME = EditorSessionRealStackFixtures.NAME;
    private static final Set<EditorCapability> CAPABILITIES = Set.of(EditorCapability.MODEL_EDITING, EditorCapability.UNDO_REDO);

    @TempDir Path temporary;
    private JsonMapper mapper;
    private CanonicalMetamodelApplicationService metamodels;
    private InMemoryCanonicalEventBus bus;
    private final List<CanonicalEvent> seen = new ArrayList<>();
    private final AtomicInteger failingDeliveries = new AtomicInteger();

    @BeforeEach void setUp() {
        mapper = JsonMapper.builder().findAndAddModules().build();
        metamodels = EditorSessionRealStackFixtures.metamodels(temporary, mapper);
        bus = new InMemoryCanonicalEventBus();
        bus.subscribe(event -> true, seen::add);
        bus.subscribe(event -> true, event -> { failingDeliveries.incrementAndGet(); throw new IllegalStateException("subscriber failure"); });
    }

    @Test void disabledPolicyPublishesNothingAndEnabledPolicyPublishesTheFullyBuiltEvents() {
        Stack off = stack(properties(), "editor-off", policy(true, false));
        EditorSessionView view = off.service.create(M, "editor-off", request("session-off", "model-off"));
        view = off.createPerson("session-off", view.etag());
        off.rename("session-off", view, "Ada");
        // d1: con la publicacion desactivada la bus no recibe ningun evento.
        assertThat(seen).isEmpty();

        Stack on = stack(properties(), "editor-on", policy(true, true));
        EditorSessionView created = on.service.create(M, "editor-on", request("session-on", "model-on"));
        EditorSessionView withPerson = on.createPerson("session-on", created.etag());
        String personId = withPerson.nodes().get(0).elementId();
        EditorSessionView renamed = on.rename("session-on", withPerson, "Ada");

        assertThat(seen).extracting(CanonicalEvent::eventTypeId)
                .containsExactly("core.model.saved", "core.model.element-created", "core.model.element-updated");
        // a1: el evento se construye con origen, contexto, sujeto, transaccion y correlacion de la sesion.
        CanonicalEvent createdEvent = seen.get(1);
        assertThat(createdEvent.source().module()).isEqualTo("editor-runtime");
        assertThat(createdEvent.source().instanceId()).isEqualTo("session-on");
        assertThat(createdEvent.context().kind()).isEqualTo(EventContextKind.MODEL_EDITING);
        assertThat(createdEvent.context().metamodelId()).isEqualTo("people");
        assertThat(createdEvent.context().modelId()).isEqualTo("model-on");
        assertThat(createdEvent.context().editorId()).isEqualTo("editor-on");
        assertThat(createdEvent.context().sessionId()).isEqualTo("session-on");
        assertThat(createdEvent.subject().kind()).isEqualTo(EventSubjectKind.MODEL_ELEMENT);
        assertThat(createdEvent.subject().elementId()).isEqualTo(personId);
        assertThat(createdEvent.subject().typeId()).isEqualTo("class:Person");
        assertThat(createdEvent.transactionId()).startsWith("transaction:");
        assertThat(createdEvent.correlationId()).isEqualTo("correlation:session-on");
        assertThat(createdEvent.payload()).containsEntry("toolId", "tool:create-person").containsEntry("hydra.eventsEnabled", true);
        assertThat(createdEvent.metadata()).containsEntry("sessionRevision", Long.toString(withPerson.session().revision()));
        assertThat(seen.get(2).metadata()).containsEntry("sessionRevision", Long.toString(renamed.session().revision()));
        assertThat(seen.get(0).subject().kind()).isEqualTo(EventSubjectKind.MODEL);
        // Con autoguardado los eventos salen de inmediato (a1/d2) y el buffer queda vacio.
        assertThat(on.buffer.status("session-on").inMemoryEvents()).isZero();
    }

    @Test void withoutAutosaveEventsStayRetainedUntilSaveThenReachEverySubscriberOnceAndInOrder() throws IOException {
        Stack manual = stack(properties(), "editor-manual", policy(false, true));
        EditorSessionView view = manual.service.create(M, "editor-manual", request("session-manual", "model-manual"));
        assertThat(seen).extracting(CanonicalEvent::eventTypeId).containsExactly("core.model.saved");
        EditorSessionView withPerson = manual.createPerson("session-manual", view.etag());
        String personId = withPerson.nodes().get(0).elementId();
        EditorSessionView renamed = manual.rename("session-manual", withPerson, "Ada");

        // d2: sin autoguardado y con copia de trabajo los eventos se retienen; el disco aun no tiene el elemento.
        assertThat(seen).hasSize(1);
        assertThat(manual.buffer.status("session-manual").inMemoryEvents()).isEqualTo(2);
        Path sessionFile = temporary.resolve("sessions").resolve("session-manual.editor-session.json");
        assertThat(Files.readString(sessionFile)).doesNotContain(personId);

        manual.service.save(M, "editor-manual", "session-manual", renamed.etag());

        // a3: tras guardar con exito salen los eventos retenidos, en orden, y el modelo ya esta en disco.
        assertThat(Files.readString(sessionFile)).contains(personId).contains("Ada");
        assertThat(seen).extracting(CanonicalEvent::eventTypeId)
                .containsExactly("core.model.saved", "core.model.element-created", "core.model.element-updated");
        assertThat(manual.buffer.status("session-manual").inMemoryEvents()).isZero();
        // a4: el canal entrega cada evento a todos los suscriptores; el suscriptor que falla no impide al otro
        // recibirlos y no se reintenta (3 eventos, 3 intentos).
        assertThat(failingDeliveries.get()).isEqualTo(3);
        manual.service.save(M, "editor-manual", "session-manual", manual.service.find(M, "editor-manual", "session-manual", "").etag());
        assertThat(seen).hasSize(3);
    }

    @Test void discardingTheWorkingCopyPublishesNothingAndPersistsNothing() throws IOException {
        Stack manual = stack(properties(), "editor-manual", policy(false, true));
        EditorSessionView view = manual.service.create(M, "editor-manual", request("session-discard", "model-discard"));
        EditorSessionView withPerson = manual.createPerson("session-discard", view.etag());
        String personId = withPerson.nodes().get(0).elementId();
        assertThat(manual.buffer.status("session-discard").inMemoryEvents()).isEqualTo(1);

        EditorSessionView discarded = manual.service.discardWorkingCopy(M, "editor-manual", "session-discard");

        assertThat(discarded.session().model().elements()).isEmpty();
        assertThat(seen).extracting(CanonicalEvent::eventTypeId).containsExactly("core.model.saved");
        assertThat(manual.buffer.status("session-discard").inMemoryEvents()).isZero();
        assertThat(Files.readString(temporary.resolve("sessions").resolve("session-discard.editor-session.json"))).doesNotContain(personId);
    }

    @Test void pendingEventsSpillToDiskBeyondTheMemoryLimitAndAreDeliveredInOrderOnSave() throws IOException {
        EditorSessionProperties properties = properties();
        properties.setMaximumPendingEventsInMemory(1);
        Stack manual = stack(properties, "editor-manual", policy(false, true));
        EditorSessionView view = manual.service.create(M, "editor-manual", request("session-spill", "model-spill"));
        EditorSessionView withPerson = manual.createPerson("session-spill", view.etag());
        EditorSessionView renamed = manual.rename("session-spill", withPerson, "Ada");

        // a8: el primer evento se queda en memoria y el segundo se vuelca a un fichero JSONL en disco.
        EditorSessionPendingEventBuffer.PendingEventBufferStatus status = manual.buffer.status("session-spill");
        assertThat(status.inMemoryEvents()).isEqualTo(1);
        assertThat(status.spooling()).isTrue();
        assertThat(status.spooledBytes()).isPositive();
        try (Stream<Path> files = Files.list(temporary.resolve("spool"))) {
            assertThat(files.filter(path -> path.getFileName().toString().endsWith(".jsonl")).count()).isEqualTo(1L);
        }

        manual.service.save(M, "editor-manual", "session-spill", renamed.etag());

        assertThat(seen).extracting(CanonicalEvent::eventTypeId)
                .containsExactly("core.model.saved", "core.model.element-created", "core.model.element-updated");
        try (Stream<Path> files = Files.list(temporary.resolve("spool"))) {
            assertThat(files.filter(path -> path.getFileName().toString().endsWith(".jsonl")).count()).isZero();
        }
    }

    @Test void aSecondSessionWithPendingEventsIsRefusedAsAConflictWhenTheBufferIsFull() {
        EditorSessionProperties properties = properties();
        properties.setMaximumPendingEventSessions(1);
        Stack manual = stack(properties, "editor-manual", policy(false, true));
        EditorSessionView first = manual.service.create(M, "editor-manual", request("session-one", "model-one"));
        EditorSessionView second = manual.service.create(M, "editor-manual", request("session-two", "model-two"));
        manual.createPerson("session-one", first.etag());

        // a8: con el buffer lleno, retener eventos de otra sesion es un conflicto.
        assertThatThrownBy(() -> manual.createPerson("session-two", second.etag()))
                .isInstanceOfSatisfying(EditorSessionApplicationException.class,
                        exception -> assertThat(exception.getReason()).isEqualTo(EditorSessionApplicationException.Reason.CONFLICT));
    }

    private EditorSessionProperties properties() {
        EditorSessionProperties properties = new EditorSessionProperties();
        properties.setDirectory(temporary.resolve("sessions"));
        properties.setPendingEventSpoolDirectory(temporary.resolve("spool"));
        return properties;
    }

    private static EditorRuntimePolicy policy(boolean autosave, boolean publishEvents) {
        return new EditorRuntimePolicy(Set.of(), autosave, true, publishEvents, false, false, 2, 30_000L);
    }

    private static EditorSessionCreateRequest request(String sessionId, String modelId) {
        return new EditorSessionCreateRequest(sessionId, modelId, "People", "representation:main", "", Map.of());
    }

    private Stack stack(EditorSessionProperties properties, String editorId, EditorRuntimePolicy policy) {
        EditorSessionPendingEventBuffer buffer = new EditorSessionPendingEventBuffer(properties, mapper);
        EditorModelSessionService service = new EditorModelSessionService(
                EditorSessionRealStackFixtures.runtimes(EditorSessionRealStackFixtures.editor(editorId, policy, CAPABILITIES)),
                metamodels, new JsonEditorModelSessionRepository(properties, mapper), null, properties, null, null, null, null,
                new EditorSessionEventPublisher(bus), null, buffer, null, null, null);
        return new Stack(service, buffer, editorId);
    }

    private record Stack(EditorModelSessionService service, EditorSessionPendingEventBuffer buffer, String editorId) {
        EditorSessionView createPerson(String sessionId, String etag) {
            return service.createNode(M, editorId, sessionId, "tool:create-person",
                    new EditorNodeCreationRequest("representation:main", "", "", Map.of()), etag);
        }
        EditorSessionView rename(String sessionId, EditorSessionView view, String name) {
            return service.updateElement(M, editorId, sessionId, view.nodes().get(0).elementId(),
                    new EditorElementUpdateRequest("representation:main", "form:person", Map.of(NAME, name)), view.etag());
        }
    }
}
