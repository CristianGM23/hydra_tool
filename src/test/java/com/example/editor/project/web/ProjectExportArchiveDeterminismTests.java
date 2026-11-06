package com.example.editor.project.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.example.editor.project.CreateLocalProjectRequest;
import com.example.editor.project.ProjectApplicationService;
import com.example.editor.project.ProjectDescriptorDrawioExporter;
import com.example.editor.project.ProjectStorageTestFixture;
import com.example.editor.project.ProjectSynchronizationPlanDrawioExporter;
import com.example.editor.project.ProjectTreeDrawioExporter;
import com.example.editor.project.portability.ProjectPortablePackageService;
import com.example.editor.projection.drawio.DrawioProjectionService;
import com.example.editor.testing.fault.HydraFaultInjectionProperties;
import com.example.editor.testing.fault.HydraFaultInjectionService;
import com.example.editor.web.ApiExceptionHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * OBS-DET-BUNDLE-ARCHIVE-TIMES (proyectos): la exportación de una selección y el paquete portable no deben cambiar entre dos exportaciones
 * del mismo proyecto sin cambios separadas más que la resolución de 2 s de la hora DOS. El paquete portable declara en su manifiesto
 * cuándo se creó (createdAt, esquema 1.0): ese campo es información del contrato y es lo único que puede diferir.
 */
class ProjectExportArchiveDeterminismTests {
    private static final long REPRODUCIBLE_ENTRY_TIME=reproducibleEntryTime();
    @TempDir Path temporary;
    private ProjectApplicationService service;
    private MockMvc mockMvc;

    @BeforeEach void setUp() {
        service=new ProjectApplicationService(ProjectStorageTestFixture.storage(temporary));
        DrawioProjectionService drawio=new DrawioProjectionService(java.util.List.of(new ProjectDescriptorDrawioExporter(),new ProjectTreeDrawioExporter(),new ProjectSynchronizationPlanDrawioExporter()));
        mockMvc=MockMvcBuilders.standaloneSetup(new ProjectController(service,drawio)).setControllerAdvice(new ApiExceptionHandler()).build();
        service.create(new CreateLocalProjectRequest(temporary.toString(),"det","Det","",false));
        service.upload("assets","first.txt","primero".getBytes(StandardCharsets.UTF_8));
        service.upload("assets","second.txt","segundo".getBytes(StandardCharsets.UTF_8));
    }

    @Test void selectionExportIsByteIdenticalAcrossTheTwoSecondZipWindowAndKeepsItsContent() throws Exception {
        byte[] first=exportSelection();
        Thread.sleep(2_100L);
        byte[] second=exportSelection();

        assertThat(second).isEqualTo(first);
        Map<String,byte[]> entries=read(first);
        assertThat(entries).containsKeys("assets/","assets/first.txt","assets/second.txt");
        assertThat(new String(entries.get("assets/first.txt"),StandardCharsets.UTF_8)).isEqualTo("primero");
        assertEntryTimes(first);
    }

    @Test void portablePackageDiffersOnlyInItsDeclaredCreationTime() throws Exception {
        ProjectPortablePackageService portable=new ProjectPortablePackageService(service,JsonMapper.builder().findAndAddModules().build(),new HydraFaultInjectionService(new HydraFaultInjectionProperties()));
        byte[] first=portable.exportPortable();
        Thread.sleep(2_100L);
        byte[] second=portable.exportPortable();

        Map<String,byte[]> a=read(first);
        Map<String,byte[]> b=read(second);
        assertThat(b.keySet()).containsExactlyElementsOf(a.keySet());
        assertThat(b.get("project.zip")).isEqualTo(a.get("project.zip"));
        assertThat(withoutCreationTime(b.get("hydra-portable-manifest.json"))).isEqualTo(withoutCreationTime(a.get("hydra-portable-manifest.json")));
        assertEntryTimes(first);
        assertEntryTimes(second);
    }

    private byte[] exportSelection() throws Exception {
        return mockMvc.perform(post("/api/projects/active/export-selection").contentType(MediaType.APPLICATION_JSON).content("{\"paths\":[\"assets\"]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    private static JsonNode withoutCreationTime(byte[] manifest) {
        ObjectNode node=(ObjectNode)JsonMapper.builder().build().readTree(manifest);
        assertThat(node.has("createdAt")).isTrue();
        node.remove("createdAt");
        return node;
    }

    private static void assertEntryTimes(byte[] archive) throws Exception {
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(archive))) {
            for(ZipEntry entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()) assertThat(entry.getTime()).as("time of "+entry.getName()).isEqualTo(REPRODUCIBLE_ENTRY_TIME);
        }
    }

    private static Map<String,byte[]> read(byte[] archive) throws Exception {
        Map<String,byte[]> entries=new LinkedHashMap<>();
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(archive))) {
            for(ZipEntry entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()) entries.put(entry.getName(),zip.readAllBytes());
        }
        return entries;
    }

    private static long reproducibleEntryTime() {
        ZipEntry probe=new ZipEntry("probe");
        probe.setTime(0L);
        return probe.getTime();
    }
}
