package com.example.editor.persistence.evolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.validation.persistence.AtomicJsonDocumentStore;
import com.example.editor.validation.persistence.CanonicalDocumentStoreException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * DT-TXT-SAVE-EVENTS: sin migraciones para el namespace, la lectura no construye el árbol JSON de evolución. El resultado observable debe ser
 * el mismo: documento, ETag de los bytes exactos del fichero, fichero intacto, sin copia de seguridad y STORAGE_ERROR ante JSON corrupto.
 */
class AtomicJsonDocumentStoreReadWithoutEvolutionTests {
    @TempDir Path temporary;

    @Test void namespacesWithoutEvolutionsAreReportedAsSuch() {
        HydraSchemaEvolutionService service = new HydraSchemaEvolutionService(List.of(evolution("other.document")));
        assertThat(service.hasEvolutions("other.document")).isTrue();
        assertThat(service.hasEvolutions("test.document")).isFalse();
        assertThat(service.hasEvolutions(null)).isFalse();
        assertThat(HydraSchemaEvolutionService.none().hasEvolutions("other.document")).isFalse();
    }

    @Test void readsWithoutTouchingTheFileWhenOnlyOtherNamespacesEvolve() throws Exception {
        String original = "{ \"schemaVersion\" : \"1.0\", \"id\" : \"sample\" }";
        Path document = temporary.resolve("sample.json");
        Files.writeString(document, original);
        AtomicJsonDocumentStore<Sample> store = store(new HydraSchemaEvolutionService(List.of(evolution("other.document"))));

        var stored = store.find("sample").orElseThrow();

        assertThat(stored.document()).isEqualTo(new Sample("1.0", "sample"));
        assertThat(stored.etag()).isEqualTo(sha256(Files.readAllBytes(document)));
        assertThat(document).hasContent(original);
        try(var files = Files.list(temporary)) { assertThat(files.map(path -> path.getFileName().toString()).toList()).containsExactly("sample.json"); }
    }

    @Test void replaceStillVerifiesTheEtagOfTheStoredBytes() throws Exception {
        Files.writeString(temporary.resolve("sample.json"), "{\"schemaVersion\":\"1.0\",\"id\":\"sample\"}");
        AtomicJsonDocumentStore<Sample> store = new AtomicJsonDocumentStore<>(temporary, ".json", true, JsonMapper.builder().build(),
                Sample.class, Sample::id, "test.document", null, null, HydraSchemaEvolutionService.none());
        String etag = store.find("sample").orElseThrow().etag();
        assertThatThrownBy(() -> store.replace("sample", new Sample("1.1", "sample"), "\"stale\""))
                .isInstanceOfSatisfying(CanonicalDocumentStoreException.class,
                        exception -> assertThat(exception.getReason()).isEqualTo(CanonicalDocumentStoreException.Reason.PRECONDITION_FAILED));
        assertThat(store.replace("sample", new Sample("1.1", "sample"), etag).document().schemaVersion()).isEqualTo("1.1");
    }

    @Test void corruptJsonIsStillAStorageError() throws Exception {
        Files.writeString(temporary.resolve("sample.json"), "{\"schemaVersion\":\"1.0\",\"id\":");
        AtomicJsonDocumentStore<Sample> store = store(HydraSchemaEvolutionService.none());
        assertThatThrownBy(() -> store.find("sample"))
                .isInstanceOfSatisfying(CanonicalDocumentStoreException.class,
                        exception -> assertThat(exception.getReason()).isEqualTo(CanonicalDocumentStoreException.Reason.STORAGE_ERROR));
    }

    private AtomicJsonDocumentStore<Sample> store(HydraSchemaEvolutionService schemaEvolution) {
        return new AtomicJsonDocumentStore<>(temporary, ".json", false, JsonMapper.builder().build(),
                Sample.class, Sample::id, "test.document", null, null, schemaEvolution);
    }

    private static HydraJsonSchemaEvolution evolution(String namespace) {
        return new HydraJsonSchemaEvolution() {
            public String namespace() { return namespace; }
            public String fromVersion() { return "1.0"; }
            public String toVersion() { return "2.0"; }
            public JsonNode evolve(JsonNode source) { ((ObjectNode) source).put("schemaVersion", "2.0"); return source; }
        };
    }

    private static String sha256(byte[] content) throws Exception {
        return "\"" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)) + "\"";
    }

    record Sample(String schemaVersion, String id) { }
}
