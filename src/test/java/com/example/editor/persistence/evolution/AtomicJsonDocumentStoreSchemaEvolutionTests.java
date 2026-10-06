package com.example.editor.persistence.evolution;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.validation.persistence.AtomicJsonDocumentStore;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class AtomicJsonDocumentStoreSchemaEvolutionTests {
    @TempDir Path temporary;

    @Test void evolvesBeforeDeserializationAndKeepsTheOriginalBytes() throws Exception {
        JsonMapper mapper = JsonMapper.builder().build();
        HydraSchemaEvolutionService schemaEvolution = new HydraSchemaEvolutionService(List.of(new HydraJsonSchemaEvolution() {
            public String namespace() { return "test.document"; }
            public String fromVersion() { return "1.0"; }
            public String toVersion() { return "2.0"; }
            public JsonNode evolve(JsonNode source) { ((ObjectNode) source).put("schemaVersion", "2.0"); return source; }
        }));
        Path document = temporary.resolve("sample.json");
        Files.writeString(document, "{\"schemaVersion\":\"1.0\",\"id\":\"sample\"}");
        AtomicJsonDocumentStore<Sample> store = new AtomicJsonDocumentStore<>(temporary, ".json", false, mapper,
                Sample.class, Sample::id, "test.document", null, null, schemaEvolution);

        assertThat(store.find("sample").orElseThrow().document().schemaVersion()).isEqualTo("2.0");
        assertThat(mapper.readTree(Files.readString(document)).get("schemaVersion").asString()).isEqualTo("2.0");
        assertThat(temporary.resolve("sample.json.schema-1.0.bak")).hasContent("{\"schemaVersion\":\"1.0\",\"id\":\"sample\"}");
    }

    record Sample(String schemaVersion, String id) { }
}
