package com.example.editor.metamodel.core;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba que el contrato neutral se serializa sin EMF.
 */
class NeutralMetamodelJsonRoundTripTests {

    @Test
    void serializesAndReadsNeutralMetamodel() throws Exception {
        MetaAttribute attribute = new MetaAttribute("attribute-name", "name", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.one(), true, true, "", Map.of());
        MetaClass classDesign = new MetaClass("class-person", "Person", MetaClassKind.CLASS, List.of(attribute), List.of(), Map.of("owner", "test"));
        MetaModel original = new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, "sample", "sample", List.of(classDesign), List.of(), List.of(), List.of(), Map.of("source", "test"));
        JsonMapper jsonMapper = JsonMapper.builder().build();

        byte[] json = jsonMapper.writeValueAsBytes(original);
        MetaModel restored = jsonMapper.readValue(json, MetaModel.class);

        assertThat(restored).isEqualTo(original);
        assertThat(new String(json, StandardCharsets.UTF_8)).contains("\"schemaVersion\"", "\"classes\"");
    }
}
