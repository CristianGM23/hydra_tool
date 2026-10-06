package com.example.editor.validation.validation.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.config.StorageProperties;
import com.example.editor.semantic.expression.ExpressionTestFixtures;
import com.example.editor.validation.persistence.CanonicalDocumentStoreException;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
import com.example.editor.validation.validation.AuthoritativeRepresentation;
import com.example.editor.validation.validation.ConversionStatus;
import com.example.editor.validation.validation.ValidationContext;
import com.example.editor.validation.validation.ValidationContextKind;
import com.example.editor.validation.validation.ValidationDependency;
import com.example.editor.validation.validation.ValidationDependencyKind;
import com.example.editor.validation.validation.ValidationExecutionMode;
import com.example.editor.validation.validation.ValidationModel;
import com.example.editor.validation.validation.ValidationModelProperties;
import com.example.editor.validation.validation.ValidationRule;
import com.example.editor.validation.validation.ValidationRuleSet;
import com.example.editor.validation.validation.ValidationSeverity;
import com.example.editor.validation.validation.ValidationTrigger;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonValidationModelRepositoryTests {
    @TempDir Path temporary;
    private JsonValidationModelRepository repository;
    @BeforeEach void setUp() {
        StorageProperties storage = new StorageProperties(temporary.resolve("metamodels"), temporary.resolve("models"), temporary.resolve("notations"), temporary.resolve("validations"), temporary.resolve("quickfixes"), temporary.resolve("generators"), temporary.resolve("assets"), temporary.resolve("exports"), true);
        repository = new JsonValidationModelRepository(storage, new ValidationModelProperties(), JsonMapper.builder().findAndAddModules().build());
    }
    @Test void createsReadsReplacesAndDeletes() {
        ValidationModel model = model(Map.of());
        StoredCanonicalDocument<ValidationModel> created = repository.create(model);
        assertThat(repository.findById("library").orElseThrow().document()).isEqualTo(model);
        assertThat(Files.isRegularFile(temporary.resolve("validations/library.validations.json"))).isTrue();
        ValidationModel changed = model(Map.of("version", "2"));
        StoredCanonicalDocument<ValidationModel> replaced = repository.replace("library", changed, created.etag());
        assertThat(replaced.document().annotations()).containsEntry("version", "2");
        repository.delete("library", replaced.etag());
        assertThat(repository.findById("library")).isEmpty();
    }
    @Test void requiresCurrentEtag() {
        ValidationModel model = model(Map.of());
        repository.create(model);
        assertThatThrownBy(() -> repository.replace("library", model, "")).isInstanceOf(CanonicalDocumentStoreException.class).extracting("reason").isEqualTo(CanonicalDocumentStoreException.Reason.PRECONDITION_FAILED);
    }
    @Test void listsTheCanonicalRuleCounts() {
        repository.create(model(Map.of()));
        assertThat(repository.findAll()).singleElement().satisfies(summary -> {
            assertThat(summary.id()).isEqualTo("library-validations");
            assertThat(summary.ruleCount()).isEqualTo(1);
            assertThat(summary.ruleSetCount()).isEqualTo(1);
        });
    }
    private ValidationModel model(Map<String, String> annotations) {
        ValidationRule rule = new ValidationRule("rule:positive-age", "Positive age", "POSITIVE_AGE", "", true, new ValidationContext(ValidationContextKind.ATTRIBUTE, ExpressionTestFixtures.PERSON, ExpressionTestFixtures.AGE), ValidationSeverity.ERROR, "expression:positive-age", "Age must be positive", List.of(ValidationTrigger.ON_SAVE), List.of(new ValidationDependency(ValidationDependencyKind.ATTRIBUTE, ExpressionTestFixtures.AGE)), List.of(), ValidationExecutionMode.INCREMENTAL, AuthoritativeRepresentation.CANONICAL, ConversionStatus.FULL, null, Map.of());
        return new ValidationModel("1.0", "library-validations", "library", "library", "", List.of(new ValidationRuleSet("set:default", "Default", "", true, 0, List.of(rule.id()), Map.of())), List.of(rule), List.of(), annotations);
    }
}
