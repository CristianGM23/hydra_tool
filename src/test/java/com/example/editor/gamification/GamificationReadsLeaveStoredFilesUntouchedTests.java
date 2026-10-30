package com.example.editor.gamification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletResponse;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.editor.audit.AuditRequestContext;
import com.example.editor.definition.EditorDefinitionApplicationService;
import com.example.editor.event.EventModelApplicationService;
import com.example.editor.event.flow.EventFlowApplicationService;
import com.example.editor.integration.IntegrationProfileApplicationService;
import com.example.editor.validation.validation.ValidationModelApplicationService;
import com.example.editor.workbench.capability.HydraCapabilityReadiness;
import com.example.editor.workbench.capability.HydraCapabilityReadinessService;
import tools.jackson.databind.JsonNode;

/**
 * D-GAMIF-QUARANTINE-GET (decision of the user, 30/10): a read of the gamification configuration never modifies or moves the stored
 * documents and never quarantines the readable document of a pair because the other one is malformed. A corrupt configuration is a 409
 * CORRUPT_CONFIGURATION with a public message (which document, no server path) and a WARN in the log under the correlation id; 404 only
 * when nothing is stored; 500 for an I/O or permission failure. The oracle is the whole data directory, byte for byte and with its
 * modification times, before and after every read, not only the HTTP status.
 *
 * <p>Written to fail with the jar 15 (whose reads moved the whole pair into .invalid-recovery and answered 404) and to use only API that
 * the jar 15 already has, so the same class runs before and after the change.
 */
class GamificationReadsLeaveStoredFilesUntouchedTests {
    private static final String CORRELATION = "corr-gamification-read";

    @Test
    void aReadOfAMalformedPairAnswersCorruptConfigurationAndChangesNoFile(@TempDir Path temporary) throws Exception {
        for(String corrupted : List.of("bindings", "definitions", "both")) {
            GamificationStoredPairFixture fixture = GamificationStoredPairFixture.open(temporary.resolve(corrupted));
            fixture.createValid();
            if(!"definitions".equals(corrupted)) Files.writeString(fixture.bindingsFile(), "{ unreadable bindings");
            if(!"bindings".equals(corrupted)) Files.writeString(fixture.definitionsFile(), "{ unreadable definitions");
            Map<String, String> before = fixture.snapshot();
            ListAppender<ILoggingEvent> log = capture();
            try {
                MockHttpServletResponse response;
                try(var scope = AuditRequestContext.begin(CORRELATION)) { response = fixture.mvc.perform(get(GamificationStoredPairFixture.ROUTE)).andReturn().getResponse(); }
                assertThat(response.getStatus()).as(corrupted).isEqualTo(409);
                JsonNode body = fixture.mapper.readTree(response.getContentAsString());
                assertThat(body.path("code").asString()).as(corrupted).isEqualTo("CORRUPT_CONFIGURATION");
                assertThat(body.path("correlationId").asString()).as(corrupted).isEqualTo(CORRELATION);
                String message = body.path("message").asString();
                if(!"definitions".equals(corrupted)) assertThat(message).as(corrupted).contains("bindings are malformed");
                if(!"bindings".equals(corrupted)) assertThat(message).as(corrupted).contains("definitions are malformed");
                if("bindings".equals(corrupted)) assertThat(message).doesNotContain("definitions are");
                if("definitions".equals(corrupted)) assertThat(message).doesNotContain("bindings are");
                assertThat(message).as(corrupted).contains("left unchanged").contains("/gamification/recovery");
                assertNoServerDetail(response.getContentAsString(), temporary);
                assertThat(body.path("recoveryActions").toString()).contains("inspect-configuration-recovery");
                assertThat(log.list).as("the read is logged with its correlation id and cause").anySatisfy(event -> assertThat(event.getFormattedMessage())
                        .contains("hydra.gamification_corrupt_configuration").contains(CORRELATION).contains("Could not parse document"));
            } finally { release(log); }
            assertThat(fixture.snapshot()).as(corrupted + ": no stored file was moved, rewritten or touched").isEqualTo(before);
            assertThat(fixture.quarantines()).as(corrupted).doesNotExist();
        }
    }

    @Test
    void anIncompletePairIsACorruptConfigurationAndOnlyAnAbsentPairIsAnEmptyNotFound(@TempDir Path temporary) throws Exception {
        GamificationStoredPairFixture fixture = GamificationStoredPairFixture.open(temporary);
        fixture.createValid();
        Files.delete(fixture.bindingsFile());
        Map<String, String> before = fixture.snapshot();
        MockHttpServletResponse incomplete = fixture.mvc.perform(get(GamificationStoredPairFixture.ROUTE)).andReturn().getResponse();
        assertThat(incomplete.getStatus()).isEqualTo(409);
        assertThat(fixture.mapper.readTree(incomplete.getContentAsString()).path("message").asString()).contains("bindings are missing");
        assertThat(fixture.snapshot()).isEqualTo(before);

        Files.delete(fixture.definitionsFile());
        MockHttpServletResponse absent = fixture.mvc.perform(get(GamificationStoredPairFixture.ROUTE)).andReturn().getResponse();
        assertThat(absent.getStatus()).isEqualTo(404);
        assertThat(absent.getContentAsString()).as("the contract shared by the artefact controllers: an absence is an empty 404").isEmpty();
    }

    @Test
    void anUnreadableDocumentIsAStorageErrorWithoutDetailsAndChangesNoFile(@TempDir Path temporary) throws Exception {
        Assumptions.assumeTrue(GamificationStoredPairFixture.windows(), "exclusive file sharing is a Windows behaviour");
        GamificationStoredPairFixture fixture = GamificationStoredPairFixture.open(temporary);
        fixture.createValid();
        Map<String, String> before = fixture.snapshot();
        MockHttpServletResponse response;
        try(FileChannel held = GamificationStoredPairFixture.holdExclusively(fixture.bindingsFile())) {
            response = fixture.mvc.perform(get(GamificationStoredPairFixture.ROUTE)).andReturn().getResponse();
        }
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(fixture.mapper.readTree(response.getContentAsString()).path("code").asString()).isEqualTo("STORAGE_ERROR");
        assertNoServerDetail(response.getContentAsString(), temporary);
        assertThat(fixture.snapshot()).isEqualTo(before);
        assertThat(fixture.mvc.perform(get(GamificationStoredPairFixture.ROUTE)).andReturn().getResponse().getStatus())
                .as("once the other program releases the file the configuration is readable again").isEqualTo(200);
    }

    @Test
    void repeatedAndConcurrentReadsNeverMoveTheCorruptPair(@TempDir Path temporary) throws Exception {
        GamificationStoredPairFixture fixture = GamificationStoredPairFixture.open(temporary);
        fixture.createValid();
        Files.writeString(fixture.bindingsFile(), "{ unreadable bindings");
        Map<String, String> before = fixture.snapshot();
        for(int read = 0; read < 50; read++) assertThat(reason(fixture)).as("read " + read).isEqualTo("CORRUPT_CONFIGURATION");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            for(int round = 0; round < 2; round++) {
                CountDownLatch start = new CountDownLatch(1);
                List<Future<String>> reads = new ArrayList<>();
                for(int reader = 0; reader < 8; reader++) reads.add(pool.submit(() -> { start.await(); return reason(fixture); }));
                start.countDown();
                for(Future<String> read : reads) assertThat(read.get(30, TimeUnit.SECONDS)).isEqualTo("CORRUPT_CONFIGURATION");
            }
        } finally { pool.shutdownNow(); }
        assertThat(fixture.snapshot()).isEqualTo(before);
        assertThat(fixture.quarantines()).doesNotExist();
    }

    @Test
    void theCatalogAndHomeReadinessKeepTheValidConfigurationsAndNeverDiscloseAPath(@TempDir Path temporary) throws Exception {
        GamificationStoredPairFixture fixture = GamificationStoredPairFixture.open(temporary);
        fixture.createValid();
        fixture.definitionRepository.create(GamificationStoredPairFixture.definitions("other-metamodel", "Other"));
        fixture.bindingRepository.create(GamificationStoredPairFixture.bindings("other-metamodel", "Other rule"));
        Files.writeString(fixture.bindingsFile(), "{ unreadable bindings");
        Map<String, String> before = fixture.snapshot();

        GamificationCatalogResult catalog = fixture.service.listWithDiagnostics();
        assertThat(catalog.summaries()).extracting(GamificationSummary::metamodelId).containsExactly("other-metamodel");
        assertThat(catalog.diagnostics()).singleElement().asString().contains(GamificationStoredPairFixture.ID).contains("bindings are malformed")
                .contains("left unchanged");
        catalog.diagnostics().forEach(diagnostic -> assertNoServerDetail(diagnostic, temporary));

        HydraCapabilityReadiness readiness = readiness(fixture).summarize(GamificationStoredPairFixture.ID);
        assertThat(readiness.layers()).hasSize(7);
        assertThat(readiness.warnings()).singleElement().asString().contains("bindings are malformed");
        readiness.warnings().forEach(warning -> assertNoServerDetail(warning, temporary));
        assertThat(fixture.snapshot()).as("neither the catalog nor Home readiness moves or rewrites a document").isEqualTo(before);

        if(GamificationStoredPairFixture.windows()) {
            try(FileChannel held = GamificationStoredPairFixture.holdExclusively(fixture.gamification().resolve("other-metamodel.gamification-bindings.json"))) {
                GamificationCatalogResult unreadable = fixture.service.listWithDiagnostics();
                assertThat(unreadable.diagnostics()).hasSize(2).anySatisfy(diagnostic -> assertThat(diagnostic).contains("other-metamodel").contains("storage error"));
                unreadable.diagnostics().forEach(diagnostic -> assertNoServerDetail(diagnostic, temporary));
            }
            assertThat(fixture.snapshot()).isEqualTo(before);
        }
    }

    @Test
    void readsDuringConcurrentWritesNeverObserveAMixedOrHalfStoredPair(@TempDir Path temporary) throws Exception {
        GamificationStoredPairFixture fixture = GamificationStoredPairFixture.open(temporary);
        GamificationSaveResponse saved = fixture.createValid();
        AtomicBoolean writing = new AtomicBoolean(true);
        Set<String> observed = ConcurrentHashMap.newKeySet();
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            Future<?> writer = pool.submit(() -> {
                String definitionEtag = saved.definitionEtag(), bindingEtag = saved.bindingEtag();
                try {
                    for(int version = 0; version < 300; version++) {
                        String name = "v" + version;
                        GamificationSaveResponse next = fixture.service.replace(GamificationStoredPairFixture.ID, GamificationStoredPairFixture.definitions(name), definitionEtag,
                                GamificationStoredPairFixture.bindings(name), bindingEtag);
                        definitionEtag = next.definitionEtag();
                        bindingEtag = next.bindingEtag();
                    }
                    fixture.service.delete(GamificationStoredPairFixture.ID, definitionEtag, bindingEtag);
                    for(int cycle = 0; cycle < 100; cycle++) {
                        GamificationSaveResponse created = fixture.service.create(GamificationStoredPairFixture.ID, GamificationStoredPairFixture.definitions("c" + cycle),
                                GamificationStoredPairFixture.bindings("c" + cycle));
                        fixture.service.delete(GamificationStoredPairFixture.ID, created.definitionEtag(), created.bindingEtag());
                    }
                } finally { writing.set(false); }
                return null;
            });
            List<Future<?>> readers = new ArrayList<>();
            for(int reader = 0; reader < 2; reader++) readers.add(pool.submit(() -> {
                while(writing.get()) {
                    try {
                        GamificationLoadResult loaded = fixture.service.find(GamificationStoredPairFixture.ID);
                        String pair = loaded.definitions().name() + "|" + loaded.bindings().rules().get(0).name();
                        if(!loaded.definitions().name().equals(loaded.bindings().rules().get(0).name())
                                && !("Gamification".equals(loaded.definitions().name()) && "Rule".equals(loaded.bindings().rules().get(0).name())))
                            observed.add("MIXED " + pair);
                    } catch(GamificationApplicationException exception) {
                        if(!"NOT_FOUND".equals(exception.getReason().name())) observed.add(exception.getReason().name());
                    }
                }
                return null;
            }));
            writer.get(120, TimeUnit.SECONDS);
            for(Future<?> reader : readers) reader.get(30, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertThat(observed).as("a read sees one complete pair, or none, never half of a write").isEmpty();
        assertThat(fixture.quarantines()).doesNotExist();
    }

    private static String reason(GamificationStoredPairFixture fixture) {
        try { fixture.service.find(GamificationStoredPairFixture.ID); return "OK"; }
        catch(GamificationApplicationException exception) { return exception.getReason().name(); }
    }

    private static HydraCapabilityReadinessService readiness(GamificationStoredPairFixture fixture) {
        ValidationModelApplicationService validations = mock(ValidationModelApplicationService.class);
        EventModelApplicationService events = mock(EventModelApplicationService.class);
        EventFlowApplicationService flows = mock(EventFlowApplicationService.class);
        EditorDefinitionApplicationService editors = mock(EditorDefinitionApplicationService.class);
        IntegrationProfileApplicationService integrations = mock(IntegrationProfileApplicationService.class);
        when(validations.findAll()).thenReturn(List.of());
        when(events.list()).thenReturn(List.of());
        when(flows.list()).thenReturn(List.of());
        when(editors.list(GamificationStoredPairFixture.ID)).thenReturn(List.of());
        when(integrations.list(GamificationStoredPairFixture.ID)).thenReturn(List.of());
        return new HydraCapabilityReadinessService(fixture.metamodelService, validations, events, flows, fixture.service, editors, integrations);
    }

    /** A public text must not name the server directories, a stored file, an exception class or an exception message. */
    static void assertNoServerDetail(String text, Path temporary) {
        assertNoServerPath(text, temporary);
        assertThat(text).doesNotContain(".gamification-bindings.json").doesNotContain(".gamification-definitions.json");
    }

    /**
     * No server path and no exception detail. The recovery API names each document by its bare file name on purpose (so the user knows which
     * file is affected); errors and diagnostics do not even do that, see {@link #assertNoServerDetail}.
     */
    static void assertNoServerPath(String text, Path temporary) {
        String real;
        try { real = temporary.toRealPath().toString(); } catch(java.io.IOException exception) { real = temporary.toString(); }
        // the temporary directory may be printed in its short (8.3) or long form: any absolute Windows or temp path is a disclosure
        assertThat(text).doesNotContain(temporary.toString()).doesNotContain(real).doesNotContain(temporary.toString().replace('\\', '/'))
                .doesNotContainPattern("[A-Za-z]:[\\\\/]").doesNotContain("AppData").doesNotContain("/tmp/")
                .doesNotContain("Exception").doesNotContain("Could not parse").doesNotContain("Could not read document");
    }

    private static ListAppender<ILoggingEvent> capture() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(GamificationApplicationService.class)).addAppender(appender);
        return appender;
    }
    private static void release(ListAppender<ILoggingEvent> appender) {
        ((Logger) LoggerFactory.getLogger(GamificationApplicationService.class)).detachAppender(appender);
    }
}
