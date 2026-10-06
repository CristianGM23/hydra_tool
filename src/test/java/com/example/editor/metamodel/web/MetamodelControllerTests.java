package com.example.editor.metamodel.web;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.editor.config.StorageProperties;
import com.example.editor.emf.EmfResourceSetFactory;
import com.example.editor.emf.MetamodelLoader;
import com.example.editor.metamodel.projection.ProjectionStatus;
import com.example.editor.metamodel.registry.MetamodelRegistryService;
import com.example.editor.metamodel.registry.RegisteredMetamodel;
import com.example.editor.metamodel.registry.RuntimeMetamodelTestSupport;
import com.example.editor.web.ApiExceptionHandler;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprueba los endpoints de consulta del registro runtime de metamodelos.
 * La creación y modificación de metamodelos pertenece a la API canónica.
 */
class MetamodelControllerTests {

    @TempDir
    Path temporaryDirectory;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        StorageProperties storage = createStorageProperties();
        MetamodelLoader loader = new MetamodelLoader(new EmfResourceSetFactory());
        java.net.URL resourceUrl = Objects.requireNonNull(getClass().getResource("/metamodels/sample.ecore"), "Test resource was not found");
        RegisteredMetamodel sample = RuntimeMetamodelTestSupport.load("sample", "sample.ecore", Path.of(resourceUrl.toURI()), loader);
        var runtimeStatus = new MetamodelRegistryService.RuntimeMetamodelStatus("sample", ProjectionStatus.UP_TO_DATE, true, "");
        MetamodelRegistryService registryService = RuntimeMetamodelTestSupport.fixedRegistry(storage, loader, List.of(sample), List.of(runtimeStatus));

        MetamodelController controller = new MetamodelController(registryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void listsRegisteredMetamodels() throws Exception {
        mockMvc.perform(get("/api/metamodels"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("sample"))
            .andExpect(jsonPath("$[0].fileName").value("sample.ecore"))
            .andExpect(jsonPath("$[0].rootPackageCount").value(1));
    }

    @Test
    void returnsCompleteMetamodelDescriptor() throws Exception {
        mockMvc.perform(get("/api/metamodels/sample"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.packages[0].name").value("sample"))
            .andExpect(jsonPath("$.packages[0].nsUri").value("https://example.com/sample"));
    }

    @Test
    void returnsRuntimeProjectionStatus() throws Exception {
        mockMvc.perform(get("/api/metamodels/runtime-status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].metamodelId").value("sample"))
            .andExpect(jsonPath("$[0].projectionStatus").value("UP_TO_DATE"))
            .andExpect(jsonPath("$[0].runtimeAvailable").value(true));
    }

    @Test
    void reloadsRuntimeRegistry() throws Exception {
        mockMvc.perform(post("/api/metamodels/reload"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("sample"));
    }

    @Test
    void returnsNotFoundForUnknownMetamodel() throws Exception {
        mockMvc.perform(get("/api/metamodels/unknown"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Metamodel was not found: unknown"));
    }

    private StorageProperties createStorageProperties() {
        return new StorageProperties(
            temporaryDirectory.resolve("metamodels"),
            temporaryDirectory.resolve("models"),
            temporaryDirectory.resolve("notations"),
            temporaryDirectory.resolve("validations"),
            temporaryDirectory.resolve("quickfixes"),
            temporaryDirectory.resolve("generators"),
            temporaryDirectory.resolve("assets"),
            temporaryDirectory.resolve("exports"),
            true
        );
    }
}
