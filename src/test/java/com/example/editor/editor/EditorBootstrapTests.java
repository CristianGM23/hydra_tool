package com.example.editor.editor;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.editor.config.StorageProperties;
import com.example.editor.editor.EditorBootstrapResponse.PaletteEntry;
import com.example.editor.emf.EmfResourceSetFactory;
import com.example.editor.emf.MetamodelLoader;
import com.example.editor.metamodel.registry.MetamodelRegistryService;
import com.example.editor.metamodel.registry.RegisteredMetamodel;
import com.example.editor.metamodel.registry.RuntimeMetamodelTestSupport;
import com.example.editor.web.ApiExceptionHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprueba la preparación de la paleta y el endpoint de arranque del editor.
 */
class EditorBootstrapTests {

    @TempDir
    Path temporaryDirectory;

    private EditorBootstrapService bootstrapService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        StorageProperties properties = new StorageProperties(temporaryDirectory.resolve("metamodels"), temporaryDirectory.resolve("models"), temporaryDirectory.resolve("notations"), temporaryDirectory.resolve("validations"), temporaryDirectory.resolve("quickfixes"), temporaryDirectory.resolve("generators"), temporaryDirectory.resolve("assets"), temporaryDirectory.resolve("exports"), true);
        MetamodelLoader metamodelLoader = new MetamodelLoader(new EmfResourceSetFactory());
        java.net.URL resourceUrl = Objects.requireNonNull(getClass().getResource("/metamodels/introspection.ecore"), "Test resource was not found");
        RegisteredMetamodel registered = RuntimeMetamodelTestSupport.load("introspection", "introspection.ecore", Path.of(resourceUrl.toURI()), metamodelLoader);
        MetamodelRegistryService registryService = RuntimeMetamodelTestSupport.fixedRegistry(properties, metamodelLoader, List.of(registered));

        bootstrapService = new EditorBootstrapService(registryService);
        EditorBootstrapController controller = new EditorBootstrapController(bootstrapService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void returnsMetamodelIdentity() {
        EditorBootstrapResponse response = bootstrapService.create("introspection");
        assertThat(response.metamodelId()).isEqualTo("introspection");
        assertThat(response.fileName()).isEqualTo("introspection.ecore");
    }

    @Test
    void excludesAbstractClassesFromPalette() {
        EditorBootstrapResponse response = bootstrapService.create("introspection");
        assertThat(response.palette()).extracting(PaletteEntry::className).doesNotContain("NamedElement");
    }

    @Test
    void includesInstantiableClassesInPalette() {
        EditorBootstrapResponse response = bootstrapService.create("introspection");
        assertThat(response.palette()).extracting(PaletteEntry::className).contains("Model", "Node");
    }

    @Test
    void includesNestedPackageClasses() {
        EditorBootstrapResponse response = bootstrapService.create("introspection");
        assertThat(response.palette()).extracting(PaletteEntry::className).contains("NestedElement");
    }

    @Test
    void exposesEditorBootstrapEndpoint() throws Exception {
        mockMvc.perform(get("/api/editor/bootstrap/introspection"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.metamodelId").value("introspection"))
            .andExpect(jsonPath("$.fileName").value("introspection.ecore"))
            .andExpect(jsonPath("$.palette.length()").value(3));
    }
}
