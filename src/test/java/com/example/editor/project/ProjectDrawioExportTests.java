package com.example.editor.project;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.example.editor.projection.drawio.DrawioProjectionService;

class ProjectDrawioExportTests {
    private final DrawioProjectionService service=new DrawioProjectionService(List.of(
            new ProjectDescriptorDrawioExporter(),new ProjectTreeDrawioExporter(),new ProjectSynchronizationPlanDrawioExporter()));

    @Test void registersAllProjectExportersWithoutChangingCentralService() {
        assertThat(service.artifactTypes()).containsExactly("project-descriptor","project-sync-plan","project-tree");
    }

    @Test void exportsDescriptorCapabilitiesAndLayout() {
        ProjectDescriptor descriptor=new ProjectDescriptor("1.0","railway","Railway","",Instant.now(),Instant.now(),ProjectStorageMode.LOCAL_DIRECTORY,
                ProjectLayout.standard(),Set.of(ProjectCapability.EVENTS,ProjectCapability.DRAWIO_EXPORT),Map.of());
        String xml=service.export("project-descriptor",descriptor);
        assertThat(xml).contains("<mxfile").contains("Railway").contains("EVENTS").contains("Metamodels");
    }

    @Test void exportsLogicalTreeAndArtifactKinds() {
        ProjectTreeNode file=new ProjectTreeNode("file","people.metamodel.json","metamodels/people.metamodel.json",ProjectNodeType.FILE,ArtifactKind.METAMODEL,
                FileOwnership.USER_MANAGED,ProjectNodeStatus.NORMAL,true,10,Instant.now(),"etag",List.of());
        ProjectTreeNode root=new ProjectTreeNode("project","People","",ProjectNodeType.PROJECT,ArtifactKind.PROJECT_DESCRIPTOR,
                FileOwnership.OWNED,ProjectNodeStatus.NORMAL,false,0,Instant.now(),"",List.of(file));
        assertThat(service.export("project-tree",root)).contains("people.metamodel.json").contains("METAMODEL");
    }

    @Test void exportsSynchronizationOperationsAndDestinations() {
        ProjectSynchronizationPlan plan=new ProjectSynchronizationPlan(WorkspaceTransferDirection.WORKSPACE_TO_PROJECT,WorkspaceTransferMode.REPLACE,
                List.of(new ProjectSyncFileChange("source","metamodels/people.metamodel.json","REPLACE",ArtifactKind.METAMODEL,100)),List.of(),true);
        assertThat(service.export("project-sync-plan",plan)).contains("REPLACE").contains("metamodels/people.metamodel.json").contains("Local project");
    }

    @Test void rejectsWrongSourceType() {
        assertThatThrownBy(() -> service.export("project-tree",ProjectLayout.standard())).isInstanceOf(IllegalArgumentException.class);
    }
}
