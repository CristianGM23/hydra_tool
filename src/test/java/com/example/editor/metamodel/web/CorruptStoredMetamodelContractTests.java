package com.example.editor.metamodel.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.registry.MetamodelRegistryService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * D-MM-CORRUPT-CONTRATO (decision of the user, 31/10, alternatives A and B combined): a stored canonical metamodel whose bytes are not the
 * metamodel of its file is a corrupt configuration. Every read that needs it answers 409 CORRUPT_CONFIGURATION with a public message (which
 * metamodel, no server path) and the cause goes to the server log under the correlation id; 500 STORAGE_ERROR stays for a real I/O failure.
 * The listing keeps its JSON array with the valid metamodels and names the omitted ones only in the headers X-Hydra-Omitted-Count and
 * X-Hydra-Omitted-Items. The runtime registry reports it instead of failing: HYDRA starts (DEF-MM-CORRUPT-STARTUP-FAIL) and a write of another
 * metamodel is answered with its real outcome (DEF-MM-CORRUPT-WRITE-REPORTED-AS-FAILED). No read moves, rewrites or deletes a stored file.
 *
 * <p>Full Spring context and MockMvc on real stores in a temporary directory; the damage is written into the files. Written to fail with the
 * classes of the jar 17 (every case answered 500, the registry threw, a valid temporary copy was deleted by a read) and to use only API that
 * the jar 17 already has, so the same class runs before and after the change.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CorruptStoredMetamodelContractTests {
    private static final String UNREADABLE = "{ unreadable document written by the test";
    private static final String OMITTED_COUNT = "X-Hydra-Omitted-Count";
    private static final String OMITTED_ITEMS = "X-Hydra-Omitted-Items";

    @TempDir
    static Path root;

    @DynamicPropertySource
    static void directories(DynamicPropertyRegistry registry) {
        MetamodelHttpTestEnvironment.register(registry, root);
    }

    @Autowired private MockMvc mvc;
    @Autowired private CanonicalMetamodelApplicationService metamodels;
    @Autowired private MetamodelRegistryService registry;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final Map<Path, byte[]> originals = new LinkedHashMap<>();
    private final List<Path> created = new ArrayList<>();

    @Test
    void aCorruptStoredMetamodelIsAConflictWithAPublicMessageInEveryReadThatNeedsIt() throws Exception {
        metamodel("corrupt-a");
        metamodel("valid-b");
        Map<String, byte[]> variants = new LinkedHashMap<>();
        variants.put("unreadable JSON", UNREADABLE.getBytes(StandardCharsets.UTF_8));
        variants.put("JSON that is not a metamodel", "{\"schemaVersion\":\"2.1\",\"id\":\"corrupt-a\",\"classes\":\"not-a-list\"}".getBytes(StandardCharsets.UTF_8));
        variants.put("the metamodel of another id", Files.readAllBytes(file("valid-b")));
        for (Map.Entry<String, byte[]> variant : variants.entrySet()) {
            damage("corrupt-a", variant.getValue());
            registry.initialize();
            Map<String, String> before = snapshot();
            ListAppender<ILoggingEvent> log = capture();
            try {
                Map<String, RequestBuilder> reads = new LinkedHashMap<>();
                reads.put("metamodel", get("/api/canonical-metamodels/corrupt-a"));
                reads.put("validate", post("/api/canonical-metamodels/corrupt-a/validate"));
                reads.put("editors", get("/api/canonical-metamodels/corrupt-a/editors"));
                reads.put("Home readiness", get("/api/hydra/capabilities/metamodel/corrupt-a"));
                reads.put("Ecore projection", post("/api/canonical-metamodels/corrupt-a/projections/ecore"));
                reads.put("runtime metamodel", get("/api/metamodels/corrupt-a"));
                for (Map.Entry<String, RequestBuilder> read : reads.entrySet()) {
                    String label = variant.getKey() + " -> " + read.getKey();
                    MockHttpServletResponse response = mvc.perform(read.getValue()).andReturn().getResponse();
                    assertThat(response.getStatus()).as(label).isEqualTo(409);
                    JsonNode body = mapper.readTree(response.getContentAsString());
                    assertThat(body.path("code").asString()).as(label).isEqualTo("CORRUPT_CONFIGURATION");
                    assertThat(body.path("message").asString()).as(label).contains("'corrupt-a'").contains("left unchanged");
                    assertNoServerDetail(response.getContentAsString(), label);
                }
                String correlation = mapper.readTree(mvc.perform(get("/api/canonical-metamodels/corrupt-a")).andReturn().getResponse().getContentAsString())
                        .path("correlationId").asString();
                assertThat(correlation).as("the 409 carries the correlation id of the request").isNotBlank();
                assertThat(log.list).as(variant.getKey() + ": the cause is in the server log under the correlation id").anySatisfy(event ->
                        assertThat(event.getFormattedMessage()).contains("hydra.metamodel_corrupt_configuration").contains("corrupt-a").contains(correlation));
            } finally {
                release(log);
            }
            assertThat(mvc.perform(get("/api/canonical-metamodels/valid-b")).andReturn().getResponse().getStatus()).as(variant.getKey()).isEqualTo(200);
            assertThat(mvc.perform(get("/api/hydra/capabilities/metamodel/valid-b")).andReturn().getResponse().getStatus()).as(variant.getKey()).isEqualTo(200);
            assertThat(snapshot()).as(variant.getKey() + ": no read moved, rewrote or deleted a stored file").isEqualTo(before);
            restore();
        }
    }

    @Test
    void theListingKeepsTheValidMetamodelsAndNamesTheOmittedOnesOnlyInHeaders() throws Exception {
        metamodel("list-a");
        metamodel("list-b");
        damage("list-a", UNREADABLE.getBytes(StandardCharsets.UTF_8));
        Path strayName = root.resolve("metamodels").resolve("stray name.metamodel.json");
        Files.write(strayName, Files.readAllBytes(file("list-b")));
        created.add(strayName);
        Map<String, String> before = snapshot();

        MockHttpServletResponse listing = mvc.perform(get("/api/canonical-metamodels")).andReturn().getResponse();

        assertThat(listing.getStatus()).isEqualTo(200);
        JsonNode body = mapper.readTree(listing.getContentAsString());
        assertThat(body.isArray()).as("the listing keeps its JSON array").isTrue();
        List<String> ids = new ArrayList<>();
        body.forEach(item -> ids.add(item.path("id").asString()));
        assertThat(ids).contains("list-b").doesNotContain("list-a");
        assertThat(listing.getHeader(OMITTED_COUNT)).isEqualTo("2");
        assertThat(listing.getHeader(OMITTED_ITEMS)).as("only valid ids are named; a file whose name is not an id is only counted").isEqualTo("list-a");
        assertThat(listing.getContentAsString()).doesNotContain("stray");
        assertNoServerDetail(listing.getContentAsString(), "listing");
        assertThat(snapshot()).isEqualTo(before);

        restore();
        MockHttpServletResponse healthy = mvc.perform(get("/api/canonical-metamodels")).andReturn().getResponse();
        assertThat(healthy.getHeader(OMITTED_COUNT)).as("without damage nothing is reported").isNull();
        assertThat(healthy.getHeader(OMITTED_ITEMS)).isNull();
    }

    @Test
    void writesOfOtherMetamodelsSucceedAndTheRuntimeRegistryReportsTheCorruptOne() throws Exception {
        metamodel("reg-a");
        damage("reg-a", UNREADABLE.getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse createdOther = mvc.perform(post("/api/canonical-metamodels").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(model("fresh-c")))).andReturn().getResponse();
        assertThat(createdOther.getStatus()).as("the write is answered with its real outcome").isEqualTo(201);
        MockHttpServletResponse deleted = mvc.perform(delete("/api/canonical-metamodels/fresh-c").header("If-Match", createdOther.getHeader("ETag")))
                .andReturn().getResponse();
        assertThat(deleted.getStatus()).isEqualTo(204);
        assertThat(file("fresh-c")).doesNotExist();

        assertThatCode(() -> registry.initialize()).as("HYDRA starts and rebuilds its runtime registry with a corrupt metamodel").doesNotThrowAnyException();
        MockHttpServletResponse statuses = mvc.perform(get("/api/metamodels/runtime-status")).andReturn().getResponse();
        assertThat(statuses.getStatus()).isEqualTo(200);
        JsonNode status = null;
        for (JsonNode item : mapper.readTree(statuses.getContentAsString())) if ("reg-a".equals(item.path("metamodelId").asString())) status = item;
        assertThat(status).as("the corrupt metamodel is reported in the runtime status").isNotNull();
        assertThat(status.path("projectionStatus").asString()).isEqualTo("FAILED");
        assertThat(status.path("runtimeAvailable").asBoolean()).isFalse();
        assertThat(status.path("message").asString()).contains("left unchanged");
        assertNoServerDetail(statuses.getContentAsString(), "runtime status");
        Throwable missing = catchThrowable(() -> registry.find("reg-a"));
        assertThat(missing).isNotNull();
        assertThat(((com.example.editor.metamodel.registry.MetamodelRegistryException) missing).getReason().name()).isEqualTo("CORRUPT_CONFIGURATION");

        // a project brought into the workspace with a corrupt metamodel: the reload reports it without a path and keeps the others
        List<String> warnings = registry.reloadAfterProjectToWorkspace();
        assertThat(warnings).anySatisfy(warning -> assertThat(warning).contains("reg-a"));
        warnings.forEach(warning -> assertNoServerDetail(warning, "reload warning"));
    }

    @Test
    void restoringTheStoredFileMakesTheMetamodelAvailableAgainWithoutARestart() throws Exception {
        metamodel("rep-a");
        damage("rep-a", UNREADABLE.getBytes(StandardCharsets.UTF_8));
        assertThat(mvc.perform(get("/api/canonical-metamodels/rep-a")).andReturn().getResponse().getStatus()).isEqualTo(409);

        restore();
        assertThat(mvc.perform(get("/api/canonical-metamodels/rep-a")).andReturn().getResponse().getStatus()).isEqualTo(200);
        MockHttpServletResponse listing = mvc.perform(get("/api/canonical-metamodels")).andReturn().getResponse();
        assertThat(listing.getContentAsString()).contains("\"rep-a\"");
        assertThat(listing.getHeader(OMITTED_COUNT)).isNull();
        assertThat(mvc.perform(post("/api/metamodels/reload")).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(get("/api/metamodels/runtime-status")).andReturn().getResponse().getContentAsString()).doesNotContain("left unchanged");
    }

    @Test
    void concurrentReadsAndWritesWhileAMetamodelIsCorruptStayConsistent() throws Exception {
        metamodel("con-a");
        damage("con-a", UNREADABLE.getBytes(StandardCharsets.UTF_8));
        byte[] damaged = Files.readAllBytes(file("con-a"));
        Set<String> unexpected = ConcurrentHashMap.newKeySet();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> tasks = new ArrayList<>();
            for (int reader = 0; reader < 6; reader++) tasks.add(pool.submit(() -> {
                start.await();
                for (int read = 0; read < 10; read++) {
                    MockHttpServletResponse listing = mvc.perform(get("/api/canonical-metamodels")).andReturn().getResponse();
                    if (listing.getStatus() != 200 || listing.getHeader(OMITTED_ITEMS) == null || !listing.getHeader(OMITTED_ITEMS).contains("con-a"))
                        unexpected.add("listing " + listing.getStatus() + " " + listing.getHeader(OMITTED_ITEMS));
                    int item = mvc.perform(get("/api/canonical-metamodels/con-a")).andReturn().getResponse().getStatus();
                    if (item != 409) unexpected.add("item " + item);
                }
                return null;
            }));
            for (int writer = 0; writer < 2; writer++) {
                String id = "con-w" + writer;
                tasks.add(pool.submit(() -> {
                    start.await();
                    for (int round = 0; round < 3; round++) {
                        MockHttpServletResponse made = mvc.perform(post("/api/canonical-metamodels").contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsBytes(model(id)))).andReturn().getResponse();
                        if (made.getStatus() != 201) { unexpected.add("create " + made.getStatus()); continue; }
                        int removed = mvc.perform(delete("/api/canonical-metamodels/" + id).header("If-Match", made.getHeader("ETag"))).andReturn().getResponse().getStatus();
                        if (removed != 204) unexpected.add("delete " + removed);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> task : tasks) task.get(120, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertThat(unexpected).isEmpty();
        assertThat(Files.readAllBytes(file("con-a"))).as("the corrupt document is left as it is").isEqualTo(damaged);
    }

    @Test
    void aReadKeepsAValidTemporaryCopyNextToACorruptDocumentAndAnIoFailureIsStillAStorageError() throws Exception {
        metamodel("tmp-a");
        byte[] valid = Files.readAllBytes(file("tmp-a"));
        Path temporary = file("tmp-a").resolveSibling("tmp-a.metamodel.json.tmp");
        Files.write(temporary, valid);
        created.add(temporary);
        damage("tmp-a", UNREADABLE.getBytes(StandardCharsets.UTF_8));

        assertThat(mvc.perform(get("/api/canonical-metamodels/tmp-a")).andReturn().getResponse().getStatus()).isEqualTo(409);
        assertThat(temporary).as("next to a corrupt document the temporary file may be the only valid copy").exists();
        assertThat(Files.readAllBytes(temporary)).isEqualTo(valid);
        restore();

        Assumptions.assumeTrue(System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win"), "exclusive file sharing is a Windows behaviour");
        metamodel("io-a");
        try (FileChannel held = FileChannel.open(file("io-a"), StandardOpenOption.READ, StandardOpenOption.WRITE, com.sun.nio.file.ExtendedOpenOption.NOSHARE_READ,
                com.sun.nio.file.ExtendedOpenOption.NOSHARE_WRITE, com.sun.nio.file.ExtendedOpenOption.NOSHARE_DELETE)) {
            MockHttpServletResponse item = mvc.perform(get("/api/canonical-metamodels/io-a")).andReturn().getResponse();
            assertThat(item.getStatus()).as("a file that cannot be read is a failure of the storage, not a corrupt configuration").isEqualTo(500);
            assertThat(mapper.readTree(item.getContentAsString()).path("code").asString()).isEqualTo("STORAGE_ERROR");
            assertNoServerDetail(item.getContentAsString(), "I/O failure");
            MockHttpServletResponse listing = mvc.perform(get("/api/canonical-metamodels")).andReturn().getResponse();
            assertThat(listing.getStatus()).as("one unreadable file does not hide the other metamodels").isEqualTo(200);
            assertThat(listing.getHeader(OMITTED_ITEMS)).contains("io-a");
        }
        assertThat(mvc.perform(get("/api/canonical-metamodels/io-a")).andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    private void metamodel(String id) {
        metamodels.create(model(id));
    }

    private static MetaModel model(String id) {
        MetaClass node = new MetaClass(id + ".Node", "Node", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        return new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, id, id, List.of(node), List.of(), List.of(), List.of(), Map.of());
    }

    private static Path file(String id) {
        return root.resolve("metamodels").resolve(id + ".metamodel.json");
    }

    private void damage(String id, byte[] content) throws Exception {
        Path target = file(id);
        originals.putIfAbsent(target, Files.readAllBytes(target));
        Files.write(target, content);
    }

    /** The tests share one data directory: the damage of one test must not reach the next one (the listing scans every metamodel). */
    @AfterEach
    void restore() throws Exception {
        for (Map.Entry<Path, byte[]> original : originals.entrySet()) Files.write(original.getKey(), original.getValue());
        originals.clear();
        for (Path path : created) Files.deleteIfExists(path);
        created.clear();
    }

    /** SHA-256 and modification time of every stored metamodel document and of every temporary file next to them. */
    private static Map<String, String> snapshot() throws Exception {
        Map<String, String> files = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(root.resolve("metamodels"))) {
            for (Path path : walk.filter(Files::isRegularFile).toList()) {
                String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
                files.put(root.relativize(path).toString(), digest + "@" + Files.getLastModifiedTime(path).toMillis());
            }
        }
        return files;
    }

    /** A public text must not name a server path, a stored file, an exception class or a parser message. */
    private static void assertNoServerDetail(String text, String label) {
        String real;
        try { real = root.toRealPath().toString(); } catch (java.io.IOException exception) { real = root.toString(); }
        assertThat(text).as(label).doesNotContain(root.toString()).doesNotContain(real).doesNotContain(root.toString().replace('\\', '/'))
                .doesNotContainPattern("[A-Za-z]:[\\\\/]").doesNotContain("AppData").doesNotContain("/tmp/").doesNotContain(".metamodel.json")
                .doesNotContain("Exception").doesNotContain("Could not parse").doesNotContain("Unexpected character");
    }

    private static ListAppender<ILoggingEvent> capture() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(CanonicalMetamodelApplicationService.class)).addAppender(appender);
        return appender;
    }

    private static void release(ListAppender<ILoggingEvent> appender) {
        ((Logger) LoggerFactory.getLogger(CanonicalMetamodelApplicationService.class)).detachAppender(appender);
    }
}
