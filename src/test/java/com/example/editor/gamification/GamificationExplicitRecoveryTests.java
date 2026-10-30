package com.example.editor.gamification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import com.example.editor.audit.AuditRequestContext;
import tools.jackson.databind.JsonNode;

/**
 * The explicit, fingerprinted quarantine that replaces the implicit quarantine of a read (D-GAMIF-QUARANTINE-GET). The oracles are the
 * files: which bytes are still in the store, which are in the quarantine folder (byte for byte), what the manifest says, and that the
 * normal operations (create, read, replace, delete) work again afterwards; the HTTP status alone is never taken as the result.
 */
class GamificationExplicitRecoveryTests {
    private static final String RECOVERY = GamificationStoredPairFixture.ROUTE + "/recovery";
    private static final String QUARANTINE = RECOVERY + "/quarantine";

    @Test
    void theInspectionIsReadOnlyAndFingerprintsTheExactStoredBytes(@TempDir Path temporary) throws Exception {
        GamificationStoredPairFixture fixture = GamificationStoredPairFixture.open(temporary);
        fixture.createValid();
        Files.writeString(fixture.bindingsFile(), "{ unreadable bindings");
        Map<String, String> before = fixture.snapshot();

        MockHttpServletResponse response = fixture.mvc.perform(get(RECOVERY)).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode report = json(fixture, response);
        assertThat(report.path("state").asString()).isEqualTo("CORRUPT");
        assertThat(report.path("definitions").path("status").asString()).isEqualTo("VALID");
        assertThat(report.path("definitions").path("etag").asString()).isEqualTo(GamificationStoredPairFixture.etag(fixture.definitionsFile()));
        assertThat(report.path("bindings").path("status").asString()).isEqualTo("MALFORMED");
        assertThat(report.path("bindings").path("etag").asString()).isEqualTo(GamificationStoredPairFixture.etag(fixture.bindingsFile()));
        assertThat(report.path("bindings").path("sizeBytes").asLong()).isEqualTo(Files.size(fixture.bindingsFile()));
        assertThat(report.path("readableDefinitions").path("name").asString()).as("the readable data can be recovered right away").isEqualTo("Gamification");
        assertThat(report.path("readableBindings").isNull()).isTrue();
        assertThat(response.getHeader("X-Gamification-Definitions-ETag")).isEqualTo(GamificationStoredPairFixture.etag(fixture.definitionsFile()));
        assertThat(response.getHeader("X-Gamification-Bindings-ETag")).isEqualTo(GamificationStoredPairFixture.etag(fixture.bindingsFile()));
        GamificationReadsLeaveStoredFilesUntouchedTests.assertNoServerPath(response.getContentAsString(), temporary);
        assertThat(fixture.snapshot()).isEqualTo(before);
        assertThat(fixture.quarantines()).doesNotExist();
    }

    @Test
    void everyCorruptOrIncompletePairIsQuarantinedExplicitlyAndTheNormalOperationsWorkAgain(@TempDir Path temporary) throws Exception {
        for(String damage : List.of("bindings-malformed", "definitions-malformed", "both-malformed", "bindings-missing", "definitions-missing")) {
            GamificationStoredPairFixture fixture = GamificationStoredPairFixture.open(temporary.resolve(damage));
            fixture.createValid();
            if(damage.equals("bindings-malformed") || damage.equals("both-malformed")) Files.writeString(fixture.bindingsFile(), "{ unreadable bindings");
            if(damage.equals("definitions-malformed") || damage.equals("both-malformed")) Files.writeString(fixture.definitionsFile(), "{ unreadable definitions");
            if(damage.equals("bindings-missing")) Files.delete(fixture.bindingsFile());
            if(damage.equals("definitions-missing")) Files.delete(fixture.definitionsFile());
            Map<Path, byte[]> stored = new LinkedHashMap<>();
            for(Path file : List.of(fixture.definitionsFile(), fixture.bindingsFile())) if(Files.exists(file)) stored.put(file, Files.readAllBytes(file));

            // before the recovery a create is refused and changes nothing: the corrupt pair is never mixed with a new document
            Map<String, String> untouched = fixture.snapshot();
            MockHttpServletResponse refused = fixture.mvc.perform(json(post(GamificationStoredPairFixture.ROUTE), fixture, "Fresh", "Fresh rule")).andReturn().getResponse();
            assertThat(refused.getStatus()).as(damage).isEqualTo(409);
            assertThat(json(fixture, refused).path("code").asString()).as(damage).isEqualTo("CORRUPT_CONFIGURATION");
            assertThat(fixture.snapshot()).as(damage).isEqualTo(untouched);

            JsonNode report = json(fixture, fixture.mvc.perform(get(RECOVERY)).andReturn().getResponse());
            assertThat(report.path("state").asString()).as(damage).isEqualTo(damage.endsWith("missing") ? "INCOMPLETE" : "CORRUPT");
            MockHttpServletResponse quarantined;
            try(var scope = AuditRequestContext.begin("corr-" + damage)) {
                quarantined = fixture.mvc.perform(withEtags(post(QUARANTINE), report)).andReturn().getResponse();
            }
            assertThat(quarantined.getStatus()).as(damage).isEqualTo(200);
            JsonNode result = json(fixture, quarantined);
            assertThat(result.path("replayed").asBoolean()).as(damage).isFalse();
            JsonNode manifest = result.path("quarantine");
            assertThat(manifest.path("status").asString()).as(damage).isEqualTo("COMPLETE");
            assertThat(manifest.path("correlationId").asString()).as(damage).isEqualTo("corr-" + damage);
            assertThat(manifest.path("documents")).as(damage).hasSize(stored.size()).allSatisfy(document -> assertThat(document.path("outcome").asString()).isEqualTo("MOVED"));
            Path folder = fixture.quarantines().resolve(GamificationStoredPairFixture.ID).resolve(manifest.path("quarantineId").asString());
            for(Map.Entry<Path, byte[]> original : stored.entrySet()) {
                assertThat(original.getKey()).as(damage + ": moved out of the active store").doesNotExist();
                assertThat(folder.resolve(original.getKey().getFileName())).as(damage + ": kept byte for byte").hasBinaryContent(original.getValue());
            }
            assertThat(fixture.mapper.readTree(Files.readAllBytes(folder.resolve("quarantine.json"))).path("status").asString()).isEqualTo("COMPLETE");
            if(damage.equals("bindings-malformed") || damage.equals("bindings-missing"))
                assertThat(result.path("recoveredDefinitions").path("name").asString()).as(damage + ": the valid document is returned").isEqualTo("Gamification");
            if(damage.equals("definitions-malformed") || damage.equals("definitions-missing"))
                assertThat(result.path("recoveredBindings").path("rules").get(0).path("name").asString()).as(damage).isEqualTo("Rule");
            GamificationReadsLeaveStoredFilesUntouchedTests.assertNoServerPath(quarantined.getContentAsString(), temporary);

            // the normal operations work again: absent -> create -> read -> replace -> delete
            assertThat(fixture.mvc.perform(get(GamificationStoredPairFixture.ROUTE)).andReturn().getResponse().getStatus()).as(damage).isEqualTo(404);
            MockHttpServletResponse created = fixture.mvc.perform(json(post(GamificationStoredPairFixture.ROUTE), fixture, "Recreated", "Recreated rule")).andReturn().getResponse();
            assertThat(created.getStatus()).as(damage).isEqualTo(201);
            MockHttpServletResponse loaded = fixture.mvc.perform(get(GamificationStoredPairFixture.ROUTE)).andReturn().getResponse();
            assertThat(loaded.getStatus()).as(damage).isEqualTo(200);
            assertThat(json(fixture, loaded).path("definitions").path("name").asString()).isEqualTo("Recreated");
            MockHttpServletResponse replaced = fixture.mvc.perform(json(put(GamificationStoredPairFixture.ROUTE), fixture, "Replaced", "Replaced rule")
                    .header("If-Match-Definitions", created.getHeader("X-Gamification-Definitions-ETag"))
                    .header("If-Match-Bindings", created.getHeader("X-Gamification-Bindings-ETag"))).andReturn().getResponse();
            assertThat(replaced.getStatus()).as(damage).isEqualTo(200);
            assertThat(fixture.mvc.perform(delete(GamificationStoredPairFixture.ROUTE)
                    .header("If-Match-Definitions", replaced.getHeader("X-Gamification-Definitions-ETag"))
                    .header("If-Match-Bindings", replaced.getHeader("X-Gamification-Bindings-ETag"))).andReturn().getResponse().getStatus()).as(damage).isEqualTo(204);
            assertThat(folder.resolve("quarantine.json")).as(damage + ": the evidence survives the new configuration").exists();
        }
    }

    @Test
    void anOutdatedOrMissingFingerprintOrAHealthyPairNeverMovesAnything(@TempDir Path temporary) throws Exception {
        GamificationStoredPairFixture fixture = GamificationStoredPairFixture.open(temporary);
        fixture.createValid();
        String healthyDefinitions = GamificationStoredPairFixture.etag(fixture.definitionsFile());
        String healthyBindings = GamificationStoredPairFixture.etag(fixture.bindingsFile());
        Map<String, String> healthy = fixture.snapshot();
        MockHttpServletResponse refused = fixture.mvc.perform(post(QUARANTINE).header("If-Match-Definitions", healthyDefinitions).header("If-Match-Bindings", healthyBindings)).andReturn().getResponse();
        assertThat(refused.getStatus()).as("a healthy configuration is never quarantined").isEqualTo(409);
        assertThat(json(fixture, refused).path("code").asString()).isEqualTo("CONFLICT");
        assertThat(fixture.snapshot()).isEqualTo(healthy);

        Files.writeString(fixture.bindingsFile(), "{ unreadable bindings");
        JsonNode report = json(fixture, fixture.mvc.perform(get(RECOVERY)).andReturn().getResponse());
        String definitions = report.path("definitions").path("etag").asString();
        String bindings = report.path("bindings").path("etag").asString();
        Files.writeString(fixture.bindingsFile(), "{ unreadable bindings, changed after the inspection");
        Map<String, String> changed = fixture.snapshot();
        List<MockHttpServletRequestBuilder> attempts = List.of(
                post(QUARANTINE).header("If-Match-Definitions", definitions).header("If-Match-Bindings", bindings),
                post(QUARANTINE).header("If-Match-Definitions", definitions),
                post(QUARANTINE).header("If-Match-Definitions", "*").header("If-Match-Bindings", "*"),
                post(QUARANTINE).header("If-Match-Definitions", "\"0000\"").header("If-Match-Bindings", GamificationStoredPairFixture.etag(fixture.bindingsFile())));
        for(MockHttpServletRequestBuilder attempt : attempts) {
            MockHttpServletResponse response = fixture.mvc.perform(attempt).andReturn().getResponse();
            assertThat(response.getStatus()).isEqualTo(412);
            assertThat(json(fixture, response).path("code").asString()).isEqualTo("PRECONDITION_FAILED");
            assertThat(fixture.snapshot()).as("a failed precondition moves nothing").isEqualTo(changed);
        }
        Files.delete(fixture.bindingsFile());
        MockHttpServletResponse vanished = fixture.mvc.perform(post(QUARANTINE).header("If-Match-Definitions", definitions)
                .header("If-Match-Bindings", GamificationStoredPairFixture.etag(fixture.definitionsFile()))).andReturn().getResponse();
        assertThat(vanished.getStatus()).as("a fingerprint for a document that is no longer stored is outdated").isEqualTo(412);
        assertThat(fixture.quarantines().resolve(GamificationStoredPairFixture.ID)).doesNotExist();
    }

    @Test
    void repeatingTheQuarantineSequentiallyOrConcurrentlyIsIdempotent(@TempDir Path temporary) throws Exception {
        GamificationStoredPairFixture fixture = GamificationStoredPairFixture.open(temporary);
        fixture.createValid();
        Files.writeString(fixture.definitionsFile(), "{ unreadable definitions");
        JsonNode report = json(fixture, fixture.mvc.perform(get(RECOVERY)).andReturn().getResponse());
        String definitions = report.path("definitions").path("etag").asString();
        String bindings = report.path("bindings").path("etag").asString();
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<GamificationQuarantineResult> results = new ArrayList<>();
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<GamificationQuarantineResult>> calls = new ArrayList<>();
            for(int caller = 0; caller < 6; caller++) calls.add(pool.submit(() -> { start.await(); return fixture.service.quarantine(GamificationStoredPairFixture.ID, definitions, bindings); }));
            start.countDown();
            for(Future<GamificationQuarantineResult> call : calls) results.add(call.get(30, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
        assertThat(results).extracting(result -> result.quarantine().quarantineId()).as("one quarantine, answered six times").containsOnly(results.get(0).quarantine().quarantineId());
        assertThat(results).filteredOn(result -> !result.replayed()).hasSize(1);
        try(var folders = Files.list(fixture.quarantines().resolve(GamificationStoredPairFixture.ID))) { assertThat(folders.toList()).hasSize(1); }
        GamificationQuarantineResult again = fixture.service.quarantine(GamificationStoredPairFixture.ID, definitions, bindings);
        assertThat(again.replayed()).isTrue();
        assertThat(again.quarantine()).isEqualTo(results.get(0).quarantine());
        assertThat(again.recoveredBindings().rules()).extracting(GamificationRule::name).containsExactly("Rule");
    }

    @Test
    void aCrashBetweenTheTwoMovesIsResumedByTheNextRequestEvenAfterARestart(@TempDir Path temporary) throws Exception {
        GamificationStoredPairFixture fixture = GamificationStoredPairFixture.open(temporary);
        fixture.createValid();
        Files.writeString(fixture.bindingsFile(), "{ unreadable bindings");
        byte[] definitionsBytes = Files.readAllBytes(fixture.definitionsFile());
        byte[] bindingsBytes = Files.readAllBytes(fixture.bindingsFile());
        String definitions = GamificationStoredPairFixture.etag(fixture.definitionsFile());
        String bindings = GamificationStoredPairFixture.etag(fixture.bindingsFile());
        fixture.recovery.steps(new GamificationCorruptDocumentRecovery.Steps() {
            @Override public void afterMove(String role) { throw new IllegalStateException("simulated crash after moving the " + role + " document"); }
        });
        try { fixture.service.quarantine(GamificationStoredPairFixture.ID, definitions, bindings); throw new AssertionError("the simulated crash did not happen"); }
        catch(IllegalStateException crash) { assertThat(crash).hasMessageContaining("simulated crash"); }
        fixture.recovery.steps(null);

        // the state a crash leaves: the first document already moved, the manifest still IN_PROGRESS and saying PENDING for it
        Path folder;
        try(var folders = Files.list(fixture.quarantines().resolve(GamificationStoredPairFixture.ID))) { folder = folders.findFirst().orElseThrow(); }
        assertThat(fixture.definitionsFile()).doesNotExist();
        assertThat(folder.resolve(fixture.definitionsFile().getFileName())).hasBinaryContent(definitionsBytes);
        assertThat(fixture.bindingsFile()).hasBinaryContent(bindingsBytes);
        JsonNode interrupted = fixture.mapper.readTree(Files.readAllBytes(folder.resolve("quarantine.json")));
        assertThat(interrupted.path("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(interrupted.path("documents")).allSatisfy(document -> assertThat(document.path("outcome").asString()).isEqualTo("PENDING"));
        assertThat(fixture.mvc.perform(get(GamificationStoredPairFixture.ROUTE)).andReturn().getResponse().getStatus()).as("half quarantined is incomplete").isEqualTo(409);

        GamificationStoredPairFixture restarted = fixture.restarted();
        JsonNode report = json(restarted, restarted.mvc.perform(get(RECOVERY)).andReturn().getResponse());
        assertThat(report.path("quarantines").get(0).path("status").asString()).isEqualTo("IN_PROGRESS");
        GamificationQuarantineResult resumed = restarted.service.quarantine(GamificationStoredPairFixture.ID, definitions, bindings);
        assertThat(resumed.replayed()).isTrue();
        assertThat(resumed.quarantine().status()).isEqualTo("COMPLETE");
        assertThat(resumed.quarantine().documents()).extracting(GamificationQuarantineRecord.Document::outcome).containsOnly("MOVED");
        assertThat(folder.resolve(fixture.definitionsFile().getFileName())).hasBinaryContent(definitionsBytes);
        assertThat(folder.resolve(fixture.bindingsFile().getFileName())).hasBinaryContent(bindingsBytes);
        assertThat(resumed.recoveredDefinitions().name()).isEqualTo("Gamification");
        assertThat(restarted.service.quarantine(GamificationStoredPairFixture.ID, definitions, bindings).quarantine()).isEqualTo(resumed.quarantine());
        assertThat(restarted.createValid().definitionEtag()).isNotBlank();
    }

    @Test
    void aDocumentThatDisappearsDuringTheQuarantineIsRecordedAndTheOtherIsKept(@TempDir Path temporary) throws Exception {
        GamificationStoredPairFixture fixture = GamificationStoredPairFixture.open(temporary);
        fixture.createValid();
        Files.writeString(fixture.bindingsFile(), "{ unreadable bindings");
        byte[] definitionsBytes = Files.readAllBytes(fixture.definitionsFile());
        fixture.recovery.steps(new GamificationCorruptDocumentRecovery.Steps() {
            @Override public void beforeMove(String role, Path source) throws IOException { if(role.equals("bindings")) Files.delete(source); }
        });
        GamificationQuarantineResult result = fixture.service.quarantine(GamificationStoredPairFixture.ID,
                GamificationStoredPairFixture.etag(fixture.definitionsFile()), GamificationStoredPairFixture.etag(fixture.bindingsFile()));
        assertThat(result.quarantine().status()).isEqualTo("COMPLETE");
        assertThat(result.quarantine().documents()).extracting(GamificationQuarantineRecord.Document::role, GamificationQuarantineRecord.Document::outcome)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("definitions", "MOVED"), org.assertj.core.groups.Tuple.tuple("bindings", "VANISHED"));
        assertThat(fixture.quarantines().resolve(GamificationStoredPairFixture.ID).resolve(result.quarantine().quarantineId())
                .resolve(fixture.definitionsFile().getFileName())).hasBinaryContent(definitionsBytes);
        assertThat(result.recoveredDefinitions().name()).isEqualTo("Gamification");
        assertThat(fixture.createValid().bindingEtag()).isNotBlank();
    }

    @Test
    void aMoveThatWindowsKeepsDenyingIsAStorageErrorAndTheRetryCompletesWithoutLosingData(@TempDir Path temporary) throws Exception {
        Assumptions.assumeTrue(GamificationStoredPairFixture.windows(), "a file held open without FILE_SHARE_DELETE cannot be renamed only on Windows");
        GamificationStoredPairFixture fixture = GamificationStoredPairFixture.open(temporary);
        fixture.createValid();
        Files.writeString(fixture.bindingsFile(), "{ unreadable bindings");
        byte[] definitionsBytes = Files.readAllBytes(fixture.definitionsFile());
        byte[] bindingsBytes = Files.readAllBytes(fixture.bindingsFile());
        String definitions = GamificationStoredPairFixture.etag(fixture.definitionsFile());
        String bindings = GamificationStoredPairFixture.etag(fixture.bindingsFile());
        MockHttpServletResponse denied;
        try(FileChannel held = GamificationStoredPairFixture.holdAgainstRename(fixture.bindingsFile())) {
            denied = fixture.mvc.perform(post(QUARANTINE).header("If-Match-Definitions", definitions).header("If-Match-Bindings", bindings)).andReturn().getResponse();
        }
        assertThat(denied.getStatus()).isEqualTo(500);
        assertThat(json(fixture, denied).path("code").asString()).isEqualTo("STORAGE_ERROR");
        GamificationReadsLeaveStoredFilesUntouchedTests.assertNoServerDetail(denied.getContentAsString(), temporary);
        Path folder;
        try(var folders = Files.list(fixture.quarantines().resolve(GamificationStoredPairFixture.ID))) { folder = folders.findFirst().orElseThrow(); }
        assertThat(fixture.bindingsFile()).as("the document that could not be moved is still stored").hasBinaryContent(bindingsBytes);
        assertThat(folder.resolve(fixture.definitionsFile().getFileName())).as("the one already moved is kept").hasBinaryContent(definitionsBytes);

        GamificationQuarantineResult retried = fixture.service.quarantine(GamificationStoredPairFixture.ID, definitions, bindings);
        assertThat(retried.replayed()).isTrue();
        assertThat(retried.quarantine().status()).isEqualTo("COMPLETE");
        assertThat(folder.resolve(fixture.bindingsFile().getFileName())).hasBinaryContent(bindingsBytes);
        assertThat(fixture.bindingsFile()).doesNotExist();
    }

    @Test
    void aRestartAnswersTheSameAndTheRecoveryOnlyEverSeesItsOwnMetamodel(@TempDir Path temporary) throws Exception {
        GamificationStoredPairFixture fixture = GamificationStoredPairFixture.open(temporary);
        fixture.createValid();
        Files.writeString(fixture.bindingsFile(), "{ unreadable bindings");
        // a quarantine folder left by the implicit quarantine of an earlier HYDRA (no manifest) for this metamodel, and one of another metamodel
        Path legacy = fixture.quarantines().resolve(GamificationStoredPairFixture.ID).resolve("2026-10-29T10-00-00Z");
        Files.createDirectories(legacy);
        Files.writeString(legacy.resolve(GamificationStoredPairFixture.ID + ".gamification-bindings.json"), "{ older unreadable bindings");
        Path foreign = fixture.quarantines().resolve("other-metamodel").resolve("2026-10-29T11-00-00Z");
        Files.createDirectories(foreign);
        Files.writeString(foreign.resolve("other-metamodel.gamification-definitions.json"), "{\"secret\":\"of another metamodel\"}");

        MockHttpServletResponse first = fixture.mvc.perform(get(GamificationStoredPairFixture.ROUTE)).andReturn().getResponse();
        JsonNode firstReport = json(fixture, fixture.mvc.perform(get(RECOVERY)).andReturn().getResponse());
        GamificationStoredPairFixture restarted = fixture.restarted();
        MockHttpServletResponse second = restarted.mvc.perform(get(GamificationStoredPairFixture.ROUTE)).andReturn().getResponse();
        JsonNode secondReport = json(restarted, restarted.mvc.perform(get(RECOVERY)).andReturn().getResponse());
        assertThat(second.getStatus()).isEqualTo(first.getStatus()).isEqualTo(409);
        assertThat(json(restarted, second).path("message")).isEqualTo(json(fixture, first).path("message"));
        assertThat(secondReport).isEqualTo(firstReport);

        assertThat(secondReport.path("quarantines")).singleElement().satisfies(record -> {
            assertThat(record.path("status").asString()).isEqualTo("LEGACY");
            assertThat(record.path("documents").get(0).path("etag").asString()).isEqualTo(GamificationStoredPairFixture.etag(legacy.resolve(GamificationStoredPairFixture.ID + ".gamification-bindings.json")));
        });
        assertThat(secondReport.toString()).doesNotContain("other-metamodel").doesNotContain("secret");
        assertThat(restarted.mvc.perform(get("/api/canonical-metamodels/other-metamodel/gamification/recovery")).andReturn().getResponse().getStatus())
                .as("the recovery of a metamodel that does not exist in this workspace is refused like any other operation").isEqualTo(404);
        assertThat(restarted.recovery.quarantined(GamificationStoredPairFixture.ID, "../other-metamodel/2026-10-29T11-00-00Z", "other-metamodel.gamification-definitions.json"))
                .as("a quarantine id cannot reach the quarantines of another metamodel").isEmpty();
    }

    private static MockHttpServletRequestBuilder withEtags(MockHttpServletRequestBuilder request, JsonNode report) {
        String definitions = report.path("definitions").path("etag").asString();
        String bindings = report.path("bindings").path("etag").asString();
        if(!definitions.isEmpty()) request.header("If-Match-Definitions", definitions);
        if(!bindings.isEmpty()) request.header("If-Match-Bindings", bindings);
        return request;
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, GamificationStoredPairFixture fixture, String name, String ruleName) {
        return request.contentType(MediaType.APPLICATION_JSON).content(fixture.mapper.writeValueAsString(Map.of(
                "definitions", GamificationStoredPairFixture.definitions(name), "bindings", GamificationStoredPairFixture.bindings(ruleName))));
    }

    private static JsonNode json(GamificationStoredPairFixture fixture, MockHttpServletResponse response) throws Exception {
        return fixture.mapper.readTree(response.getContentAsString());
    }
}
