package com.example.editor.project;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.project.storage.LocalDirectoryProjectStorage;

class LocalDirectoryProjectStorageTests {
    @TempDir Path temporary;
    private LocalDirectoryProjectStorage storage;
    @BeforeEach void setUp() { storage=ProjectStorageTestFixture.storage(temporary); }
    private ProjectSession create() { return storage.create(new CreateLocalProjectRequest(temporary.toString(),"railway","Railway","Demo",false)); }

    @Test void createsCleanPortableStructureAndDefersSpecializedFolders() throws Exception {
        ProjectSession session=create(); Path root=Path.of(session.directory());
        assertThat(root.resolve("modeling-project.json")).isRegularFile();
        assertThat(root.resolve("README.md")).isRegularFile();
        assertThat(root.resolve(".gitignore")).isRegularFile();
        assertThat(root.resolve("metamodels")).isDirectory();
        assertThat(root.resolve("editors")).isDirectory();
        assertThat(root.resolve("behavior")).isDirectory();
        assertThat(root.resolve("events")).isDirectory();
        assertThat(root.resolve("gamification")).isDirectory();
        assertThat(root.resolve("models")).isDirectory();
        assertThat(root.resolve("integrations")).isDirectory();
        assertThat(root.resolve("assets")).isDirectory();
        assertThat(root.resolve("generated")).isDirectory();
        assertThat(root.resolve("tests")).isDirectory();
        assertThat(root.resolve("docs")).isDirectory();
        assertThat(root.resolve("behavior/validations")).doesNotExist();
        assertThat(root.resolve("events/flows")).doesNotExist();
        assertThat(Files.readString(root.resolve("README.md"))).contains("## Estructura","editors/","generated/");
        storage.createEntry(new ProjectEntryCreateRequest("behavior/validations/quality.json",false,"{}"));
        assertThat(root.resolve("behavior/validations/quality.json")).isRegularFile();
    }
    @Test void listsLogicalAndPhysicalTrees() {
        create();
        assertThat(storage.tree(ProjectTreeView.LOGICAL).children()).extracting(ProjectTreeNode::name).contains("Metamodels","Validations","Generated");
        assertThat(storage.tree(ProjectTreeView.PHYSICAL).children()).extracting(ProjectTreeNode::name).contains("metamodels","modeling-project.json");
    }
    @Test void createsReadsAndWritesFileWithEtag() {
        create(); storage.createEntry(new ProjectEntryCreateRequest("docs/note.md",false,"first"));
        ProjectFileContent loaded=storage.read("docs/note.md");
        storage.write("docs/note.md",new ProjectFileWriteRequest("second",loaded.etag(),false));
        assertThat(storage.read("docs/note.md").content()).isEqualTo("second");
    }
    @Test void rejectsStaleEtag() {
        create(); storage.createEntry(new ProjectEntryCreateRequest("docs/note.md",false,"first"));
        assertThatThrownBy(() -> storage.write("docs/note.md",new ProjectFileWriteRequest("second","\"stale\"",false)))
                .isInstanceOf(ProjectException.class).extracting("reason").isEqualTo(ProjectException.Reason.PRECONDITION_FAILED);
    }
    @Test void renamesDuplicatesAndDeletesEntries() {
        create(); storage.createEntry(new ProjectEntryCreateRequest("docs/note.md",false,"first"));
        String etag=storage.read("docs/note.md").etag(); storage.move(new ProjectMoveRequest("docs/note.md","docs/renamed.md",etag));
        storage.duplicate(new ProjectDuplicateRequest("docs/renamed.md","docs/copy.md"));
        assertThat(storage.read("docs/copy.md").content()).isEqualTo("first");
        storage.delete("docs/copy.md",storage.read("docs/copy.md").etag());
        assertThatThrownBy(() -> storage.read("docs/copy.md")).isInstanceOf(ProjectException.class);
    }
    @Test void protectsDescriptorAndInternalFiles() {
        create();
        assertThatThrownBy(() -> storage.delete("modeling-project.json",storage.read("modeling-project.json").etag())).isInstanceOf(ProjectException.class);
        assertThatThrownBy(() -> storage.delete(".modeling","")).isInstanceOf(ProjectException.class);
    }
    @Test void exportsAndImportsProjectArchive() throws Exception {
        create(); storage.createEntry(new ProjectEntryCreateRequest("docs/note.md",false,"portable")); byte[] archive=storage.exportArchive(); storage.close();
        ProjectSession imported=storage.importArchive(temporary.resolve("imports").toString(),"Imported",archive);
        assertThat(imported.active()).isTrue(); assertThat(storage.read("docs/note.md").content()).isEqualTo("portable");
    }
    @Test void importsCompleteProjectDirectoryWithoutModifyingTheSource() throws Exception {
        ProjectSession source=create();
        Path sourceRoot=Path.of(source.directory());
        storage.createEntry(new ProjectEntryCreateRequest("docs/source.md",false,"source content"));
        storage.close();
        Path destinationParent=temporary.resolve("directory-imports");
        ProjectSession imported=storage.importDirectory(new ImportLocalProjectDirectoryRequest(sourceRoot.toString(),destinationParent.toString(),"RailwayCopy"));
        assertThat(imported.active()).isTrue();
        assertThat(storage.read("docs/source.md").content()).isEqualTo("source content");
        assertThat(Files.readString(sourceRoot.resolve("docs/source.md"))).isEqualTo("source content");
        assertThat(Path.of(imported.directory())).isEqualTo(destinationParent.resolve("RailwayCopy").toAbsolutePath().normalize());
    }

    @Test void rejectsImportingProjectInsideItsOwnSourceDirectory() {
        ProjectSession source=create();
        assertThatThrownBy(() -> storage.importDirectory(new ImportLocalProjectDirectoryRequest(source.directory(),source.directory(),"nested-copy")))
                .isInstanceOf(ProjectException.class).extracting("reason").isEqualTo(ProjectException.Reason.INVALID_REQUEST);
    }

    @Test void detectsExternalFileChanges() throws Exception {
        ProjectSession session=create(); storage.pollChanges();
        Files.writeString(Path.of(session.directory()).resolve("docs/external.md"),"external");
        assertThat(storage.pollChanges().changes()).extracting(ProjectFileChange::type).contains(ProjectFileChangeType.CREATED);
    }
    @Test void restoresOpenTabs() {
        ProjectSession created=create(); storage.saveTabs(new ProjectTabSessionRequest(java.util.List.of("README.md"))); storage.close();
        ProjectSession opened=storage.open(new OpenLocalProjectRequest(created.directory(),false,WorkspaceTransferMode.MERGE));
        assertThat(opened.restoredTabs()).containsExactly("README.md");
    }
    @Test void storesRecentProjectWithoutRequiringGlobalIds() { create(); assertThat(storage.recentProjects()).extracting(ProjectSummary::name).containsExactly("Railway"); }

    @Test void protectsManagedRootDirectories() {
        create();
        assertThatThrownBy(() -> storage.delete("metamodels","")).isInstanceOf(ProjectException.class)
                .extracting("reason").isEqualTo(ProjectException.Reason.INVALID_REQUEST);
        assertThatThrownBy(() -> storage.delete("behavior",""))
                .isInstanceOf(ProjectException.class).extracting("reason").isEqualTo(ProjectException.Reason.INVALID_REQUEST);
    }

    @Test void searchesFileNamesAndTextContent() {
        create(); storage.createEntry(new ProjectEntryCreateRequest("docs/architecture.md",false,"Canonical project storage"));
        ProjectSearchResult result=storage.search("canonical");
        assertThat(result.matches()).extracting(ProjectSearchMatch::relativePath).contains("docs/architecture.md");
    }

    @Test void resolvesAbsolutePathWithoutExposingOtherFiles() {
        ProjectSession session=create(); storage.createEntry(new ProjectEntryCreateRequest("docs/note.md",false,"text"));
        ProjectResolvedPath result=storage.resolvePath("docs/note.md");
        assertThat(result.exists()).isTrue();
        assertThat(Path.of(result.absolutePath())).startsWith(Path.of(session.directory()));
    }

    @Test void uploadsAndDownloadsBinaryFiles() {
        create(); byte[] content=new byte[]{1,2,3,4};
        storage.upload("assets","sample.bin",content);
        assertThat(storage.download("assets/sample.bin")).containsExactly(content);
        assertThat(storage.read("assets/sample.bin").binary()).isTrue();
    }

    @Test void rejectsDuplicateProjectDirectory() {
        create();
        assertThatThrownBy(this::create).isInstanceOf(ProjectException.class)
                .extracting("reason").isEqualTo(ProjectException.Reason.CONFLICT);
    }

    @Test void disabledModuleRejectsNewProjects() {
        LocalDirectoryProjectStorage disabled=ProjectStorageTestFixture.storage(temporary.resolve("disabled"),properties -> properties.setEnabled(false));
        assertThatThrownBy(() -> disabled.create(new CreateLocalProjectRequest(temporary.toString(),"disabled","Disabled","",false)))
                .isInstanceOf(ProjectException.class).extracting("reason").isEqualTo(ProjectException.Reason.DISABLED);
    }

    @Test void reportsModifiedAndDeletedExternalFiles() throws Exception {
        ProjectSession session=create(); storage.createEntry(new ProjectEntryCreateRequest("docs/change.md",false,"one")); storage.pollChanges();
        Path file=Path.of(session.directory()).resolve("docs/change.md"); Files.writeString(file,"two");
        assertThat(storage.pollChanges().changes()).extracting(ProjectFileChange::type).contains(ProjectFileChangeType.MODIFIED);
        Files.delete(file);
        assertThat(storage.pollChanges().changes()).extracting(ProjectFileChange::type).contains(ProjectFileChangeType.DELETED);
    }

    @Test void searchResultsRespectConfiguredLimit() {
        LocalDirectoryProjectStorage limited=ProjectStorageTestFixture.storage(temporary.resolve("limited"),properties -> properties.setMaxSearchResults(2));
        limited.create(new CreateLocalProjectRequest(temporary.toString(),"limited","Limited","",false));
        limited.createEntry(new ProjectEntryCreateRequest("docs/repeated.md",false,"needle\nneedle\nneedle"));
        assertThat(limited.search("needle").matches()).hasSize(2);
        assertThat(limited.search("needle").truncated()).isTrue();
    }
}
