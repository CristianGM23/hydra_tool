package com.example.editor.validation.contract;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalValidationContractExamplesTests {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void exampleDocumentsUseStableCrossDocumentReferences() throws Exception {
        JsonNode expressions = read("library.expressions.json");
        JsonNode actions = read("library.actions.json");
        JsonNode validations = read("library.validations.json");

        Set<String> expressionIds = ids(expressions.path("expressions"));
        Set<String> actionIds = ids(actions.path("actions"));
        Set<String> ruleIds = ids(validations.path("rules"));

        assertThat(validations.path("expressionModelId").asString()).isEqualTo(expressions.path("id").asString());
        assertThat(validations.path("actionModelId").asString()).isEqualTo(actions.path("id").asString());

        for(JsonNode rule : validations.path("rules")) {
            assertThat(expressionIds).contains(rule.path("conditionExpressionId").asString());
            for(JsonNode quickFixId : rule.path("quickFixIds")) {
                assertThat(actionIds).contains(quickFixId.asString());
            }
        }

        for(JsonNode ruleSet : validations.path("ruleSets")) {
            for(JsonNode ruleId : ruleSet.path("ruleIds")) {
                assertThat(ruleIds).contains(ruleId.asString());
            }
        }
    }

    @Test
    void canonicalModelUsesMetamodelFeatureIdsInsteadOfEditableNames() throws Exception {
        JsonNode model = read("library.model.json");
        JsonNode book = model.path("elements").get(1);
        JsonNode relation = model.path("relations").get(0);

        assertThat(book.path("classId").asString()).isEqualTo("class:Book");
        assertThat(book.path("attributes").has("attribute:class:Book:pages")).isTrue();
        assertThat(relation.path("relationId").asString()).isEqualTo("reference:class:Library:books");
        assertThat(relation.has("referenceName")).isFalse();
    }


    @Test
    void canonicalModelReferencesExistingMetamodelElements() throws Exception {
        JsonNode metamodel = read("library.metamodel.json");
        JsonNode model = read("library.model.json");
        Set<String> structuralIds = new HashSet<>();

        for(JsonNode classNode : metamodel.path("classes")) {
            structuralIds.add(classNode.path("id").asString());
            for(JsonNode attribute : classNode.path("attributes")) {
                structuralIds.add(attribute.path("id").asString());
            }
        }
        for(JsonNode relation : metamodel.path("relations")) {
            structuralIds.add(relation.path("id").asString());
        }

        for(JsonNode element : model.path("elements")) {
            assertThat(structuralIds).contains(element.path("classId").asString());
        }
        assertThat(structuralIds).contains("attribute:class:Library:name");
        assertThat(structuralIds).contains("attribute:class:Book:title");
        assertThat(structuralIds).contains("attribute:class:Book:pages");
        for(JsonNode relation : model.path("relations")) {
            assertThat(structuralIds).contains(relation.path("relationId").asString());
        }
    }


    @Test
    void operationBehaviorExampleReferencesExistingOperationAndExpression() throws Exception {
        JsonNode metamodel = read("library.metamodel.json");
        JsonNode expressions = read("library.expressions.json");
        JsonNode behaviors = read("library.operation-behaviors.json");
        Set<String> operationIds = new HashSet<>();
        for(JsonNode metaClass : metamodel.path("classes")) {
            for(JsonNode operation : metaClass.path("operations")) operationIds.add(operation.path("id").asString());
        }
        Set<String> expressionIds = ids(expressions.path("expressions"));

        assertThat(behaviors.path("metamodelId").asString()).isEqualTo(metamodel.path("id").asString());
        for(JsonNode binding : behaviors.path("bindings")) {
            assertThat(operationIds).contains(binding.path("operationId").asString());
            if("EXPRESSION".equals(binding.path("implementationKind").asString())) {
                assertThat(expressionIds).contains(binding.path("expressionId").asString());
            }
        }
    }

    private Set<String> ids(JsonNode nodes) {
        Set<String> result = new HashSet<>();
        for(JsonNode node : nodes) {
            result.add(node.path("id").asString());
        }
        return result;
    }

    private JsonNode read(String name) throws Exception {
        String path = "/validation/contracts/examples/" + name;
        try(InputStream input = CanonicalValidationContractExamplesTests.class.getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return jsonMapper.readTree(input);
        }
    }
}
