package com.example.editor.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.config.StorageProperties;
import com.example.editor.definition.EditorDefinitionTestFixtures;
import com.example.editor.grammar.GrammarProperties;
import com.example.editor.grammar.persistence.GrammarRepository;
import com.example.editor.grammar.persistence.JsonGrammarRepository;
import com.example.editor.integration.persistence.JsonIntegrationProfileRepository;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.persistence.LocalCanonicalDocumentStoreFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia real, sin mocks, de que IntegrationProfileApplicationService.create() comprueba de
 * verdad el perfil y solo lo guarda cuando el informe es valido. Cubre directamente "sv" (Analizar
 * y guardar) de integration_author_views -- el mismo guardado real sirve a las tres vistas
 * (formulario completo, arbol de estructura) descritas por ese proceso de autoria.
 */
class IntegrationProfileApplicationServiceSaveIntegrationTests {

    @Test
    void savesARealProfileOnceTheRealCheckerResolvesAllRequiredArtifacts(@TempDir Path temporary) {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var storage = new StorageProperties(temporary.resolve("metamodels"), temporary.resolve("models"), temporary.resolve("notations"),
                temporary.resolve("validations"), temporary.resolve("quickfixes"), temporary.resolve("generators"), temporary.resolve("assets"),
                temporary.resolve("exports"), true);
        var metamodelService = new CanonicalMetamodelApplicationService(new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());
        metamodelService.create(EditorDefinitionTestFixtures.metamodel());

        var profileProperties = new IntegrationProfileProperties();
        profileProperties.setDirectory(temporary.resolve("integration-profiles"));
        var repository = new JsonIntegrationProfileRepository(profileProperties, mapper);

        IntegrationArtifactResolver resolver = (metamodelId, artifact) -> new IntegrationArtifactResolution(artifact, IntegrationResolutionStatus.RESOLVED, "Real artifact found for " + artifact.kind());
        var checker = new IntegrationProfileChecker(resolver);
        var grammarProperties = new GrammarProperties();
        grammarProperties.setDirectory(temporary.resolve("grammars"));
        GrammarRepository grammars = new JsonGrammarRepository(grammarProperties, mapper, new LocalCanonicalDocumentStoreFactory());
        var service = new IntegrationProfileApplicationService(repository, metamodelService, checker, grammars);

        var profile = IntegrationProfileTestFixtures.emf();
        var saved = service.create("people", profile);

        assertThat(saved.etag()).isNotBlank();
        assertThat(saved.checkReport().valid()).isTrue();
        assertThat(saved.checkReport().artifactResolutions()).isNotEmpty().allMatch(value -> value.status() == IntegrationResolutionStatus.RESOLVED);

        var reread = new JsonIntegrationProfileRepository(profileProperties, mapper).findById(profile.id()).orElseThrow();
        assertThat(reread.document().target().technology()).isEqualTo(profile.target().technology());
    }

    @Test
    void refusesToSaveARealProfileWhenTheRealCheckerCannotResolveRequiredArtifacts(@TempDir Path temporary) {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var storage = new StorageProperties(temporary.resolve("metamodels"), temporary.resolve("models"), temporary.resolve("notations"),
                temporary.resolve("validations"), temporary.resolve("quickfixes"), temporary.resolve("generators"), temporary.resolve("assets"),
                temporary.resolve("exports"), true);
        var metamodelService = new CanonicalMetamodelApplicationService(new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());
        metamodelService.create(EditorDefinitionTestFixtures.metamodel());

        var profileProperties = new IntegrationProfileProperties();
        profileProperties.setDirectory(temporary.resolve("integration-profiles"));
        var repository = new JsonIntegrationProfileRepository(profileProperties, mapper);

        IntegrationArtifactResolver resolver = (metamodelId, artifact) -> new IntegrationArtifactResolution(artifact, IntegrationResolutionStatus.NOT_FOUND, "No real artifact is registered");
        var checker = new IntegrationProfileChecker(resolver);
        var grammarProperties = new GrammarProperties();
        grammarProperties.setDirectory(temporary.resolve("grammars"));
        GrammarRepository grammars = new JsonGrammarRepository(grammarProperties, mapper, new LocalCanonicalDocumentStoreFactory());
        var service = new IntegrationProfileApplicationService(repository, metamodelService, checker, grammars);

        var profile = IntegrationProfileTestFixtures.emf();
        assertThatThrownBy(() -> service.create("people", profile))
                .isInstanceOf(IntegrationProfileApplicationException.class)
                .hasMessageContaining("blocking errors");
        assertThat(repository.findById(profile.id())).isEmpty();
    }
}
