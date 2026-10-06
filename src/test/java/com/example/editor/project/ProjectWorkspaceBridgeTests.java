package com.example.editor.project;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.config.StorageProperties;
import com.example.editor.definition.EditorDefinitionProperties;
import com.example.editor.definition.runtime.session.EditorSessionProperties;
import com.example.editor.event.EventModelProperties;
import com.example.editor.integration.IntegrationProfileProperties;
import com.example.editor.project.storage.*;

class ProjectWorkspaceBridgeTests {
    @TempDir Path temporary;
    private ProjectWorkspaceBridge bridge;
    private ProjectDescriptor descriptor;
    private Path project;
    private Path metamodels;
    private Path editors;

    @BeforeEach void setUp() throws Exception {
        metamodels=temporary.resolve("configured/metamodels"); editors=temporary.resolve("configured/editors");
        StorageProperties storage=new StorageProperties(metamodels,temporary.resolve("configured/models"),temporary.resolve("configured/notations"),temporary.resolve("configured/validations"),temporary.resolve("configured/quickfixes"),temporary.resolve("configured/generators"),temporary.resolve("configured/assets"),temporary.resolve("configured/exports"),true);
        EditorDefinitionProperties editorProperties=new EditorDefinitionProperties(); editorProperties.setDirectory(editors);
        EditorSessionProperties sessionProperties=new EditorSessionProperties(); sessionProperties.setDirectory(temporary.resolve("configured/editor-sessions"));
        EventModelProperties eventProperties=new EventModelProperties(); eventProperties.setDirectory(temporary.resolve("configured/events"));
        IntegrationProfileProperties integrationProperties=new IntegrationProfileProperties(); integrationProperties.setDirectory(temporary.resolve("configured/integrations"));
        ProjectWorkspaceContributionRegistry registry=new ProjectWorkspaceContributionRegistry(java.util.List.of(
                new CoreProjectWorkspaceContributor(storage),new EditorProjectWorkspaceContributor(editorProperties,sessionProperties),
                new EventProjectWorkspaceContributor(eventProperties),new IntegrationProjectWorkspaceContributor(integrationProperties)));
        bridge=new ProjectWorkspaceBridge(registry,new ProjectArtifactClassifier(),new ProjectFileOperations(),new LocalProjectProperties());
        descriptor=new ProjectDescriptor("1.0","people","People","",Instant.now(),Instant.now(),ProjectStorageMode.LOCAL_DIRECTORY,ProjectLayout.standard(),EnumSet.allOf(ProjectCapability.class),Map.of());
        project=temporary.resolve("project"); Files.createDirectories(project);
    }

    @Test void previewsWorkspaceToProjectWithoutWriting() throws Exception {
        Files.createDirectories(metamodels); Files.writeString(metamodels.resolve("people.metamodel.json"),"{}");
        ProjectSynchronizationPlan plan=bridge.preview(project,descriptor,new ProjectSyncRequest(WorkspaceTransferDirection.WORKSPACE_TO_PROJECT,WorkspaceTransferMode.MERGE));
        assertThat(plan.changes()).extracting(ProjectSyncFileChange::operation).containsExactly("CREATE");
        assertThat(project.resolve("metamodels/people.metamodel.json")).doesNotExist();
    }

    @Test void appliesWorkspaceToProjectUsingCanonicalFolder() throws Exception {
        Files.createDirectories(metamodels); Files.writeString(metamodels.resolve("people.metamodel.json"),"source");
        bridge.apply(project,descriptor,new ProjectSyncRequest(WorkspaceTransferDirection.WORKSPACE_TO_PROJECT,WorkspaceTransferMode.REPLACE));
        assertThat(project.resolve("metamodels/people.metamodel.json")).hasContent("source");
    }

    @Test void mergeSkipsExistingProjectFile() throws Exception {
        Files.createDirectories(metamodels); Files.writeString(metamodels.resolve("people.metamodel.json"),"source");
        Files.createDirectories(project.resolve("metamodels")); Files.writeString(project.resolve("metamodels/people.metamodel.json"),"project");
        ProjectSynchronizationResult result=bridge.apply(project,descriptor,new ProjectSyncRequest(WorkspaceTransferDirection.WORKSPACE_TO_PROJECT,WorkspaceTransferMode.MERGE));
        assertThat(result.appliedChanges()).extracting(ProjectSyncFileChange::operation).containsExactly("SKIP");
        assertThat(project.resolve("metamodels/people.metamodel.json")).hasContent("project");
    }

    @Test void replaceOverwritesExistingProjectFile() throws Exception {
        Files.createDirectories(metamodels); Files.writeString(metamodels.resolve("people.metamodel.json"),"source");
        Files.createDirectories(project.resolve("metamodels")); Files.writeString(project.resolve("metamodels/people.metamodel.json"),"project");
        bridge.apply(project,descriptor,new ProjectSyncRequest(WorkspaceTransferDirection.WORKSPACE_TO_PROJECT,WorkspaceTransferMode.REPLACE));
        assertThat(project.resolve("metamodels/people.metamodel.json")).hasContent("source");
    }

    @Test void projectToWorkspaceRoutesEditorsToEditorRepository() throws Exception {
        Files.createDirectories(project.resolve("editors")); Files.writeString(project.resolve("editors/people.editor.json"),"editor");
        bridge.apply(project,descriptor,new ProjectSyncRequest(WorkspaceTransferDirection.PROJECT_TO_WORKSPACE,WorkspaceTransferMode.REPLACE));
        assertThat(editors.resolve("people.editor.json")).hasContent("editor");
    }

    @Test void projectToWorkspaceIgnoresInternalAndUnknownFiles() throws Exception {
        Files.createDirectories(project.resolve(".modeling")); Files.writeString(project.resolve(".modeling/session.json"),"{}"); Files.writeString(project.resolve("unknown.bin"),"unknown");
        ProjectSynchronizationResult result=bridge.apply(project,descriptor,new ProjectSyncRequest(WorkspaceTransferDirection.PROJECT_TO_WORKSPACE,WorkspaceTransferMode.REPLACE));
        assertThat(result.appliedChanges()).isEmpty();
    }

    @Test void projectToWorkspacePreviewWarnsAboutUnsavedMemoryState() {
        ProjectSynchronizationPlan plan=bridge.preview(project,descriptor,new ProjectSyncRequest(WorkspaceTransferDirection.PROJECT_TO_WORKSPACE,WorkspaceTransferMode.MERGE));
        assertThat(plan.warnings()).singleElement().asString().contains("unsaved in-memory changes");
    }
    @Test void projectToWorkspacePreservesNestedArtifactPaths() throws Exception {
        Path nested=project.resolve("generated/java/com/example"); Files.createDirectories(nested); Files.writeString(nested.resolve("Person.java"),"class Person {}");
        bridge.apply(project,descriptor,new ProjectSyncRequest(WorkspaceTransferDirection.PROJECT_TO_WORKSPACE,WorkspaceTransferMode.REPLACE));
        assertThat(temporary.resolve("configured/exports/java/com/example/Person.java")).hasContent("class Person {}");
    }

    @Test void rejectsSymbolicLinksDuringSynchronizationWhenSupported() throws Exception {
        Files.createDirectories(metamodels); Path outside=temporary.resolve("outside.metamodel.json"); Files.writeString(outside,"{}");
        Path link=metamodels.resolve("linked.metamodel.json");
        try { Files.createSymbolicLink(link,outside); }
        catch(UnsupportedOperationException|java.nio.file.FileSystemException exception) { return; }
        assertThatThrownBy(() -> bridge.preview(project,descriptor,new ProjectSyncRequest(WorkspaceTransferDirection.WORKSPACE_TO_PROJECT,WorkspaceTransferMode.MERGE)))
                .isInstanceOf(ProjectException.class).extracting("reason").isEqualTo(ProjectException.Reason.INVALID_REQUEST);
    }

    @Test void rejectsSynchronizationBeyondConfiguredEntryBudget() throws Exception {
        LocalProjectProperties limited=new LocalProjectProperties(); limited.setMaxArchiveEntries(1);
        StorageProperties storage=new StorageProperties(metamodels,temporary.resolve("configured/models"),temporary.resolve("configured/notations"),temporary.resolve("configured/validations"),temporary.resolve("configured/quickfixes"),temporary.resolve("configured/generators"),temporary.resolve("configured/assets"),temporary.resolve("configured/exports"),true);
        ProjectWorkspaceContributionRegistry registry=new ProjectWorkspaceContributionRegistry(java.util.List.of(new CoreProjectWorkspaceContributor(storage)));
        ProjectWorkspaceBridge limitedBridge=new ProjectWorkspaceBridge(registry,new ProjectArtifactClassifier(),new ProjectFileOperations(),limited);
        Files.createDirectories(metamodels); Files.writeString(metamodels.resolve("a.metamodel.json"),"{}"); Files.writeString(metamodels.resolve("b.metamodel.json"),"{}");
        assertThatThrownBy(() -> limitedBridge.preview(project,descriptor,new ProjectSyncRequest(WorkspaceTransferDirection.WORKSPACE_TO_PROJECT,WorkspaceTransferMode.MERGE)))
                .isInstanceOf(ProjectException.class).extracting("reason").isEqualTo(ProjectException.Reason.FILE_TOO_LARGE);
    }

}
