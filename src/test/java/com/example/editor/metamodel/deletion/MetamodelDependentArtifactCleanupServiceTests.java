package com.example.editor.metamodel.deletion;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.config.StorageProperties;
import com.example.editor.definition.EditorDefinitionProperties;
import com.example.editor.event.EventModelProperties;
import com.example.editor.event.flow.EventFlowProperties;
import com.example.editor.gamification.GamificationProperties;
import com.example.editor.integration.IntegrationProfileProperties;
import com.example.editor.grammar.GrammarProperties;
import com.example.editor.textual.TextualModelProperties;
import com.example.editor.transformation.TransformationProperties;

import tools.jackson.databind.json.JsonMapper;

class MetamodelDependentArtifactCleanupServiceTests {
    @TempDir Path temporary;

    @Test void scansOnlyConfiguredLayerRootsAndNeverTreatsSavedModelsAsExports() throws Exception {
        StorageProperties storage = storage();
        EditorDefinitionProperties editors = new EditorDefinitionProperties();
        editors.setDirectory(temporary.resolve("custom-editors"));
        IntegrationProfileProperties integrations = new IntegrationProfileProperties();
        integrations.setDirectory(temporary.resolve("custom-integrations"));
        EventModelProperties events = new EventModelProperties();
        events.setDirectory(temporary.resolve("custom-events"));
        EventFlowProperties flows = new EventFlowProperties();
        flows.setDirectory(temporary.resolve("custom-event-flows"));
        GamificationProperties gamification = new GamificationProperties();
        gamification.setDirectory(temporary.resolve("custom-gamification"));
        for (Path directory : storage.allDirectories()) Files.createDirectories(directory);
        Files.createDirectories(editors.getDirectory());
        Files.createDirectories(integrations.getDirectory());
        Files.createDirectories(events.getDirectory());
        Files.createDirectories(flows.getDirectory());
        Files.createDirectories(gamification.getDirectory());

        Path validation = storage.validations().resolve("people.validations.json");
        Path notation = storage.notations().resolve("people.notation.json");
        Path exportedXmi = storage.exports().resolve("people.xmi");
        Path savedModel = storage.models().resolve("people.xmi");
        Path unrelated = storage.validations().resolve("orders.validations.json");
        Files.writeString(validation, "{}");
        Files.writeString(notation, "{}");
        Files.writeString(exportedXmi, "export");
        Files.writeString(savedModel, "saved model");
        Files.writeString(unrelated, "{}");

        var service = newCleanupService(storage, editors, integrations, events, flows, gamification);
        var result = service.cleanup("people", true, true);

        assertThat(validation).doesNotExist();
        assertThat(notation).doesNotExist();
        assertThat(exportedXmi).doesNotExist();
        assertThat(savedModel).exists();
        assertThat(unrelated).exists();
        assertThat(result.deletedFiles()).hasSize(3);
    }

    @Test void honorsNotationAndExportDeletionOptionsIndependently() throws Exception {
        StorageProperties storage = storage();
        EditorDefinitionProperties editors = new EditorDefinitionProperties();
        IntegrationProfileProperties integrations = new IntegrationProfileProperties();
        EventModelProperties events = new EventModelProperties();
        EventFlowProperties flows = new EventFlowProperties();
        GamificationProperties gamification = new GamificationProperties();
        for (Path directory : storage.allDirectories()) Files.createDirectories(directory);

        Path notation = storage.notations().resolve("people.diagram.json");
        Path exported = storage.exports().resolve("people.ecore");
        Files.writeString(notation, "{}");
        Files.writeString(exported, "ecore");

        var service = newCleanupService(storage, editors, integrations, events, flows, gamification);
        service.cleanup("people", false, false);

        assertThat(notation).exists();
        assertThat(exported).exists();
    }

    @Test void removesNewLayerArtifactsByExplicitReferencesInsteadOfFilenamePrefix() throws Exception {
        StorageProperties storage = storage();
        EditorDefinitionProperties editors = new EditorDefinitionProperties();
        IntegrationProfileProperties integrations = new IntegrationProfileProperties();
        EventModelProperties events = new EventModelProperties();
        EventFlowProperties flows = new EventFlowProperties();
        GamificationProperties gamification = new GamificationProperties();

        TransformationProperties transformations = new TransformationProperties();
        transformations.setDirectory(temporary.resolve("transformations"));
        transformations.setTestDirectory(temporary.resolve("transformation-tests"));
        transformations.setSynchronizationSessionDirectory(temporary.resolve("transformation-sync-sessions"));
        transformations.setSynchronizationHistoryDirectory(temporary.resolve("transformation-sync-history"));
        GrammarProperties grammars = new GrammarProperties();
        grammars.setDirectory(temporary.resolve("grammars"));
        grammars.setCompilationDirectory(temporary.resolve("grammar-compilations"));
        grammars.setActivationDirectory(temporary.resolve("grammar-activations"));
        TextualModelProperties textual = new TextualModelProperties();
        textual.setDefinitionDirectory(temporary.resolve("textual-definitions"));
        textual.setSessionDirectory(temporary.resolve("textual-sessions"));
        textual.setSyntaxStyleDirectory(temporary.resolve("textual-styles"));

        for (Path directory : List.of(transformations.getDirectory(), transformations.getTestDirectory(),
                transformations.getSynchronizationSessionDirectory(), transformations.getSynchronizationHistoryDirectory(),
                grammars.getDirectory(), grammars.getCompilationDirectory(), grammars.getActivationDirectory(),
                textual.getDefinitionDirectory(), textual.getSessionDirectory(), textual.getSyntaxStyleDirectory())) {
            Files.createDirectories(directory);
        }

        Path transformation = transformations.getDirectory().resolve("rename-people.transformation.json");
        Path transformationTest = transformations.getTestDirectory().resolve("case-1.transformation-test.json");
        Path grammar = grammars.getDirectory().resolve("people-language.grammar.json");
        Path grammarSnapshot = grammars.getCompilationDirectory().resolve("snapshot-1.grammar-snapshot.json");
        Path textualSession = textual.getSessionDirectory().resolve("session-1.textual-session.json");
        Path unrelated = transformations.getDirectory().resolve("orders.transformation.json");
        Files.writeString(transformation, "{\"id\":\"tx-people\",\"sources\":[{\"metamodelId\":\"people\"}],\"targets\":[{\"metamodelId\":\"archive\"}]}");
        Files.writeString(transformationTest, "{\"id\":\"test-1\",\"transformationId\":\"tx-people\"}");
        Files.writeString(grammar, "{\"id\":\"grammar-people\",\"metamodelId\":\"people\"}");
        Files.writeString(grammarSnapshot, "{\"id\":\"snapshot-1\",\"grammarId\":\"grammar-people\"}");
        Files.writeString(textualSession, "{\"id\":\"session-1\",\"grammarId\":\"grammar-people\"}");
        Files.writeString(unrelated, "{\"id\":\"tx-orders\",\"sources\":[{\"metamodelId\":\"orders\"}]}");

        var service = new MetamodelDependentArtifactCleanupService(storage, editors, integrations, events, flows, gamification,
                transformations, grammars, textual, JsonMapper.builder().findAndAddModules().build());
        var result = service.cleanup("people", false, false);

        assertThat(transformation).doesNotExist();
        assertThat(transformationTest).doesNotExist();
        assertThat(grammar).doesNotExist();
        assertThat(grammarSnapshot).doesNotExist();
        assertThat(textualSession).doesNotExist();
        assertThat(unrelated).exists();
        assertThat(result.deletedFiles()).hasSize(5);
    }

    private MetamodelDependentArtifactCleanupService newCleanupService(StorageProperties storage,
            EditorDefinitionProperties editors, IntegrationProfileProperties integrations,
            EventModelProperties events, EventFlowProperties flows, GamificationProperties gamification) {
        TransformationProperties transformations = new TransformationProperties();
        transformations.setDirectory(temporary.resolve("transformations"));
        transformations.setTestDirectory(temporary.resolve("transformation-tests"));
        transformations.setSynchronizationSessionDirectory(temporary.resolve("transformation-sync-sessions"));
        transformations.setSynchronizationHistoryDirectory(temporary.resolve("transformation-sync-history"));
        GrammarProperties grammars = new GrammarProperties();
        grammars.setDirectory(temporary.resolve("grammars"));
        grammars.setCompilationDirectory(temporary.resolve("grammar-compilations"));
        grammars.setActivationDirectory(temporary.resolve("grammar-activations"));
        TextualModelProperties textual = new TextualModelProperties();
        textual.setDefinitionDirectory(temporary.resolve("textual-definitions"));
        textual.setSessionDirectory(temporary.resolve("textual-sessions"));
        textual.setSyntaxStyleDirectory(temporary.resolve("textual-styles"));
        return new MetamodelDependentArtifactCleanupService(storage, editors, integrations, events, flows, gamification,
                transformations, grammars, textual, JsonMapper.builder().findAndAddModules().build());
    }

    private StorageProperties storage() {
        return new StorageProperties(
                temporary.resolve("metamodels"), temporary.resolve("models"), temporary.resolve("notations"),
                temporary.resolve("validations"), temporary.resolve("quickfixes"), temporary.resolve("generators"),
                temporary.resolve("assets"), temporary.resolve("exports"), true);
    }
}
