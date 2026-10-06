package com.example.editor.integration.persistence;
import static org.assertj.core.api.Assertions.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.integration.IntegrationProfileProperties;
import com.example.editor.integration.IntegrationProfileTestFixtures;
import com.example.editor.validation.persistence.CanonicalDocumentStoreException;
import tools.jackson.databind.json.JsonMapper;
class JsonIntegrationProfileRepositoryTests {
    @TempDir Path temporary;
    @Test void roundTripsProfilesAndEtags() {
        var repository=repository(); var stored=repository.create(IntegrationProfileTestFixtures.plantUml());
        assertThat(repository.findById(stored.document().id())).isPresent().get().extracting(value -> value.document().target().technology()).isEqualTo(stored.document().target().technology());
        assertThat(repository.fileName(stored.document().id())).endsWith(".integration.json");
    }
    @Test void rejectsStaleReplacement() {
        var repository=repository(); var stored=repository.create(IntegrationProfileTestFixtures.emf());
        assertThatThrownBy(() -> repository.replace(stored.document().id(),stored.document(),"stale")).isInstanceOf(CanonicalDocumentStoreException.class);
    }
    @Test void deletesOnlyTheRequestedProfile() {
        var repository=repository(); var one=repository.create(IntegrationProfileTestFixtures.plantUml()); var two=repository.create(IntegrationProfileTestFixtures.emf());
        repository.delete(one.document().id(),one.etag());
        assertThat(repository.exists(one.document().id())).isFalse(); assertThat(repository.exists(two.document().id())).isTrue();
    }
    @Test void createEtagMatchesThePersistedBytesForSetValuedProfiles() {
        var repository=repository(); var created=repository.create(IntegrationProfileTestFixtures.plantUml());
        var loaded=repository.findById(created.document().id()).orElseThrow();
        assertThat(loaded.etag()).isEqualTo(created.etag());
    }
    @Test void replacesASetValuedProfileUsingTheCreateEtag() {
        var repository=repository(); var created=repository.create(IntegrationProfileTestFixtures.plantUml());
        var replacement=IntegrationProfileTestFixtures.plantUml();
        var replaced=repository.replace(created.document().id(),replacement,created.etag());
        assertThat(replaced.document()).isEqualTo(replacement);
    }

    @Test void deletesASetValuedProfileUsingTheCreateEtag() {
        var repository=repository(); var created=repository.create(IntegrationProfileTestFixtures.plantUml());
        repository.delete(created.document().id(),created.etag());
        assertThat(repository.findById(created.document().id())).isEmpty();
    }

    private JsonIntegrationProfileRepository repository(){var properties=new IntegrationProfileProperties();properties.setDirectory(temporary);return new JsonIntegrationProfileRepository(properties,JsonMapper.builder().findAndAddModules().build());}
}
