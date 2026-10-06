package com.example.editor.validation.action.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.config.StorageProperties;
import com.example.editor.validation.action.ActionDefinition;
import com.example.editor.validation.action.ActionModel;
import com.example.editor.validation.action.ActionModelProperties;
import com.example.editor.validation.action.ActionSelector;
import com.example.editor.validation.action.ActionSelectorKind;
import com.example.editor.validation.action.ActionTransactionPolicy;
import com.example.editor.validation.action.ActionUndoPolicy;
import com.example.editor.validation.action.ActionValueSource;
import com.example.editor.validation.action.SetAttributeOperation;
import com.example.editor.semantic.expression.ExpressionTestFixtures;
import com.example.editor.validation.persistence.CanonicalDocumentStoreException;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonActionModelRepositoryTests {
    @TempDir Path temporary;
    private JsonActionModelRepository repository;
    @BeforeEach void setUp() {
        StorageProperties storage = new StorageProperties(temporary.resolve("metamodels"), temporary.resolve("models"), temporary.resolve("notations"), temporary.resolve("validations"), temporary.resolve("quickfixes"), temporary.resolve("generators"), temporary.resolve("assets"), temporary.resolve("exports"), true);
        repository = new JsonActionModelRepository(storage, new ActionModelProperties(), JsonMapper.builder().findAndAddModules().build());
    }
    @Test void createsReadsReplacesAndDeletes() {
        ActionModel model = model(Map.of());
        StoredCanonicalDocument<ActionModel> created = repository.create(model);
        assertThat(repository.findById("library").orElseThrow().document()).isEqualTo(model);
        assertThat(Files.isRegularFile(temporary.resolve("validations/library.actions.json"))).isTrue();
        ActionModel changed = model(Map.of("version", "2"));
        StoredCanonicalDocument<ActionModel> replaced = repository.replace("library", changed, created.etag());
        assertThat(replaced.document().annotations()).containsEntry("version", "2");
        repository.delete("library", replaced.etag());
        assertThat(repository.findById("library")).isEmpty();
    }
    @Test void rejectsDuplicateCreationAndStaleEtags() {
        ActionModel model = model(Map.of());
        repository.create(model);
        assertThatThrownBy(() -> repository.create(model)).isInstanceOf(CanonicalDocumentStoreException.class).extracting("reason").isEqualTo(CanonicalDocumentStoreException.Reason.CONFLICT);
        assertThatThrownBy(() -> repository.replace("library", model, "\"stale\"")).isInstanceOf(CanonicalDocumentStoreException.class).extracting("reason").isEqualTo(CanonicalDocumentStoreException.Reason.PRECONDITION_FAILED);
    }
    @Test void listsByOwningMetamodelRatherThanDocumentId() {
        repository.create(model(Map.of()));
        assertThat(repository.findAll()).singleElement().satisfies(summary -> {
            assertThat(summary.id()).isEqualTo("library-actions");
            assertThat(summary.metamodelId()).isEqualTo("library");
            assertThat(summary.actionCount()).isEqualTo(1);
        });
    }
    private ActionModel model(Map<String, String> annotations) {
        ActionDefinition action = new ActionDefinition("action:set-age", "Set age", "", true, List.of(), "", List.of(new SetAttributeOperation("operation:set", new ActionSelector(ActionSelectorKind.SELF, "", ""), ExpressionTestFixtures.AGE, ActionValueSource.literal(0))), ActionTransactionPolicy.ATOMIC, ActionUndoPolicy.REQUIRED, Map.of());
        return new ActionModel("1.0", "library-actions", "library", "library", List.of(action), annotations);
    }
}
