package com.example.editor.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.json.JsonMapper;
import com.example.editor.scope.ResourceScope;

class CanonicalPersistenceFactoryTests {
    @TempDir
    Path temporary;

    @Test
    void localFactoryPreservesAtomicJsonStoreSemantics() {
        LocalCanonicalDocumentStoreFactory factory = new LocalCanonicalDocumentStoreFactory();
        CanonicalDocumentStore<TestDocument> store = factory.create(new CanonicalDocumentStoreSpec<>("test.documents",
                temporary, ".test.json", true, JsonMapper.builder().findAndAddModules().build(), TestDocument.class,
                TestDocument::id));

        var created = store.create(new TestDocument("document-one", "first"));
        var loaded = store.find("document-one").orElseThrow();
        var replaced = store.replace("document-one", new TestDocument("document-one", "second"), created.etag());

        assertThat(loaded.document().value()).isEqualTo("first");
        assertThat(replaced.document().value()).isEqualTo("second");
        assertThat(replaced.etag()).isNotEqualTo(created.etag());
        assertThat(store.listIds()).containsExactly("document-one");
        assertThat(store.fileName("document-one")).isEqualTo("document-one.test.json");

        store.delete("document-one", replaced.etag());
        assertThat(store.find("document-one")).isEmpty();
    }

    @Test
    void explicitResourceScopesIsolateTheSameDocumentIdFromLocalData() {
        LocalCanonicalDocumentStoreFactory factory = new LocalCanonicalDocumentStoreFactory();
        var spec = new CanonicalDocumentStoreSpec<>("test.documents", temporary, ".json", true,
                JsonMapper.builder().findAndAddModules().build(), TestDocument.class, TestDocument::id);
        CanonicalDocumentStore<TestDocument> local = factory.create(spec);
        CanonicalDocumentStore<TestDocument> alpha = factory.create(spec, new ResourceScope("alpha"));
        CanonicalDocumentStore<TestDocument> beta = factory.create(spec, new ResourceScope("beta"));
        local.create(new TestDocument("same", "local")); alpha.create(new TestDocument("same", "alpha")); beta.create(new TestDocument("same", "beta"));
        assertThat(local.find("same").orElseThrow().document().value()).isEqualTo("local");
        assertThat(alpha.find("same").orElseThrow().document().value()).isEqualTo("alpha");
        assertThat(beta.find("same").orElseThrow().document().value()).isEqualTo("beta");
        assertThat(temporary.resolve("same.json")).exists();
        assertThat(temporary.resolve("workspaces/alpha/same.json")).exists();
        assertThat(new ResourceScope("alpha").resourceKey("validation.model", "same")).isEqualTo("alpha/validation.model/same");
    }

    @Test
    void storeSpecRequiresAStableNamespace() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new CanonicalDocumentStoreSpec<>("../outside", temporary,
                ".json", true, JsonMapper.builder().build(), TestDocument.class, TestDocument::id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("namespace");
    }

    private record TestDocument(String id, String value) { }
}
