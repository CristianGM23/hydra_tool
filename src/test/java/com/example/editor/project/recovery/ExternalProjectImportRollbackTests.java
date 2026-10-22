package com.example.editor.project.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.project.LocalProjectProperties;
import com.example.editor.project.ProjectApplicationService;
import com.example.editor.project.ProjectException;
import com.example.editor.project.ProjectStorageTestFixture;
import com.example.editor.project.storage.ProjectDeletionService;
import com.example.editor.project.storage.ProjectFileOperations;
import com.example.editor.project.storage.ProjectRegistryStore;
import com.example.editor.project.storage.SafeProjectPathResolver;

import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia FUERTE real, sin mocks, de la limpieza dirigida de {@link ExternalProjectImportService}
 * cuando una importacion falla a mitad de camino: cubre directamente la etapa "a3" (Limpieza
 * dirigida: "Se elimina solo lo creado en esa ejecucion, no una copia completa anterior") de
 * recovery_persist_rollback. El fallo real se provoca por un desajuste genuino entre dos
 * presupuestos de tamano configurados de forma independiente (el caminador de archivo real lee
 * el contenido con un limite generoso; el almacen de proyecto real al que se sube ese mismo
 * contenido tiene un limite mucho mas pequeno), no por una excepcion fabricada o un mock.
 */
class ExternalProjectImportRollbackTests {

    @TempDir
    Path temporary;

    /** El registro de proyectos recientes de estas pruebas vive en el directorio temporal: nunca en el data/ real del espacio de trabajo. */
    private static LocalProjectProperties isolatedRegistryProperties(Path temporary) {
        LocalProjectProperties properties = new LocalProjectProperties();
        properties.setRegistryDirectory(temporary.resolve("project-registry"));
        return properties;
    }
    @Test
    void aFailedUploadMidImportDeletesOnlyTheJustCreatedProjectAndLeavesSiblingsUntouched() throws Exception {
        Path workspace = temporary.resolve("workspace");
        Files.createDirectories(workspace);
        // Un directorio hermano preexistente, ajeno a esta importacion: debe sobrevivir intacto.
        Path untouchedSibling = workspace.resolve("already-here");
        Files.createDirectories(untouchedSibling);
        Files.writeString(untouchedSibling.resolve("marker.txt"), "pre-existing, must survive");

        // Almacen real de proyecto con un presupuesto de subida deliberadamente minusculo (5 bytes):
        // cualquier fichero real de la importacion lo superara.
        var storage = ProjectStorageTestFixture.storage(temporary.resolve("registry-root"),
                properties -> properties.setMaxUploadFileSizeBytes(5));
        ProjectApplicationService projects = new ProjectApplicationService(storage);
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        ProjectDeletionService deletion = new ProjectDeletionService(projects,
                new SafeProjectPathResolver(new LocalProjectProperties()), new ProjectFileOperations(),
                new ProjectRegistryStore(isolatedRegistryProperties(temporary), mapper, new ProjectFileOperations()));

        // El caminador real que lee el ZIP tiene su propio presupuesto, generoso, e independiente
        // del almacen: el desajuste entre ambos es lo que produce el fallo real a mitad de importacion.
        LocalProjectProperties walkerProperties = new LocalProjectProperties();
        ExternalProjectArchiveWalker walker = new ExternalProjectArchiveWalker(walkerProperties);
        ExternalProjectRecoveryService recovery = new ExternalProjectRecoveryService(walker);
        // El materializador semantico nunca llega a invocarse en este escenario (el fallo ocurre
        // antes, durante la subida de los ficheros recuperados): sus 8 colaboradores reales no son
        // necesarios para este camino y por eso no se construyen aqui, igual que el patron ya
        // establecido en UpdateSiteMetamodelRecoveryServiceTests para una dependencia genuinamente
        // no usada en el escenario ejercitado.
        ExternalProjectSemanticMaterializationService materialization = new ExternalProjectSemanticMaterializationService(
                null, null, null, null, null, null, null, null);

        ExternalProjectImportService importService = new ExternalProjectImportService(recovery, walker, projects,
                deletion, materialization, mapper);

        byte[] archive = zip("notes/readme.txt", "this content is well over five bytes long");

        assertThatThrownBy(() -> importService.importProject("notes.zip",
                new ByteArrayInputStream(archive), archive.length, workspace.toString(), "Notes Project"))
                .isInstanceOf(ProjectException.class)
                .satisfies(exception -> assertThat(((ProjectException) exception).getReason())
                        .isEqualTo(ProjectException.Reason.FILE_TOO_LARGE));

        // Limpieza dirigida real: el directorio creado para ESTA importacion ya no existe...
        try (var children = Files.list(workspace)) {
            java.util.List<String> remaining = children.map(path -> path.getFileName().toString()).sorted().toList();
            // ...pero el directorio hermano preexistente, ajeno a la importacion, sigue intacto:
            // no es un borrado completo de todo lo anterior, solo de lo creado en esta ejecucion.
            assertThat(remaining).containsExactly("already-here");
        }
        assertThat(Files.readString(untouchedSibling.resolve("marker.txt"))).isEqualTo("pre-existing, must survive");
    }

    @Test
    void aSuccessfulImportPersistsRealDocumentsOnDiskIdentifiedByAnActiveSession() throws Exception {
        Path workspace = temporary.resolve("workspace-success");
        Files.createDirectories(workspace);

        // Presupuesto de subida normal: este contenido pequeño no provoca ningun fallo real.
        var storage = ProjectStorageTestFixture.storage(temporary.resolve("registry-root-success"));
        ProjectApplicationService projects = new ProjectApplicationService(storage);
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        ProjectDeletionService deletion = new ProjectDeletionService(projects,
                new SafeProjectPathResolver(new LocalProjectProperties()), new ProjectFileOperations(),
                new ProjectRegistryStore(isolatedRegistryProperties(temporary), mapper, new ProjectFileOperations()));
        ExternalProjectArchiveWalker walker = new ExternalProjectArchiveWalker(new LocalProjectProperties());
        ExternalProjectRecoveryService recovery = new ExternalProjectRecoveryService(walker);
        // Sin ningun fichero semantico (.ecore/.xcore/.emf/.xtext/.langium/.g4) en el archivo, el
        // materializador real termina en su propio camino temprano y seguro sin tocar ninguno de
        // sus 8 colaboradores: por eso pueden pasarse a null aqui tambien, de forma genuina.
        ExternalProjectSemanticMaterializationService materialization = new ExternalProjectSemanticMaterializationService(
                null, null, null, null, null, null, null, null);
        ExternalProjectImportService importService = new ExternalProjectImportService(recovery, walker, projects,
                deletion, materialization, mapper);

        byte[] archive = zip("docs/readme.txt", "small file");

        ExternalProjectImportResult result = importService.importProject("docs.zip",
                new ByteArrayInputStream(archive), archive.length, workspace.toString(), "Docs Project");

        // "Documentos guardados": el resultado identifica una sesion de proyecto real y activa,
        // y los documentos reales (descriptor, analisis de recuperacion, informe de materializacion,
        // README) existen de verdad en disco, no solo en memoria.
        assertThat(result.session().active()).isTrue();
        Path projectDirectory = Path.of(result.session().directory());
        assertThat(projectDirectory.resolve("modeling-project.json")).isRegularFile();
        assertThat(projectDirectory.resolve("recovery/external-project-analysis.json")).isRegularFile();
        assertThat(projectDirectory.resolve("recovery/external-project-materialization.json")).isRegularFile();
        assertThat(projectDirectory.resolve("recovery/README.md")).isRegularFile();
        assertThat(projectDirectory.resolve("external-source/docs/readme.txt")).isRegularFile();
    }

    private byte[] zip(String... entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try(ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for(int index = 0; index < entries.length; index += 2) {
                zip.putNextEntry(new ZipEntry(entries[index]));
                zip.write(entries[index + 1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
