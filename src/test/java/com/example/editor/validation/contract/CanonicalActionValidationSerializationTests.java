package com.example.editor.validation.contract;

import java.io.InputStream;

import org.junit.jupiter.api.Test;

import com.example.editor.validation.action.ActionModel;
import com.example.editor.validation.validation.ValidationModel;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalActionValidationSerializationTests {

    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void deserializesAndSerializesActionModelWithoutTechnologySpecificTypes() throws Exception {
        ActionModel model = read("library.actions.json", ActionModel.class);
        JsonNode serialized = mapper.readTree(mapper.writeValueAsBytes(model));
        assertThat(model.id()).isEqualTo("library-actions");
        assertThat(model.metamodelId()).isEqualTo("library");
        assertThat(serialized.path("actions").get(0).path("operations").get(0).path("kind").asString()).isEqualTo("SET_ATTRIBUTE");
        assertThat(serialized.path("actions").get(0).path("operations").get(0).path("value").has("expressionId")).isFalse();
    }

    @Test
    void deserializesAndSerializesValidationModelWithQuickFixReferences() throws Exception {
        ValidationModel model = read("library.validations.json", ValidationModel.class);
        JsonNode serialized = mapper.readTree(mapper.writeValueAsBytes(model));
        assertThat(model.id()).isEqualTo("library-validations");
        assertThat(model.rules()).singleElement().satisfies(rule -> assertThat(rule.quickFixIds()).containsExactly("action:set-pages-one"));
        assertThat(serialized.path("rules").get(0).path("authoritativeRepresentation").asString()).isEqualTo("CANONICAL");
    }


    @Test
    void serializesCanonicalEmptyCollectionsInsteadOfDroppingTheUiContract() throws Exception {
        ActionModel actions = new ActionModel("1.0", "library-actions", "library", "library-expressions", java.util.List.of(), java.util.Map.of());
        JsonNode serializedActions = mapper.readTree(mapper.writeValueAsBytes(actions));
        assertThat(serializedActions.has("actions")).isTrue();
        assertThat(serializedActions.path("actions").isArray()).isTrue();
        assertThat(serializedActions.path("actions").isEmpty()).isTrue();

        ValidationModel validations = new ValidationModel("1.0", "library-validations", "library", "library-expressions", "library-actions", java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.Map.of());
        JsonNode serializedValidations = mapper.readTree(mapper.writeValueAsBytes(validations));
        assertThat(serializedValidations.path("ruleSets").isArray()).isTrue();
        assertThat(serializedValidations.path("ruleSets").isEmpty()).isTrue();
        assertThat(serializedValidations.path("rules").isArray()).isTrue();
        assertThat(serializedValidations.path("rules").isEmpty()).isTrue();
        assertThat(serializedValidations.path("importedSources").isArray()).isTrue();
        assertThat(serializedValidations.path("importedSources").isEmpty()).isTrue();
    }

    private <T> T read(String name, Class<T> type) throws Exception {
        String path = "/validation/contracts/examples/" + name;
        try(InputStream input = CanonicalActionValidationSerializationTests.class.getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return mapper.readValue(input, type);
        }
    }
}
