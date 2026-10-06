package com.example.editor.project;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProjectContractTests {
    @Test void standardLayoutSeparatesCanonicalGeneratedAndInternalFiles() {
        ProjectLayout layout=ProjectLayout.standard();
        assertThat(layout.metamodels()).isEqualTo("metamodels");
        assertThat(layout.validations()).isEqualTo("behavior/validations");
        assertThat(layout.generated()).isEqualTo("generated");
        assertThat(layout.internal()).isEqualTo(".modeling");
    }

    @Test void logicalRootsKeepStablePresentationOrder() {
        assertThat(ProjectLayout.standard().logicalRoots().keySet()).startsWith("Metamodels","Model editors","Representations","Palettes");
    }

    @Test void artifactRootsCoverCurrentAndFutureCanonicalFamilies() {
        Map<ArtifactKind,String> roots=ProjectLayout.standard().artifactRoots();
        assertThat(roots).containsEntry(ArtifactKind.METAMODEL,"metamodels")
                .containsEntry(ArtifactKind.EVENT_FLOW_MODEL,"events/flows")
                .containsEntry(ArtifactKind.EVENT_FLOW_RUNTIME,"events/flows")
                .containsEntry(ArtifactKind.EDITOR_SESSION_DIAGRAM,"models/diagrams")
                .containsEntry(ArtifactKind.GAMIFICATION_DEFINITION_MODEL,"gamification/definitions")
                .containsEntry(ArtifactKind.INTEGRATION_PROFILE,"integrations");
    }

    @Test void rejectsParentLayoutPaths() {
        assertThatThrownBy(() -> layout("../internal")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsAbsoluteLayoutPaths() {
        assertThatThrownBy(() -> layout("/absolute")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void descriptorIsPortableAndCanBeTouched() {
        Instant created=Instant.parse("2026-01-01T00:00:00Z");
        ProjectDescriptor descriptor=new ProjectDescriptor("1.0","railway","Railway","",created,created,ProjectStorageMode.LOCAL_DIRECTORY,ProjectLayout.standard(),Set.of(ProjectCapability.MODEL_EDITING),Map.of());
        assertThat(descriptor.touch(created.plusSeconds(1)).createdAt()).isEqualTo(created);
        assertThat(descriptor.touch(created.plusSeconds(1)).updatedAt()).isAfter(created);
    }

    @Test void descriptorDefensivelyCopiesCapabilitiesAndAnnotations() {
        Set<ProjectCapability> capabilities=new HashSet<>(Set.of(ProjectCapability.MODEL_EDITING));
        Map<String,String> annotations=new HashMap<>(Map.of("owner","local"));
        ProjectDescriptor descriptor=new ProjectDescriptor("1.0","railway","Railway","",null,null,null,null,capabilities,annotations);
        capabilities.clear(); annotations.clear();
        assertThat(descriptor.enabledCapabilities()).containsExactly(ProjectCapability.MODEL_EDITING);
        assertThat(descriptor.annotations()).containsEntry("owner","local");
    }

    @Test void treeNodeDefensivelyCopiesChildren() {
        List<ProjectTreeNode> children=new ArrayList<>();
        ProjectTreeNode root=new ProjectTreeNode("root","Root","",ProjectNodeType.PROJECT,null,null,null,false,0,null,null,children);
        children.add(new ProjectTreeNode("child","Child","docs",ProjectNodeType.DIRECTORY,null,null,null,false,0,null,null,List.of()));
        assertThat(root.children()).isEmpty();
    }

    @Test void synchronizationPlanDefensivelyCopiesChangesAndWarnings() {
        List<ProjectSyncFileChange> changes=new ArrayList<>();
        List<String> warnings=new ArrayList<>(List.of("warning"));
        ProjectSynchronizationPlan plan=new ProjectSynchronizationPlan(WorkspaceTransferDirection.WORKSPACE_TO_PROJECT,WorkspaceTransferMode.MERGE,changes,warnings,true);
        changes.add(new ProjectSyncFileChange("a","b","CREATE",ArtifactKind.METAMODEL,1)); warnings.clear();
        assertThat(plan.changes()).isEmpty();
        assertThat(plan.warnings()).containsExactly("warning");
    }

    @Test void configurationDefaultsAreSafeAndBounded() {
        LocalProjectProperties properties=new LocalProjectProperties();
        assertThat(properties.isAllowSymbolicLinks()).isFalse();
        assertThat(properties.getMaxEditableFileSizeBytes()).isPositive();
        assertThat(properties.getMaxArchiveEntries()).isGreaterThan(100);
        assertThat(properties.getMaxSearchResults()).isGreaterThan(10);
    }

    private ProjectLayout layout(String internal) {
        return new ProjectLayout(internal,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null);
    }
}
