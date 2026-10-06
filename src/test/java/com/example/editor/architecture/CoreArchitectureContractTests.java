package com.example.editor.architecture;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import com.example.editor.model.ModelDocumentService;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Contratos estáticos de la arquitectura base. */
class CoreArchitectureContractTests {

    @Test
    void modelDocumentServiceDoesNotSerializeAllSessionsWithSynchronizedPublicMethods() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/example/editor/model/ModelDocumentService.java"));
        assertThat(source).doesNotContain("public synchronized");
        assertThat(source).contains("ModelSessionManager sessionManager");
    }

    @Test
    void updateSiteMultipartEndpointsStageUploadsInsteadOfCallingGetBytes() throws Exception {
        String controller = Files.readString(Path.of(
                "src/main/java/com/example/editor/metamodel/importing/updatesite/UpdateSiteImportController.java"));
        assertThat(controller).contains("withStaged(files");
        assertThat(controller).contains("withStaged(List.of(file)");
        assertThat(controller).doesNotContain("file.getBytes()");
    }

    @Test
    void updateSiteArchiveReaderUsesBlobDescriptorsInsteadOfMaterializingEveryEntry() throws Exception {
        String reader = Files.readString(Path.of(
                "src/main/java/com/example/editor/metamodel/importing/updatesite/UpdateSiteArchiveReader.java"));
        assertThat(reader).contains("Map<String, UpdateSiteStoredArtifact>");
        assertThat(reader).contains("blobStore.store");
        assertThat(reader).doesNotContain("ByteArrayOutputStream");
        assertThat(reader).doesNotContain("Map<String, byte[]>");
    }

    @Test
    void p2InventoryUsesArtifactMetadataWithoutReadingAllPayloads() throws Exception {
        String analyzer = Files.readString(Path.of(
                "src/main/java/com/example/editor/metamodel/importing/updatesite/P2BundleAnalyzer.java"));
        assertThat(analyzer).contains("repository.artifacts()");
        assertThat(analyzer).doesNotContain("repository.files().entrySet()");
    }
    @Test
    void modelDocumentServiceKeepsTheReviewedPublicApiSurface() {
        Set<String> actual = Arrays.stream(ModelDocumentService.class.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                .map(method -> method.getName() + "(" + Arrays.stream(method.getParameterTypes())
                        .map(Class::getSimpleName).collect(Collectors.joining(",")) + ")")
                .collect(Collectors.toSet());
        assertThat(actual).containsExactlyInAnyOrder(
                "create(String,String)", "find(String)", "addElement(String,String,String,String)",
                "setAttribute(String,String,String,String)", "clearAttribute(String,String,String)",
                "setReference(String,String,String,String)", "removeReference(String,String,String,String)",
                "snapshot(String)", "restore(String,ModelSnapshot)", "save(String)",
                "open(String,String)", "listSavedModels()", "deleteElement(String,String)",
                "deleteElements(String,List)", "readSavedModel(String)", "deleteSavedModel(String)");
    }

}
