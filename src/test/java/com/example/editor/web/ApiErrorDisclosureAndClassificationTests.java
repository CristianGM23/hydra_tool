package com.example.editor.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.example.editor.event.flow.EventFlowApplicationException;
import com.example.editor.metamodel.application.MetamodelApplicationException;
import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.notation.NotationRegistryException;
import com.example.editor.project.ProjectException;
import com.example.editor.standalone.StandaloneEditorGenerationException;
import com.example.editor.transformation.TransformationApplicationException;
import com.example.editor.validation.persistence.CanonicalDocumentStoreException;
import com.example.editor.validation.runtime.ValidationRuntimeApplicationException;

/**
 * T-SEC (autorización del 30/10): una respuesta de error no debe entregar rutas del servidor, directorios personales ni mensajes de excepciones
 * internas, y cada error debe conservar su clasificación (cliente, conflicto, indisponibilidad o interno) y un código distinguible.
 * DEF-API-SERVER-ERROR-DISCLOSURE: los 500 con motivo (STORAGE_ERROR, EXECUTION_FAILED, GENERATION_FAILED...) devolvían el mensaje interno con
 * la ruta del fichero. DEF-API-SPRING-MVC-ERRORS-AS-500: los errores estándar de Spring MVC (405, 404, 415, parámetro o parte ausente, subida
 * demasiado grande) llegaban al manejador de último recurso y salían como 500 INTERNAL_ERROR.
 */
class ApiErrorDisclosureAndClassificationTests {
    private static final String SECRET_PATH = "C:\\srv\\hydra\\data\\secret-project\\doc.json";
    private static final String HOME_PATH = System.getProperty("user.home") + java.io.File.separator + "secreto-del-usuario" + java.io.File.separator + "x.json";

    @RestController
    static class Throwing {
        @PostMapping("/server/{kind}")
        void server(@PathVariable("kind") String kind) throws Exception {
            IOException cause = new IOException("Access is denied: " + SECRET_PATH);
            throw switch(kind) {
                case "metamodel" -> new MetamodelApplicationException(MetamodelApplicationException.Reason.STORAGE_ERROR, "Could not parse canonical metamodel: " + SECRET_PATH, cause);
                case "design" -> new MetamodelDesignException(MetamodelDesignException.Reason.STORAGE_ERROR, "Could not read metamodel diagram: " + SECRET_PATH, cause);
                case "project" -> new ProjectException(ProjectException.Reason.STORAGE_ERROR, "Could not hash " + SECRET_PATH, cause);
                case "transformation" -> new TransformationApplicationException(TransformationApplicationException.Reason.STORAGE_ERROR, "Could not read " + SECRET_PATH, cause);
                case "notation" -> new NotationRegistryException(NotationRegistryException.Reason.STORAGE_ERROR, "Could not read notation file: " + SECRET_PATH, cause);
                case "store" -> new CanonicalDocumentStoreException(CanonicalDocumentStoreException.Reason.STORAGE_ERROR, "Could not store document: " + SECRET_PATH, cause);
                case "flow" -> new EventFlowApplicationException(EventFlowApplicationException.Reason.EXECUTION_FAILED, "java.lang.IllegalStateException: " + SECRET_PATH, cause);
                case "validation" -> new ValidationRuntimeApplicationException(ValidationRuntimeApplicationException.Reason.EXECUTION_FAILED, "java.io.IOException: " + SECRET_PATH, cause);
                case "standalone" -> new StandaloneEditorGenerationException(StandaloneEditorGenerationException.Reason.GENERATION_FAILED, "npm failed in " + SECRET_PATH, cause);
                case "pending" -> new ProjectException(ProjectException.Reason.STORAGE_ERROR, "The import failed (" + SECRET_PATH + ") and the cleanup is incomplete. Pending: [METAMODEL:m1 (" + SECRET_PATH + ")]",
                        "The import failed and the cleanup is incomplete. Pending: [METAMODEL:m1]", cause);
                default -> new IllegalStateException(kind);
            };
        }

        @PostMapping("/client/{kind}")
        void client(@PathVariable("kind") String kind) throws Exception {
            throw switch(kind) {
                case "invalid" -> new MetamodelApplicationException(MetamodelApplicationException.Reason.INVALID_REQUEST, "Metamodel name must not be blank");
                case "not-found-home" -> new ProjectException(ProjectException.Reason.NOT_FOUND, "Project file was not found: " + HOME_PATH);
                case "method" -> new HttpRequestMethodNotSupportedException("DELETE", List.of("POST"));
                case "resource" -> new NoResourceFoundException(HttpMethod.GET, "/api/no-existe", "api/no-existe");
                case "media" -> new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON));
                case "param" -> new MissingServletRequestParameterException("query", "String");
                case "part" -> new MissingServletRequestPartException("file");
                case "upload" -> new MaxUploadSizeExceededException(4096);
                case "type" -> new MethodArgumentTypeMismatchException("abc", Integer.class, "limit", null, new NumberFormatException("For input string: \"abc\""));
                default -> new IllegalStateException(kind);
            };
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Throwing()).setControllerAdvice(new ApiExceptionHandler(), new HydraProblemDetailsAdvice()).build();

    @ParameterizedTest
    @ValueSource(strings = {"metamodel", "design", "project", "transformation", "notation", "store", "flow", "validation", "standalone"})
    void aServerFailureKeepsItsCodeButNeverHandsInternalDetailsToTheClient(String kind) throws Exception {
        MockHttpServletResponse response = mvc.perform(post("/server/" + kind)).andReturn().getResponse();
        String body = response.getContentAsString();

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(body).doesNotContain("secret-project").doesNotContain("C:\\\\srv").doesNotContain("C:\\srv").doesNotContain("Access is denied")
                .doesNotContain("java.lang.").doesNotContain("java.io.");
        Map<String, String> expectedCode = Map.of("flow", "EXECUTION_FAILED", "validation", "EXECUTION_FAILED", "standalone", "GENERATION_FAILED");
        assertThat(body).contains("\"code\":\"" + expectedCode.getOrDefault(kind, "STORAGE_ERROR") + "\"");
    }

    /** A server failure whose explicit public message tells the client what is pending answers it, never the internal message with paths. */
    @Test
    void aServerFailureWithAnExplicitPublicMessageAnswersItWithoutInternalDetails() throws Exception {
        MockHttpServletResponse response = mvc.perform(post("/server/pending")).andReturn().getResponse();
        String body = response.getContentAsString();

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(body).contains("\"code\":\"STORAGE_ERROR\"").contains("Pending: [METAMODEL:m1]").doesNotContain("secret-project").doesNotContain("C:\\\\srv");
    }

    @Test
    void aClientErrorKeepsItsInformativeMessage() throws Exception {
        MockHttpServletResponse response = mvc.perform(post("/client/invalid")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("\"code\":\"INVALID_REQUEST\"").contains("Metamodel name must not be blank");
    }

    @Test
    void aClientErrorDoesNotHandTheServerUserDirectoryToTheClient() throws Exception {
        MockHttpServletResponse response = mvc.perform(post("/client/not-found-home")).andReturn().getResponse();
        String body = response.getContentAsString();

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(body).contains("secreto-del-usuario").doesNotContain(System.getProperty("user.home")).doesNotContain(System.getProperty("user.home").replace("\\", "\\\\"));
    }

    @Test
    void standardSpringMvcErrorsKeepTheirClientStatus() throws Exception {
        Map<String, Integer> expected = Map.of("method", 405, "resource", 404, "media", 415, "param", 400, "part", 400, "upload", 413, "type", 400);
        for(Map.Entry<String, Integer> item : expected.entrySet()) {
            MockHttpServletResponse response = mvc.perform(post("/client/" + item.getKey())).andReturn().getResponse();
            assertThat(response.getStatus()).as(item.getKey()).isEqualTo(item.getValue());
            assertThat(response.getContentAsString()).as(item.getKey()).doesNotContain("INTERNAL_ERROR").doesNotContain("java.lang.");
        }
        assertThat(mvc.perform(post("/client/method")).andReturn().getResponse().getHeader("Allow")).contains("POST");
    }
}
