package com.example.editor.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.project.storage.ProjectDeletionService;
import com.example.editor.project.storage.ProjectFileOperations;
import com.example.editor.project.storage.ProjectRegistryStore;
import com.example.editor.project.storage.SafeProjectPathResolver;
import tools.jackson.databind.json.JsonMapper;

class ProjectDeletionServiceTests {
    @TempDir Path temporary;
    private ProjectApplicationService projects;
    private ProjectDeletionService deletion;

    @BeforeEach
    void setUp() {
        projects = new ProjectApplicationService(ProjectStorageTestFixture.storage(temporary));
        LocalProjectProperties properties = new LocalProjectProperties();
        properties.setRegistryDirectory(temporary.resolve("registry"));
        ProjectFileOperations files = new ProjectFileOperations();
        deletion = new ProjectDeletionService(
                projects,
                new SafeProjectPathResolver(properties),
                files,
                new ProjectRegistryStore(properties, JsonMapper.builder().findAndAddModules().build(), files));
    }

    @Test
    void requiresExplicitDependencyConfirmationBeforeRemovingCompleteProject() {
        ProjectSession session = projects.create(new CreateLocalProjectRequest(temporary.toString(), "full-delete", "Full delete", "", false));
        projects.createEntry(new ProjectEntryCreateRequest("docs/note.md", false, "keep until confirmed"));

        ProjectDeletionImpact impact = deletion.analyzeActiveProject();
        assertThat(impact.projectId()).isEqualTo("full-delete");
        assertThat(impact.dependencies()).isNotEmpty();
        assertThat(impact.dependencies()).extracting(ProjectDeletionDependency::path).contains("docs");

        assertThatThrownBy(() -> deletion.deleteActiveProject(false))
                .isInstanceOf(ProjectException.class)
                .extracting("reason").isEqualTo(ProjectException.Reason.CONFLICT);
        assertThat(Path.of(session.directory())).exists();
        assertThat(projects.session().active()).isTrue();
    }

    @Test
    void confirmedDeletionClosesSessionDeletesRootAndRemovesRecentEntry() {
        ProjectSession session = projects.create(new CreateLocalProjectRequest(temporary.toString(), "full-delete", "Full delete", "", false));
        projects.createEntry(new ProjectEntryCreateRequest("docs/note.md", false, "delete me"));
        Path root = Path.of(session.directory());

        ProjectDeletionResult result = deletion.deleteActiveProject(true);

        assertThat(root).doesNotExist();
        assertThat(projects.session().active()).isFalse();
        assertThat(projects.recentProjects()).extracting(ProjectSummary::id).doesNotContain("full-delete");
        assertThat(result.projectId()).isEqualTo("full-delete");
        assertThat(result.deletedEntries()).isPositive();
        assertThat(result.deletedBytes()).isPositive();
    }

    @Test
    void refusesToDeleteDirectoryThatNoLongerHasHydraDescriptor() throws Exception {
        ProjectSession session = projects.create(new CreateLocalProjectRequest(temporary.toString(), "guarded", "Guarded", "", false));
        Files.delete(Path.of(session.directory()).resolve("modeling-project.json"));

        assertThatThrownBy(deletion::analyzeActiveProject)
                .isInstanceOf(ProjectException.class)
                .extracting("reason").isEqualTo(ProjectException.Reason.INVALID_REQUEST);
        assertThat(Path.of(session.directory())).exists();
    }
}
