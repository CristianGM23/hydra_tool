package com.example.editor.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.config.StorageProperties;
import com.example.editor.integration.roundtrip.RoundTripSafePaths;
import com.example.editor.project.storage.*;

/**
 * SEC-WIN-JUNCTION: the policy "no symbolic links inside a project, a synchronization or a round-trip target" is written with
 * Files.isSymbolicLink, which is false for a Windows directory junction (mklink /J, created without any privilege), while a walk enters it
 * like any directory. The symbolic-link tests of these classes return early on Windows (creating a symbolic link needs a privilege), so
 * the policy was never exercised there. The oracle is where the bytes end up: nothing outside the project or the workspace is read into
 * HYDRA or written by it.
 */
class WindowsJunctionBoundaryTests {
    @TempDir Path temporary;
    private ProjectWorkspaceBridge bridge;
    private ProjectDescriptor descriptor;
    private Path project;
    private Path metamodels;
    private Path outside;

    @BeforeEach void setUp() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win"), "directory junctions are a Windows feature");
        metamodels=temporary.resolve("configured/metamodels");
        StorageProperties storage=new StorageProperties(metamodels,temporary.resolve("configured/models"),temporary.resolve("configured/notations"),temporary.resolve("configured/validations"),temporary.resolve("configured/quickfixes"),temporary.resolve("configured/generators"),temporary.resolve("configured/assets"),temporary.resolve("configured/exports"),true);
        ProjectWorkspaceContributionRegistry registry=new ProjectWorkspaceContributionRegistry(List.of(new CoreProjectWorkspaceContributor(storage)));
        bridge=new ProjectWorkspaceBridge(registry,new ProjectArtifactClassifier(),new ProjectFileOperations(),new LocalProjectProperties());
        descriptor=new ProjectDescriptor("1.0","people","People","",Instant.now(),Instant.now(),ProjectStorageMode.LOCAL_DIRECTORY,ProjectLayout.standard(),EnumSet.allOf(ProjectCapability.class),Map.of());
        project=temporary.resolve("project"); Files.createDirectories(project);
        outside=temporary.resolve("outside"); Files.createDirectories(outside);
    }

    @Test void aJunctionInsideAProjectDoesNotBringOutsideFilesIntoTheWorkspace() throws Exception {
        Files.writeString(outside.resolve("secret.metamodel.json"),"{\"outside\":true}");
        junction(project.resolve("metamodels"),outside);

        Throwable failure=catchThrowable(() -> bridge.apply(project,descriptor,new ProjectSyncRequest(WorkspaceTransferDirection.PROJECT_TO_WORKSPACE,WorkspaceTransferMode.REPLACE)));

        assertThat(metamodels.resolve("secret.metamodel.json")).as("a file outside the project was copied into the workspace").doesNotExist();
        assertThat(failure).as("the junction is refused like a symbolic link").isInstanceOf(ProjectException.class);
    }

    @Test void aJunctionInsideAProjectDoesNotLetTheWorkspaceWriteOutsideIt() throws Exception {
        Files.createDirectories(metamodels); Files.writeString(metamodels.resolve("people.metamodel.json"),"{}");
        junction(project.resolve("metamodels"),outside);

        Throwable failure=catchThrowable(() -> bridge.apply(project,descriptor,new ProjectSyncRequest(WorkspaceTransferDirection.WORKSPACE_TO_PROJECT,WorkspaceTransferMode.REPLACE)));

        assertThat(outside.resolve("people.metamodel.json")).as("HYDRA wrote outside the project through the junction").doesNotExist();
        assertThat(failure).as("the junction is refused like a symbolic link").isInstanceOf(ProjectException.class);
    }

    @Test void aJunctionInsideARoundTripTargetIsRefusedLikeASymbolicLink() throws Exception {
        Path root=temporary.resolve("round-trip"); Files.createDirectories(root);
        junction(root.resolve("linked"),outside);

        Throwable failure=catchThrowable(() -> RoundTripSafePaths.resolve(root,"linked/A.txt"));

        assertThat(failure).as("linked/A.txt resolves to " + outside.resolve("A.txt") + " outside the round-trip root").isNotNull();
    }

    @Test void deletingADirectoryThatContainsAJunctionNeverDeletesTheFilesItPointsTo() throws Exception {
        Files.writeString(outside.resolve("keep.txt"),"outside data");
        Path doomed=temporary.resolve("doomed"); Files.createDirectories(doomed);
        Files.writeString(doomed.resolve("own.txt"),"own");
        junction(doomed.resolve("linked"),outside);

        catchThrowable(() -> new ProjectFileOperations().deleteRecursively(doomed));

        assertThat(outside.resolve("keep.txt")).as("deleting a project directory deleted a file outside it through a junction").exists();
    }

    @Test void aPortableProjectArchiveNeverContainsFilesOutsideTheProject() throws Exception {
        Files.writeString(outside.resolve("secret.txt"),"outside data");
        Files.writeString(project.resolve("modeling-project.json"),"{}");
        junction(project.resolve("linked"),outside);

        byte[] archive=null;
        try { archive=new ProjectZipService(new LocalProjectProperties(),new ProjectFileOperations()).exportProject(project); }
        catch(ProjectException refused) { /* refusing the junction is the expected outcome */ }

        if(archive!=null) {
            try(java.util.zip.ZipInputStream zip=new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(archive))) {
                for(java.util.zip.ZipEntry entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry())
                    assertThat(entry.getName()).as("the archive carries a file read outside the project").doesNotContain("secret.txt");
            }
        }
    }

    /**
     * TOCTOU: the walk checks a directory once, when it enters it, and then deletes each entry by name. Windows lets a directory that is
     * being enumerated be renamed, so another process can put a junction in its place after the check; every remaining name is then
     * resolved through the junction (probe resultados/sondas/toctou: 50 of 50 names resolved outside after the swap).
     */
    @Test void swappingADirectoryForAJunctionWhileItIsBeingDeletedNeverDeletesTheFilesItPointsTo() throws Exception {
        Path doomed=temporary.resolve("doomed-race"); Path inner=doomed.resolve("A"); Files.createDirectories(inner);
        Path target=temporary.resolve("outside-race"); Files.createDirectories(target);
        int count=3000;
        for(int i=0;i<count;i++){String name="f%04d.txt".formatted(i);Files.writeString(inner.resolve(name),"inside");Files.writeString(target.resolve(name),"outside data");}
        Path staged=temporary.resolve("staged-junction"); junction(staged,target);
        Path first=inner.resolve("f0000.txt");
        String[] swap={"not attempted"};
        Thread racer=new Thread(() -> {
            try {
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);
                while(Files.exists(first)&&System.nanoTime()<deadline) Thread.onSpinWait();
                Files.move(inner,temporary.resolve("A-moved"));
                Files.move(staged,inner);
                swap[0]="swapped";
            } catch(Exception exception) { swap[0]="swap failed: "+exception; }
        });
        racer.start();

        catchThrowable(() -> new ProjectFileOperations().deleteRecursively(doomed));
        racer.join(TimeUnit.SECONDS.toMillis(90));

        assertThat(swap[0]).as("the directory must be replaced by the junction while it is being deleted").isEqualTo("swapped");
        long survivors;
        try(var files=Files.list(target)) { survivors=files.count(); }
        assertThat(survivors).as("files outside the deleted directory were deleted through a junction swapped in during the walk").isEqualTo(count);
    }

    @Test void aJunctionThatStaysInsideTheProjectIsNotAnEscape() throws Exception {
        Path doomed=temporary.resolve("doomed-inside"); Files.createDirectories(doomed.resolve("data"));
        junction(doomed.resolve("alias"),doomed.resolve("data"));
        Files.writeString(doomed.resolve("data").resolve("a.txt"),"a");

        new ProjectFileOperations().deleteRecursively(doomed);

        assertThat(doomed).as("a junction inside the deleted directory is not a reason to stop the deletion").doesNotExist();
    }

    /** mklink /J needs no privilege, unlike a symbolic link: any local user or tool can create one. */
    private static void junction(Path link, Path target) throws Exception {
        Process process=new ProcessBuilder("cmd","/c","mklink","/J",link.toString(),target.toString()).redirectErrorStream(true).start();
        String output=new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor(30,TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
        assertThat(Files.isSymbolicLink(link)).as("Java does not see a junction as a symbolic link").isFalse();
        assertThat(Files.isDirectory(link)).isTrue();
    }
}
