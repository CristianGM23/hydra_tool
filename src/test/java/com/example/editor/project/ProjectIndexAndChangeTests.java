package com.example.editor.project;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.project.storage.*;
import tools.jackson.databind.json.JsonMapper;

class ProjectIndexAndChangeTests {
    @TempDir Path temporary;

    @Test void persistedIndexContainsCreatedArtifacts() throws Exception {
        LocalDirectoryProjectStorage storage=created(); storage.createEntry(new ProjectEntryCreateRequest("metamodels/people.metamodel.json",false,"{}"));
        Path index=Path.of(storage.session().directory()).resolve(".modeling/project-index.json");
        assertThat(index).isRegularFile(); assertThat(Files.readString(index)).contains("metamodels/people.metamodel.json");
    }

    @Test void logicalTreeDoesNotDuplicateNestedEditorFolders() {
        LocalDirectoryProjectStorage storage=created();
        ProjectTreeNode tree=storage.tree(ProjectTreeView.LOGICAL);
        ProjectTreeNode editors=tree.children().stream().filter(node -> node.name().equals("Model editors")).findFirst().orElseThrow();
        assertThat(editors.children()).extracting(ProjectTreeNode::name).doesNotContain("representations","palettes","forms");
    }

    @Test void physicalTreeIncludesPortableDescriptorAndInternalDirectory() {
        LocalDirectoryProjectStorage storage=created();
        assertThat(storage.tree(ProjectTreeView.PHYSICAL).children()).extracting(ProjectTreeNode::name).contains("modeling-project.json",".modeling");
    }

    @Test void generatedArtifactsAreMarkedAsGenerated() {
        LocalDirectoryProjectStorage storage=created(); storage.createEntry(new ProjectEntryCreateRequest("generated/drawio/people.drawio",false,"<mxfile/>"));
        ProjectTreeNode generated=storage.tree(ProjectTreeView.LOGICAL).children().stream().filter(node -> node.name().equals("Generated")).findFirst().orElseThrow();
        ProjectTreeNode drawio=find(generated,"generated/drawio/people.drawio");
        assertThat(drawio.ownership()).isEqualTo(FileOwnership.GENERATED);
        assertThat(drawio.status()).isEqualTo(ProjectNodeStatus.GENERATED);
    }

    @Test void unchangedProjectProducesNoExternalChanges() {
        LocalDirectoryProjectStorage storage=created(); storage.pollChanges();
        assertThat(storage.pollChanges().changes()).isEmpty();
    }

    @Test void createdFileIsClassifiedInChangeSet() throws Exception {
        LocalDirectoryProjectStorage storage=created(); storage.pollChanges();
        Path validationFile=Path.of(storage.session().directory()).resolve(ProjectLayout.standard().validations()).resolve("people.validations.json");
        Files.createDirectories(validationFile.getParent());
        Files.writeString(validationFile,"{}");
        assertThat(storage.pollChanges().changes()).extracting(ProjectFileChange::artifactKind).containsExactly(ArtifactKind.VALIDATION_MODEL);
    }


    @Test void treeRejectsMoreEntriesThanConfiguredGlobalBudget() throws Exception {
        LocalProjectProperties properties=new LocalProjectProperties();
        properties.setMaxTreeEntries(3);
        Path root=temporary.resolve("bounded-tree");
        Files.createDirectories(root);
        for(int index=0;index<4;index++) Files.writeString(root.resolve("item-"+index+".txt"),"x");
        ProjectIndexService service=indexService(properties);
        assertThatThrownBy(() -> service.build(root,descriptor(),ProjectTreeView.PHYSICAL))
                .isInstanceOf(ProjectException.class)
                .hasMessageContaining("configured entry limit of 3");
    }

    @Test void incrementalIndexCannotGrowPastGlobalTreeBudget() throws Exception {
        LocalProjectProperties properties=new LocalProjectProperties();
        properties.setMaxTreeEntries(20);
        Path root=temporary.resolve("bounded-index");
        Path seed=root.resolve("seed");
        Files.createDirectories(seed);
        for(int index=0;index<15;index++) Files.writeString(seed.resolve("seed-"+index+".txt"),"seed");
        ProjectIndexService service=indexService(properties);
        ProjectDescriptor descriptor=descriptor();
        service.persist(root,descriptor);
        Path bulk=root.resolve("bulk");
        Files.createDirectories(bulk);
        for(int index=0;index<10;index++) Files.writeString(bulk.resolve("item-"+index+".txt"),"x");
        assertThatThrownBy(() -> service.persistAffected(root,descriptor,"bulk"))
                .isInstanceOf(ProjectException.class)
                .hasMessageContaining("configured entry limit of 20");
    }

    @Test void treeReadsDoNotRewritePersistentIndex() throws Exception {
        LocalDirectoryProjectStorage storage=created();
        Path index=Path.of(storage.session().directory()).resolve(".modeling/project-index.json");
        assertThat(index).isRegularFile();
        java.nio.file.attribute.FileTime sentinel=java.nio.file.attribute.FileTime.fromMillis(1_000L);
        Files.setLastModifiedTime(index,sentinel);
        String before=Files.readString(index);
        storage.tree(ProjectTreeView.LOGICAL);
        assertThat(Files.readString(index)).isEqualTo(before);
        assertThat(Files.getLastModifiedTime(index)).isEqualTo(sentinel);
    }


    private ProjectIndexService indexService(LocalProjectProperties properties) {
        ProjectFileOperations files=new ProjectFileOperations();
        SafeProjectPathResolver paths=new SafeProjectPathResolver(properties);
        return new ProjectIndexService(paths,new ProjectArtifactClassifier(),new ProjectMediaTypeResolver(),new ProjectHashService(),properties,files,JsonMapper.builder().findAndAddModules().build());
    }

    private ProjectDescriptor descriptor() {
        Instant now=Instant.parse("2026-01-01T00:00:00Z");
        return new ProjectDescriptor("1.0","bounded","Bounded","",now,now,ProjectStorageMode.LOCAL_DIRECTORY,ProjectLayout.standard(),Set.of(),Map.of());
    }

    private LocalDirectoryProjectStorage created() {
        LocalDirectoryProjectStorage storage=ProjectStorageTestFixture.storage(temporary);
        storage.create(new CreateLocalProjectRequest(temporary.toString(),"people","People","",false));
        return storage;
    }

    private ProjectTreeNode find(ProjectTreeNode node,String path) {
        if(node.relativePath().equals(path)) return node;
        return node.children().stream().map(child -> findOrNull(child,path)).filter(java.util.Objects::nonNull).findFirst().orElseThrow();
    }

    private ProjectTreeNode findOrNull(ProjectTreeNode node,String path) {
        if(node.relativePath().equals(path)) return node;
        for(ProjectTreeNode child:node.children()) { ProjectTreeNode result=findOrNull(child,path); if(result!=null) return result; }
        return null;
    }
}
