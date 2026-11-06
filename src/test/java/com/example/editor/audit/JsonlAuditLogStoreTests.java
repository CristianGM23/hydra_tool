package com.example.editor.audit;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Evidencia FUERTE real para la etapa "c" (anotar en el registro / append-only) de
 * audit_record_mutation: integración de sistema de ficheros, sin mocks, que ejercita
 * {@link JsonlAuditLogStore#appendBatch(List)} y lo que describe su propio javadoc
 * ("Store local append-only particionado por día"), con un oráculo independiente
 * (lectura cruda del fichero en disco, no solo a través del propio store).
 */
class JsonlAuditLogStoreTests {

    @TempDir
    Path temporaryDirectory;

    private JsonlAuditLogStore store;
    private JsonMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = JsonMapper.builder().findAndAddModules().build();
        AuditLogProperties properties = new AuditLogProperties();
        properties.setStorageRoot(temporaryDirectory.resolve("audit-log"));
        properties.setRetentionEnabled(false);
        store = new JsonlAuditLogStore(properties, mapper);
    }

    private AuditEntry entry(String eventId, Instant timestamp, String action) {
        return new AuditEntry(AuditEntry.SCHEMA_VERSION, eventId, timestamp,
                new AuditActor("tester", "Tester", "session-1", Map.of()), "workspace-1",
                "DOCUMENT_MUTATION", "metamodel", action, AuditImportance.INFO, AuditResult.SUCCESS,
                new AuditResource("metamodel", "metamodel", "mm-" + eventId, "Sample metamodel " + eventId),
                List.of(), false, Map.of(), "", "");
    }

    @Test
    void appendsEntriesAsIndependentlyReadableJsonlLinesOnDisk() throws Exception {
        Instant day = Instant.parse("2026-10-10T10:00:00Z");
        store.appendBatch(List.of(entry("evt-1", day, AuditActions.CREATE), entry("evt-2", day, AuditActions.UPDATE)));

        Path partitionDir = temporaryDirectory.resolve("audit-log");
        List<Path> partitions;
        try (var stream = Files.list(partitionDir)) {
            partitions = stream.toList();
        }
        assertThat(partitions).hasSize(1);
        List<String> rawLines = Files.readAllLines(partitions.get(0), StandardCharsets.UTF_8);
        assertThat(rawLines).hasSize(2);
        // Oráculo independiente: parseamos las líneas crudas nosotros mismos, sin pasar por el store.
        assertThat(mapper.readTree(rawLines.get(0)).get("eventId").asString()).isEqualTo("evt-1");
        assertThat(mapper.readTree(rawLines.get(1)).get("eventId").asString()).isEqualTo("evt-2");
        assertThat(mapper.readTree(rawLines.get(0)).get("action").asString()).isEqualTo("CREATE");
    }

    @Test
    void queryReturnsAppendedEntriesInDescendingTimestampOrderByDefault() {
        Instant base = Instant.parse("2026-10-10T08:00:00Z");
        store.appendBatch(List.of(
                entry("evt-early", base, AuditActions.CREATE),
                entry("evt-mid", base.plusSeconds(60), AuditActions.UPDATE),
                entry("evt-late", base.plusSeconds(120), AuditActions.UPDATE)));

        AuditPage page = store.query(AuditQuery.recent("workspace-1", base.minusSeconds(1), base.plusSeconds(200), 0, 10));

        assertThat(page.entries()).extracting(AuditEntry::eventId)
                .containsExactly("evt-late", "evt-mid", "evt-early");
    }

    @Test
    void appendIsStrictlyAdditiveAndNeverRewritesPriorEntries() throws Exception {
        Instant day = Instant.parse("2026-10-10T09:00:00Z");
        store.appendBatch(List.of(entry("evt-first", day, AuditActions.CREATE)));

        Path partitionDir = temporaryDirectory.resolve("audit-log");
        Path partitionFile;
        try (var stream = Files.list(partitionDir)) {
            partitionFile = stream.findFirst().orElseThrow();
        }
        String firstLineAfterFirstAppend = Files.readAllLines(partitionFile, StandardCharsets.UTF_8).get(0);

        store.appendBatch(List.of(entry("evt-second", day.plusSeconds(5), AuditActions.UPDATE)));

        List<String> linesAfterSecondAppend = Files.readAllLines(partitionFile, StandardCharsets.UTF_8);
        assertThat(linesAfterSecondAppend).hasSize(2);
        // La entrada original debe seguir siendo exactamente la misma línea de bytes: nunca se reescribe.
        assertThat(linesAfterSecondAppend.get(0)).isEqualTo(firstLineAfterFirstAppend);
        assertThat(mapper.readTree(linesAfterSecondAppend.get(1)).get("eventId").asString()).isEqualTo("evt-second");
    }

    @Test
    void queryToleratesAMalformedLineWithoutLosingOrCorruptingValidEntries() throws Exception {
        Instant day = Instant.parse("2026-10-10T11:00:00Z");
        store.appendBatch(List.of(entry("evt-valid", day, AuditActions.CREATE)));
        Path partitionDir = temporaryDirectory.resolve("audit-log");
        Path partitionFile;
        try (var stream = Files.list(partitionDir)) {
            partitionFile = stream.findFirst().orElseThrow();
        }
        // Inyectamos una línea corrupta directamente en el fichero, fuera del store, simulando una
        // escritura interrumpida o un bit-flip, tal como describe el caso negativo de esta etapa.
        Files.write(partitionFile, "{not-json".getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.APPEND);

        AuditPage page = store.query(AuditQuery.recent("workspace-1", day.minusSeconds(1), day.plusSeconds(60), 0, 10));

        assertThat(page.entries()).extracting(AuditEntry::eventId).containsExactly("evt-valid");
    }
}
