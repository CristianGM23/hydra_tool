package com.example.editor.model;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.editor.config.StorageProperties;
import com.example.editor.emf.DynamicModelService;
import com.example.editor.emf.EmfResourceSetFactory;
import com.example.editor.emf.MetamodelLoader;
import com.example.editor.emf.XmiModelRepository;
import com.example.editor.metamodel.registry.MetamodelRegistryService;
import com.example.editor.metamodel.registry.RegisteredMetamodel;
import com.example.editor.metamodel.registry.RuntimeMetamodelTestSupport;
import com.example.editor.model.ModelDocumentResponse.ModelElementResponse;
import com.example.editor.web.ApiExceptionHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprueba la creación, modificación, conexión y persistencia de documentos EMF.
 */
class ModelDocumentTests {

    private static final String NS_URI = "https://example.com/introspection";
    private static final String MODEL_CLASS_ID = NS_URI + "#//Model";
    private static final String NODE_CLASS_ID = NS_URI + "#//Node";

    @TempDir
    Path temporaryDirectory;

    private StorageProperties storageProperties;
    private ModelDocumentService service;
    private MockMvc mockMvc;

    /**
     * Descripción:
     * Prepara un registro y un servicio aislados.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * Crea el entorno temporal utilizado por cada test.
     */
    @BeforeEach
    void setUp() throws Exception {
        storageProperties = new StorageProperties(temporaryDirectory.resolve("metamodels"), temporaryDirectory.resolve("models"), temporaryDirectory.resolve("notations"), temporaryDirectory.resolve("validations"), temporaryDirectory.resolve("quickfixes"), temporaryDirectory.resolve("generators"), temporaryDirectory.resolve("assets"), temporaryDirectory.resolve("exports"), true);
        Files.createDirectories(storageProperties.metamodels());

        EmfResourceSetFactory resourceSetFactory = new EmfResourceSetFactory();
        MetamodelLoader metamodelLoader = new MetamodelLoader(resourceSetFactory);
        java.net.URL resourceUrl = Objects.requireNonNull(getClass().getResource("/metamodels/introspection.ecore"), "Test resource was not found");
        Path ecorePath = storageProperties.metamodels().resolve("introspection.ecore");
        Files.copy(Path.of(resourceUrl.toURI()), ecorePath);
        RegisteredMetamodel registered = RuntimeMetamodelTestSupport.load("introspection", "introspection.ecore", ecorePath, metamodelLoader);
        MetamodelRegistryService registryService = RuntimeMetamodelTestSupport.fixedRegistry(storageProperties, metamodelLoader, List.of(registered));

        service = new ModelDocumentService(storageProperties, registryService, metamodelLoader, new DynamicModelService(), new XmiModelRepository());
        ModelDocumentController controller = new ModelDocumentController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
    }

    /**
     * Descripción:
     * Comprueba la creación del objeto raíz.
     */
    @Test
    void createsModelWithRootElement() {
        ModelDocumentResponse response = service.create("introspection", MODEL_CLASS_ID);

        assertThat(response.elements()).hasSize(1);
        assertThat(response.rootElementId()).isEqualTo("element-1");
        assertThat(response.elements().get(0).className()).isEqualTo("Model");
    }

    /**
     * Descripción:
     * Comprueba la creación de un objeto contenido.
     */
    @Test
    void addsContainedElement() {
           ModelDocumentResponse created = service.create("introspection", MODEL_CLASS_ID);
            ModelDocumentResponse updated = service.addElement(created.id(), created.rootElementId(), "elements", NODE_CLASS_ID);
            ModelElementResponse node = updated.elements().get(1);

            assertThat(updated.elements()).hasSize(2);
            assertThat(node.className()).isEqualTo("Node");
            assertThat(node.containerId()).isEqualTo(created.rootElementId());
            assertThat(node.containmentReference()).isEqualTo("elements");
            assertThat(node.attributes()).containsKey("name");
            assertThat(node.attributes().get("name")).isNull();
    }

    /**
     * Descripción:
     * Comprueba la modificación de un atributo de texto.
     */
    @Test
    void setsStringAttribute() {
        ModelDocumentResponse document = createDocumentWithNode();
        String nodeId = document.elements().get(1).id();

        ModelDocumentResponse updated = service.setAttribute(document.id(), nodeId, "name", "Node A");

        assertThat(updated.elements().get(1).attributes()).containsEntry("name", "Node A");
    }

    /**
     * Descripción:
     * Comprueba la conversión de un atributo booleano.
     */
    @Test
    void setsBooleanAttribute() {
        ModelDocumentResponse document = createDocumentWithNode();
        String nodeId = document.elements().get(1).id();

        ModelDocumentResponse updated = service.setAttribute(document.id(), nodeId, "active", "true");

        assertThat(updated.elements().get(1).attributes()).containsEntry("active", "true");
    }

    /**
     * Descripción:
     * Comprueba la creación de una referencia entre dos nodos.
     */
    @Test
    void setsSemanticReference() {
        ModelDocumentResponse document = service.create("introspection", MODEL_CLASS_ID);
        document = service.addElement(document.id(), document.rootElementId(), "elements", NODE_CLASS_ID);
        document = service.addElement(document.id(), document.rootElementId(), "elements", NODE_CLASS_ID);

        String sourceId = document.elements().get(1).id();
        String targetId = document.elements().get(2).id();
        ModelDocumentResponse updated = service.setReference(document.id(), sourceId, "target", targetId);

        assertThat(updated.references()).hasSize(1);
        assertThat(updated.references().get(0).sourceId()).isEqualTo(sourceId);
        assertThat(updated.references().get(0).targetId()).isEqualTo(targetId);
    }

    /**
     * Descripción:
     * Comprueba que un documento desconocido es rechazado.
     */
    @Test
    void rejectsUnknownModelDocument() {
        assertThatThrownBy(() -> service.find("missing"))
            .isInstanceOf(ModelDocumentException.class)
            .hasMessageContaining("Model document was not found");
    }

    /**
     * Descripción:
     * Comprueba que una referencia rechaza tipos incompatibles.
     */
    @Test
    void rejectsIncompatibleReferenceTarget() {
        ModelDocumentResponse document = createDocumentWithNode();
        String nodeId = document.elements().get(1).id();

        assertThatThrownBy(() -> service.setReference(document.id(), nodeId, "target", document.rootElementId()))
            .isInstanceOf(ModelDocumentException.class)
            .hasMessageContaining("EObject type is not compatible with EReference");
    }

    /**
     * Descripción:
     * Comprueba el guardado del archivo XMI.
     */
    @Test
    void savesModelAsXmi() {
        ModelDocumentResponse document = createDocumentWithNode();
        ModelDocumentResponse saved = service.save(document.id());

        assertThat(saved.dirty()).isFalse();
        assertThat(storageProperties.models().resolve(document.id() + ".xmi")).isRegularFile();
    }

    /**
     * Descripción:
     * Comprueba la creación mediante HTTP.
     */
    @Test
    void createsModelThroughHttp() throws Exception {
        String requestBody = "{\"metamodelId\":\"introspection\",\"rootClassId\":\"" + MODEL_CLASS_ID + "\"}";

        mockMvc.perform(post("/api/models").contentType(MediaType.APPLICATION_JSON).content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value("model-1"))
            .andExpect(jsonPath("$.rootElementId").value("element-1"))
            .andExpect(jsonPath("$.elements[0].className").value("Model"));
    }

    /**
     * Descripción:
     * Comprueba el error HTTP para un documento desconocido.
     */
    @Test
    void returnsNotFoundForUnknownDocument() throws Exception {
        mockMvc.perform(get("/api/models/missing"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Model document was not found: missing"));
    }


    /**
     * Descripción:
     * Crea un documento con un Node contenido.
     *
     * Salida:
     * @return el documento preparado.
     */
    private ModelDocumentResponse createDocumentWithNode() {
        ModelDocumentResponse document = service.create("introspection", MODEL_CLASS_ID);
        return service.addElement(document.id(), document.rootElementId(), "elements", NODE_CLASS_ID);
    }

    /**
     * Descripción:
     * Comprueba que el nombre de archivo aparece en el documento.
     */
    @Test
    void createsModelWithFileName() {
        ModelDocumentResponse document = service.create("introspection", MODEL_CLASS_ID);
        assertThat(document.fileName()).isEqualTo("model-1.xmi");
    }

    /**
     * Descripción:
     * Comprueba que los archivos guardados pueden listarse.
     */
    @Test
    void listsSavedModels() {
        ModelDocumentResponse document = createDocumentWithNode();
        service.save(document.id());

        assertThat(service.listSavedModels()).hasSize(1);
        assertThat(service.listSavedModels().get(0).fileName()).isEqualTo("model-1.xmi");
    }

    @Test
    void deletesSavedModelAndInvalidatesItsOpenSession() throws Exception {
        ModelDocumentResponse document = createDocumentWithNode();
        service.save(document.id());

        mockMvc.perform(delete("/api/models/files/{fileName}", document.fileName()))
                .andExpect(status().isNoContent());

        assertThat(service.listSavedModels()).isEmpty();
        assertThatThrownBy(() -> service.find(document.id()))
                .isInstanceOf(ModelDocumentException.class)
                .hasMessageContaining("not found");
    }

    /**
     * Descripción:
     * Comprueba que un XMI guardado puede abrirse.
     */
    @Test
    void opensSavedModel() {
        ModelDocumentResponse document = createDocumentWithNode();
        service.save(document.id());

        ModelDocumentResponse opened = service.open("introspection", document.fileName());

        assertThat(opened.dirty()).isFalse();
        assertThat(opened.elements()).hasSize(2);
        assertThat(opened.fileName()).isEqualTo(document.fileName());
    }

    /**
     * Descripción:
     * Comprueba que el objeto raíz se conserva al abrir.
     */
    @Test
    void preservesRootWhenOpeningSavedModel() {
        ModelDocumentResponse document = createDocumentWithNode();
        service.save(document.id());

        ModelDocumentResponse opened = service.open("introspection", document.fileName());

        assertThat(opened.rootElementId()).isEqualTo("element-1");
        assertThat(opened.elements().get(0).className()).isEqualTo("Model");
    }

    /**
     * Descripción:
     * Comprueba que un elemento contenido puede eliminarse.
     */
    @Test
    void deletesContainedElement() {
        ModelDocumentResponse document = createDocumentWithNode();
        String nodeId = document.elements().get(1).id();

        ModelDocumentResponse updated = service.deleteElement(document.id(), nodeId);

        assertThat(updated.elements()).hasSize(1);
        assertThat(updated.dirty()).isTrue();
    }

    /**
     * Descripción:
     * Comprueba que el objeto raíz no puede eliminarse.
     */
    @Test
    void deletesSeveralContainedElementsInOneAtomicMutation() {
        ModelDocumentResponse created = service.create("introspection", MODEL_CLASS_ID);
        ModelDocumentResponse one = service.addElement(created.id(), created.rootElementId(), "elements", NODE_CLASS_ID);
        ModelDocumentResponse two = service.addElement(created.id(), created.rootElementId(), "elements", NODE_CLASS_ID);
        List<String> selected = List.of(one.elements().get(1).id(), two.elements().get(2).id());

        ModelDocumentResponse updated = service.deleteElements(created.id(), selected);

        assertThat(updated.elements()).hasSize(1);
        assertThat(updated.rootElementId()).isEqualTo(created.rootElementId());
        assertThat(updated.dirty()).isTrue();
    }

    @Test
    void bulkDeletionValidatesEveryElementBeforeMutatingTheModel() {
        ModelDocumentResponse document = createDocumentWithNode();
        String nodeId = document.elements().get(1).id();

        assertThatThrownBy(() -> service.deleteElements(document.id(), List.of(nodeId, "missing")))
            .isInstanceOf(ModelDocumentException.class).hasMessageContaining("missing");
        assertThat(service.find(document.id()).elements()).hasSize(2);
    }

    @Test
    void rejectsRootElementDeletion() {
        ModelDocumentResponse document = service.create("introspection", MODEL_CLASS_ID);

        assertThatThrownBy(() -> service.deleteElement(document.id(), document.rootElementId()))
            .isInstanceOf(ModelDocumentException.class)
            .hasMessageContaining("Root element cannot be deleted");
    }

    /**
     * Descripción:
     * Comprueba que un elemento desconocido no puede eliminarse.
     */
    @Test
    void rejectsUnknownElementDeletion() {
        ModelDocumentResponse document = service.create("introspection", MODEL_CLASS_ID);

        assertThatThrownBy(() -> service.deleteElement(document.id(), "missing"))
            .isInstanceOf(ModelDocumentException.class)
            .hasMessageContaining("Model element was not found");
    }

    /**
     * Descripción:
     * Comprueba la lectura del archivo guardado.
     */
    @Test
    void readsSavedModelContent() {
        ModelDocumentResponse document = createDocumentWithNode();
        service.save(document.id());

        byte[] content = service.readSavedModel(document.fileName());

        assertThat(content).isNotEmpty();
    }

    /**
     * Descripción:
     * Comprueba que se rechazan nombres con rutas.
     */
    @Test
    void rejectsUnsafeModelFileName() {
        assertThatThrownBy(() -> service.open("introspection", "../model.xmi"))
            .isInstanceOf(ModelDocumentException.class)
            .hasMessageContaining("Model file name must not contain path separators");
    }

    /**
     * Descripción:
     * Comprueba que un archivo desconocido devuelve un error.
     */
    @Test
    void rejectsUnknownModelFile() {
        assertThatThrownBy(() -> service.open("introspection", "missing.xmi"))
            .isInstanceOf(ModelDocumentException.class)
            .hasMessageContaining("Model file was not found");
    }


}