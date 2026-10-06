package com.example.editor.diagram;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.editor.config.StorageProperties;
import com.example.editor.diagram.DiagramDocument.DiagramEdgeView;
import com.example.editor.diagram.DiagramDocument.DiagramNodeView;
import com.example.editor.web.ApiExceptionHandler;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprueba el almacenamiento y la API de documentos visuales.
 */
class DiagramStorageTests {

    @TempDir
    Path temporaryDirectory;

    private DiagramStorageService service;
    private MockMvc mockMvc;

    /**
     * Descripción:
     * Crea un almacenamiento temporal.
     */
    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties(temporaryDirectory.resolve("metamodels"), temporaryDirectory.resolve("models"), temporaryDirectory.resolve("notations"), temporaryDirectory.resolve("validations"), temporaryDirectory.resolve("quickfixes"), temporaryDirectory.resolve("generators"), temporaryDirectory.resolve("assets"), temporaryDirectory.resolve("exports"), true);
        service = new DiagramStorageService(properties, JsonMapper.builder().build());
        mockMvc = MockMvcBuilders.standaloneSetup(new DiagramController(service)).setControllerAdvice(new ApiExceptionHandler()).build();
    }

    /**
     * Descripción:
     * Comprueba el guardado y lectura de un diagrama.
     */
    @Test
    void savesAndReadsDiagram() {
        DiagramDocument document = createDocument(100, 80);

        service.save(document);
        DiagramDocument loaded = service.find("model-1.xmi");

        assertThat(loaded).isEqualTo(document);
    }

    /**
     * Descripción:
     * Comprueba que un guardado posterior sustituye al anterior.
     */
    @Test
    void overwritesExistingDiagram() throws IOException {
        service.save(createDocument(100, 80));
        service.save(createDocument(220, 140));

        DiagramDocument loaded = service.find("model-1.xmi");

        assertThat(loaded.nodes().get(0).width()).isEqualTo(220);
        assertThat(loaded.nodes().get(0).height()).isEqualTo(140);

        try (var files = Files.list(temporaryDirectory.resolve("models"))) {
            assertThat(files.map(path -> path.getFileName().toString()).toList())
                .containsExactly("model-1.diagram.json");
        }
    }

    /**
     * Descripción:
     * Comprueba que no se aceptan nodos duplicados.
     */
    @Test
    void rejectsDuplicateNodeIds() {
        DiagramNodeView firstNode = new DiagramNodeView("element-1", "model-container", 10, 20, 100, 80);
        DiagramNodeView secondNode = new DiagramNodeView("element-1", "model-container", 30, 40, 100, 80);
        DiagramDocument document = new DiagramDocument(1, "model-1.xmi", "introspection-default", List.of(firstNode, secondNode), List.of());

        assertThatThrownBy(() -> service.save(document))
            .isInstanceOf(DiagramStorageException.class)
            .hasMessageContaining("Duplicate diagram node element id");
    }

    /**
     * Descripción:
     * Comprueba que las dimensiones deben ser positivas.
     */
    @Test
    void rejectsInvalidNodeDimensions() {
        DiagramNodeView node = new DiagramNodeView("element-1", "model-container", 10, 20, 0, 80);
        DiagramDocument document = new DiagramDocument(1, "model-1.xmi", "introspection-default", List.of(node), List.of());

        assertThatThrownBy(() -> service.save(document))
            .isInstanceOf(DiagramStorageException.class)
            .hasMessageContaining("Diagram node dimensions must be greater than zero");
    }

    /**
     * Descripción:
     * Comprueba que no se aceptan rutas dentro del nombre.
     */
    @Test
    void rejectsUnsafeModelFileName() {
        DiagramDocument document = new DiagramDocument(1, "../model.xmi", "introspection-default", List.of(), List.of());

        assertThatThrownBy(() -> service.save(document))
            .isInstanceOf(DiagramStorageException.class)
            .hasMessageContaining("Model file name must not contain path separators");
    }

    /**
     * Descripción:
     * Comprueba el error de un diagrama desconocido.
     */
    @Test
    void rejectsMissingDiagram() {
        assertThatThrownBy(() -> service.find("missing.xmi"))
            .isInstanceOf(DiagramStorageException.class)
            .hasMessageContaining("Diagram document was not found");
    }

    /**
     * Descripción:
     * Comprueba el guardado y lectura mediante HTTP.
     */
    @Test
    void exposesSaveAndFindEndpoints() throws Exception {
        String requestBody = "{\"formatVersion\":1,\"notationId\":\"introspection-default\",\"nodes\":[{\"elementId\":\"element-1\",\"nodeStyleId\":\"model-container\",\"x\":10,\"y\":20,\"width\":100,\"height\":80}],\"edges\":[]}";

        mockMvc.perform(put("/api/diagrams/model-1.xmi").contentType(MediaType.APPLICATION_JSON).content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.modelFileName").value("model-1.xmi"));

        mockMvc.perform(get("/api/diagrams/model-1.xmi"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nodes[0].width").value(100));
    }

    /**
     * Descripción:
     * Comprueba que el archivo visual puede eliminarse.
     */
    @Test
    void deletesDiagram() {
        service.save(createDocument(100, 80));
        service.delete("model-1.xmi");

        assertThatThrownBy(() -> service.find("model-1.xmi"))
            .isInstanceOf(DiagramStorageException.class)
            .hasMessageContaining("Diagram document was not found");
    }

    /**
     * Descripción:
     * Crea un documento visual utilizado por los tests.
     *
     * Entrada:
     * @param width contiene la anchura.
     * @param height contiene la altura.
     *
     * Salida:
     * @return un documento visual válido.
     */
    private DiagramDocument createDocument(double width, double height) {
        DiagramNodeView node = new DiagramNodeView("element-1", "model-container", 10, 20, width, height);
        DiagramEdgeView edge = new DiagramEdgeView("element-2", "target", "element-3", "node-target");
        return new DiagramDocument(1, "model-1.xmi", "introspection-default", List.of(node), List.of(edge));
    }
}