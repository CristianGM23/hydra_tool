package com.example.editor.project.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.example.editor.project.ProjectApplicationService;
import com.example.editor.project.ProjectDescriptorDrawioExporter;
import com.example.editor.project.ProjectSynchronizationPlanDrawioExporter;
import com.example.editor.project.ProjectTreeDrawioExporter;
import com.example.editor.projection.drawio.DrawioProjectionService;
import com.example.editor.project.ProjectStorageTestFixture;
import com.example.editor.project.LocalProjectProperties;
import com.example.editor.project.ProjectPathPickerResult;
import com.example.editor.web.ApiExceptionHandler;

class ProjectControllerTests {
    @TempDir Path temporary;
    private MockMvc mockMvc;

    @BeforeEach void setUp() {
        ProjectApplicationService service=new ProjectApplicationService(ProjectStorageTestFixture.storage(temporary));
        DrawioProjectionService drawio=new DrawioProjectionService(java.util.List.of(new ProjectDescriptorDrawioExporter(),new ProjectTreeDrawioExporter(),new ProjectSynchronizationPlanDrawioExporter()));
        mockMvc=MockMvcBuilders.standaloneSetup(new ProjectController(service,drawio)).setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test void nativePathPickerHasSafeFallbackInStandaloneTests() throws Exception {
        mockMvc.perform(post("/api/projects/path-picker").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"DIRECTORY\",\"title\":\"Select directory\",\"initialDirectory\":\"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.mode").value("DIRECTORY"));
    }

    @Test void nativePathPickerAcceptsIpv4Localhost() throws Exception {
        ProjectApplicationService service=new ProjectApplicationService(ProjectStorageTestFixture.storage(temporary));
        DrawioProjectionService drawio=new DrawioProjectionService(java.util.List.of(new ProjectDescriptorDrawioExporter(),new ProjectTreeDrawioExporter(),new ProjectSynchronizationPlanDrawioExporter()));
        LocalProjectProperties properties=new LocalProjectProperties();
        ProjectController controller=new ProjectController(service,drawio,request -> new ProjectPathPickerResult(true,false,"C:/work","C:/work","",request.mode(),"Directory selected"),properties,null);
        MockMvc localhostMvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
        localhostMvc.perform(post("/api/projects/path-picker").with(request -> { request.setRemoteAddr("127.0.0.1"); request.setLocalAddr("127.0.0.1"); return request; }).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"DIRECTORY\",\"title\":\"Select directory\",\"initialDirectory\":\"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true));
    }

    @Test void nativePathPickerAcceptsIpv6Localhost() throws Exception {
        ProjectApplicationService service=new ProjectApplicationService(ProjectStorageTestFixture.storage(temporary));
        DrawioProjectionService drawio=new DrawioProjectionService(java.util.List.of(new ProjectDescriptorDrawioExporter(),new ProjectTreeDrawioExporter(),new ProjectSynchronizationPlanDrawioExporter()));
        LocalProjectProperties properties=new LocalProjectProperties();
        ProjectController controller=new ProjectController(service,drawio,request -> new ProjectPathPickerResult(true,false,"C:/work","C:/work","",request.mode(),"Directory selected"),properties,null);
        MockMvc localhostMvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
        localhostMvc.perform(post("/api/projects/path-picker").with(request -> { request.setRemoteAddr("::1"); request.setLocalAddr("::1"); return request; }).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"DIRECTORY\",\"title\":\"Select directory\",\"initialDirectory\":\"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true));
    }

    @Test void nativePathPickerAcceptsTheSameComputerWhenHydraIsOpenedByItsLocalAddress() throws Exception {
        ProjectApplicationService service=new ProjectApplicationService(ProjectStorageTestFixture.storage(temporary));
        DrawioProjectionService drawio=new DrawioProjectionService(java.util.List.of(new ProjectDescriptorDrawioExporter(),new ProjectTreeDrawioExporter(),new ProjectSynchronizationPlanDrawioExporter()));
        LocalProjectProperties properties=new LocalProjectProperties();
        ProjectController controller=new ProjectController(service,drawio,request -> new ProjectPathPickerResult(true,false,"C:/work","C:/work","",request.mode(),"Directory selected"),properties,null);
        MockMvc localAddressMvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
        localAddressMvc.perform(post("/api/projects/path-picker").with(request -> { request.setRemoteAddr("192.168.1.25"); request.setLocalAddr("192.168.1.25"); return request; }).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"DIRECTORY\",\"title\":\"Select directory\",\"initialDirectory\":\"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.selectedPath").value("C:/work"));
    }

    @Test void nativePathPickerStillRejectsARealRemoteComputer() throws Exception {
        ProjectApplicationService service=new ProjectApplicationService(ProjectStorageTestFixture.storage(temporary));
        DrawioProjectionService drawio=new DrawioProjectionService(java.util.List.of(new ProjectDescriptorDrawioExporter(),new ProjectTreeDrawioExporter(),new ProjectSynchronizationPlanDrawioExporter()));
        LocalProjectProperties properties=new LocalProjectProperties();
        ProjectController controller=new ProjectController(service,drawio,request -> new ProjectPathPickerResult(true,false,"C:/work","C:/work","",request.mode(),"Directory selected"),properties,null);
        MockMvc remoteMvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
        remoteMvc.perform(post("/api/projects/path-picker").with(request -> { request.setRemoteAddr("203.0.113.25"); request.setLocalAddr("192.168.1.25"); return request; }).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"DIRECTORY\",\"title\":\"Select directory\",\"initialDirectory\":\"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("same computer")));
    }

    @Test void createsProjectAndReturnsLogicalTree() throws Exception {
        String body="""
                {"parentDirectory":"%s","projectId":"railway","name":"Railway","description":"Demo","initializeFromCurrentWorkspace":false}
                """.formatted(temporary.toString().replace("\\","\\\\"));
        mockMvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.project.id").value("railway"));
        mockMvc.perform(get("/api/projects/active/tree").param("view","LOGICAL"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nodeType").value("PROJECT"))
                .andExpect(jsonPath("$.children[0].nodeType").value("GROUP"));
    }

    @Test void reportsMissingProjectInsteadOfReadingArbitraryFiles() throws Exception {
        mockMvc.perform(get("/api/projects/active/file").param("path","../../secret.txt"))
                .andExpect(status().isNotFound());
    }


    @Test void exportsProjectDescriptorAsDrawioXml() throws Exception {
        createProject();
        mockMvc.perform(get("/api/projects/active/drawio/descriptor"))
                .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("<mxfile")));
    }

    @Test void resolvesLocalPathInsideActiveProject() throws Exception {
        createProject();
        mockMvc.perform(get("/api/projects/active/local-path").param("path","docs"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.directory").value(true)).andExpect(jsonPath("$.exists").value(true));
    }

    @Test void closesProjectAndReturnsNoContent() throws Exception {
        createProject();
        mockMvc.perform(post("/api/projects/close")).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/projects/session")).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
    }

    private void createProject() throws Exception {
        String body="""
                {"parentDirectory":"%s","projectId":"railway","name":"Railway","description":"Demo","initializeFromCurrentWorkspace":false}
                """.formatted(temporary.toString().replace("\\","\\\\"));
        mockMvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
    }
}
