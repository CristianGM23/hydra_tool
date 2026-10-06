package com.example.editor.metamodel.application;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.config.StorageProperties;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.CanonicalMetamodelTestFixtures;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba el bloque 2 en la capa de aplicación, sin depender de HTTP.
 */
class CanonicalMetamodelApplicationServiceTests {

    @TempDir
    Path temporaryDirectory;

    private CanonicalMetamodelApplicationService service;

    @BeforeEach
    void setUp() {
        StorageProperties storage = new StorageProperties(temporaryDirectory.resolve("metamodels"), temporaryDirectory.resolve("models"), temporaryDirectory.resolve("notations"), temporaryDirectory.resolve("validations"), temporaryDirectory.resolve("quickfixes"), temporaryDirectory.resolve("generators"), temporaryDirectory.resolve("assets"), temporaryDirectory.resolve("exports"), true);
        JsonCanonicalMetamodelRepository repository = new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), JsonMapper.builder().build());
        service = new CanonicalMetamodelApplicationService(repository, new NeutralMetamodelValidator());
    }

    @Test
    void validatesBeforeCreatingAndReturnsTheAppliedReport() {
        MetamodelSaveResponse response = service.create(CanonicalMetamodelTestFixtures.valid("sample"));

        assertThat(response.metamodel().id()).isEqualTo("sample");
        assertThat(response.validation().hasErrors()).isFalse();
        assertThat(response.etag()).matches("\"[0-9a-f]{64}\"");
    }

    @Test
    void rejectsInvalidMetamodelWithoutPersistingIt() {
        assertThatThrownBy(() -> service.create(CanonicalMetamodelTestFixtures.invalid("invalid")))
            .isInstanceOf(MetamodelApplicationException.class)
            .extracting(exception -> ((MetamodelApplicationException) exception).getReason())
            .isEqualTo(MetamodelApplicationException.Reason.VALIDATION_ERROR);

        assertThatThrownBy(() -> service.findById("invalid"))
            .isInstanceOf(MetamodelApplicationException.class)
            .extracting(exception -> ((MetamodelApplicationException) exception).getReason())
            .isEqualTo(MetamodelApplicationException.Reason.NOT_FOUND);
    }

    @Test
    void rejectsDifferentPathAndBodyIdsBeforeWriting() {
        service.create(CanonicalMetamodelTestFixtures.valid("sample"));

        assertThatThrownBy(() -> service.replace("sample", CanonicalMetamodelTestFixtures.valid("other"), "*"))
            .isInstanceOf(MetamodelApplicationException.class)
            .extracting(exception -> ((MetamodelApplicationException) exception).getReason())
            .isEqualTo(MetamodelApplicationException.Reason.INVALID_REQUEST);
    }

    @Test
    void returnsNotFoundForAnUnknownIdentifier() {
        assertThatThrownBy(() -> service.findById("unknown"))
            .isInstanceOf(MetamodelApplicationException.class)
            .extracting(exception -> ((MetamodelApplicationException) exception).getReason())
            .isEqualTo(MetamodelApplicationException.Reason.NOT_FOUND);
    }

    @Test
    void replacesUsingTheCurrentEtag() {
        MetamodelSaveResponse first = service.create(CanonicalMetamodelTestFixtures.valid("sample", "First", java.util.Map.of()));
        MetamodelSaveResponse second = service.replace("sample", CanonicalMetamodelTestFixtures.valid("sample", "Second", java.util.Map.of()), first.etag());

        assertThat(second.metamodel().name()).isEqualTo("Second");
        assertThat(service.findById("sample").metamodel().name()).isEqualTo("Second");
    }

    @Test
    void reportsPreconditionFailureForAStaleEtag() {
        service.create(CanonicalMetamodelTestFixtures.valid("sample"));

        assertThatThrownBy(() -> service.replace("sample", CanonicalMetamodelTestFixtures.valid("sample", "Changed", java.util.Map.of()), "\"stale\""))
            .isInstanceOf(MetamodelApplicationException.class)
            .extracting(exception -> ((MetamodelApplicationException) exception).getReason())
            .isEqualTo(MetamodelApplicationException.Reason.PRECONDITION_FAILED);
    }

    @Test
    void listsOnlyCanonicalSummaries() {
        service.create(CanonicalMetamodelTestFixtures.valid("zeta"));
        service.create(CanonicalMetamodelTestFixtures.valid("alpha"));

        assertThat(service.findAll()).extracting(item -> item.id()).containsExactly("alpha", "zeta");
    }

    @Test
    void validatesTheStoredMetamodel() {
        service.create(CanonicalMetamodelTestFixtures.valid("sample"));

        assertThat(service.validateStored("sample").issues()).isEmpty();
    }

    @Test
    void validationCanBeUsedWithoutSaving() {
        assertThat(service.validate(CanonicalMetamodelTestFixtures.invalid("invalid")).hasErrors()).isTrue();
    }

    @Test
    void deletesOnlyTheRequestedVersion() {
        MetamodelSaveResponse stored = service.create(CanonicalMetamodelTestFixtures.valid("sample"));
        service.delete("sample", stored.etag());

        assertThatThrownBy(() -> service.findById("sample"))
            .isInstanceOf(MetamodelApplicationException.class)
            .extracting(exception -> ((MetamodelApplicationException) exception).getReason())
            .isEqualTo(MetamodelApplicationException.Reason.NOT_FOUND);
    }
}
