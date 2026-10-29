package com.example.editor.grammar.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.editor.grammar.GrammarProperties;
import com.example.editor.grammar.application.GrammarAnalysisCache;
import com.example.editor.grammar.application.GrammarAnalysisCoordinator;
import com.example.editor.grammar.application.GrammarApplicationService;
import com.example.editor.grammar.application.GrammarCompilationProgressService;
import com.example.editor.grammar.application.GrammarCompilationService;
import com.example.editor.grammar.application.GrammarGeneratedProjectArchiveService;
import com.example.editor.grammar.application.GrammarNativeToolchainService;
import com.example.editor.grammar.application.GrammarToolchainProfileService;
import com.example.editor.grammar.core.GrammarAuthoringMode;
import com.example.editor.grammar.core.GrammarCompilationRequest;
import com.example.editor.grammar.core.GrammarDefinitionModel;
import com.example.editor.grammar.core.GrammarSemanticBindingMap;
import com.example.editor.grammar.core.GrammarSourceDocument;
import com.example.editor.grammar.core.GrammarTechnology;
import com.example.editor.grammar.persistence.JsonGrammarActivationRepository;
import com.example.editor.grammar.persistence.JsonGrammarCompilationRepository;
import com.example.editor.grammar.persistence.JsonGrammarRepository;
import com.example.editor.grammar.provider.GrammarProviderRegistry;
import com.example.editor.grammar.provider.antlr.AntlrGrammarProvider;
import com.example.editor.grammar.provider.langium.LangiumGrammarProvider;
import com.example.editor.grammar.provider.xtext.XtextGrammarProvider;
import com.example.editor.grammar.visualization.GrammarClassDiagramProjectionService;
import com.example.editor.integration.verification.ExternalToolCircuitBreaker;
import com.example.editor.integration.verification.ExternalToolProcessRunner;
import com.example.editor.persistence.LocalCanonicalDocumentStoreFactory;
import com.example.editor.security.process.HydraExternalProcessPolicy;
import com.example.editor.security.process.HydraExternalProcessSecurityProperties;
import com.example.editor.security.process.HydraTemporaryWorkspace;
import com.example.editor.web.ApiExceptionHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia real de grammar_author_download.dl: tras compilar una gramatica Langium valida por HTTP, la
 * descarga del proyecto generado entrega un ZIP cuyo nombre, tipo, entradas y contenido coinciden con el
 * proyecto que la propia respuesta de compilacion declaro; una compilacion fallida no se puede descargar.
 * El oraculo descomprime el ZIP en el test y lo compara con el JSON de la respuesta de compilacion.
 */
class GrammarCompileThenDownloadZipIntegrationTests {

    private MockMvc mockMvc;
    private JsonMapper mapper;

    private void wire(Path temporary) {
        GrammarProperties properties = new GrammarProperties();
        properties.setDirectory(temporary.resolve("grammars"));
        properties.setCompilationDirectory(temporary.resolve("grammar-compilations"));
        properties.setActivationDirectory(temporary.resolve("grammar-activations"));
        mapper = JsonMapper.builder().findAndAddModules().build();
        LocalCanonicalDocumentStoreFactory stores = new LocalCanonicalDocumentStoreFactory();
        GrammarToolchainProfileService profiles = new GrammarToolchainProfileService();
        GrammarProviderRegistry providers = new GrammarProviderRegistry(List.of(
                new AntlrGrammarProvider(properties), new LangiumGrammarProvider(properties), new XtextGrammarProvider(properties)));
        GrammarApplicationService grammars = new GrammarApplicationService(new JsonGrammarRepository(properties, mapper, stores), properties, profiles, providers);
        GrammarAnalysisCoordinator analysis = new GrammarAnalysisCoordinator(profiles, providers, new GrammarAnalysisCache(properties),
                new com.example.editor.grammar.runtime.GrammarProviderHealthService(properties), properties);
        GrammarNativeToolchainService nativeToolchain = new GrammarNativeToolchainService(
                new ExternalToolProcessRunner(new ExternalToolCircuitBreaker()),
                new HydraTemporaryWorkspace(new HydraExternalProcessPolicy(new HydraExternalProcessSecurityProperties())), properties);
        GrammarCompilationProgressService progress = new GrammarCompilationProgressService(properties);
        GrammarCompilationService service = new GrammarCompilationService(grammars, profiles, providers, analysis,
                new JsonGrammarCompilationRepository(properties, mapper, stores), new JsonGrammarActivationRepository(properties, mapper, stores),
                nativeToolchain, new GrammarClassDiagramProjectionService(properties), properties, progress);
        GrammarCompilationController controller = new GrammarCompilationController(service,
                new GrammarGeneratedProjectArchiveService(properties), progress, null);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();

        GrammarSourceDocument document = new GrammarSourceDocument("main", "main.langium", "langium",
                "grammar Stable\nentry Model: nodes+=Node*;\nNode: 'node' name=ID;\nhidden terminal WS: /\\s+/;\nterminal ID: /[_a-zA-Z][\\w_]*/;\n", "", true, false);
        grammars.create(new GrammarDefinitionModel("1.0", "stable", "Stable", GrammarTechnology.LANGIUM, GrammarAuthoringMode.GRAMMAR_FIRST, "",
                "langium-4.4-browser", "main", "Model", List.of(document), GrammarSemanticBindingMap.empty(), "", Map.of(), Map.of()));
    }

    private MockHttpServletResponse compile(String source, long revision) throws Exception {
        GrammarSourceDocument document = new GrammarSourceDocument("main", "main.langium", "langium", source, "", true, false);
        GrammarCompilationRequest request = new GrammarCompilationRequest("", "stable", revision, List.of(document), "langium-4.4-browser", "", true, Map.of());
        return mockMvc.perform(post("/api/grammars/stable/compile").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request))).andExpect(status().isOk()).andReturn().getResponse();
    }

    private static Map<String, String> unzip(byte[] archive, List<String> entryOrder) throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                entryOrder.add(entry.getName());
                files.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("grammar_author_download.dl: el ZIP descargado contiene exactamente los ficheros del proyecto generado que declaro la compilacion")
    void downloadedArchiveMatchesTheGeneratedProjectDeclaredByTheCompileResponse(@TempDir Path temporary) throws Exception {
        wire(temporary);
        MockHttpServletResponse compiled = compile("grammar Stable\nentry Model: nodes+=Node*;\nNode: 'node' name=ID;\nhidden terminal WS: /\\s+/;\nterminal ID: /[_a-zA-Z][\\w_]*/;\n", 1);
        Map<String, Object> snapshot = mapper.readValue(compiled.getContentAsString(StandardCharsets.UTF_8), Map.class);
        String snapshotId = (String) snapshot.get("id");
        assertThat(snapshot.get("status")).isEqualTo("SUCCESS");
        Map<String, String> declared = new TreeMap<>((Map<String, String>) ((Map<String, Object>) snapshot.get("generatedProject")).get("files"));
        assertThat(declared).isNotEmpty();

        MockHttpServletResponse download = mockMvc.perform(get("/api/grammar-compilations/" + snapshotId + "/generated-project.zip"))
                .andExpect(status().isOk()).andReturn().getResponse();

        assertThat(download.getContentType()).startsWith("application/zip");
        String portableId = snapshotId.replaceAll("[^A-Za-z0-9._-]+", "-");
        assertThat(download.getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"stable-" + portableId + "-language-services.zip\"");
        List<String> order = new ArrayList<>();
        Map<String, String> archived = unzip(download.getContentAsByteArray(), order);
        assertThat(order).containsExactlyElementsOf(new ArrayList<>(declared.keySet()));
        assertThat(archived).isEqualTo(declared);
        assertThat(download.getContentLength()).isEqualTo(download.getContentAsByteArray().length);
    }

    @Test
    @DisplayName("grammar_author_download.dl: un snapshot fallido no se puede descargar")
    void aFailedCompilationCannotBeDownloaded(@TempDir Path temporary) throws Exception {
        wire(temporary);
        MockHttpServletResponse compiled = compile("!!! this is not a valid langium grammar !!!", 1);
        Map<?, ?> snapshot = mapper.readValue(compiled.getContentAsString(StandardCharsets.UTF_8), Map.class);
        assertThat(snapshot.get("status")).isEqualTo("FAILED");

        MockHttpServletResponse download = mockMvc.perform(get("/api/grammar-compilations/" + snapshot.get("id") + "/generated-project.zip"))
                .andExpect(status().isBadRequest()).andReturn().getResponse();

        assertThat(String.valueOf(download.getContentType())).doesNotContain("zip");
        assertThat(download.getContentAsString(StandardCharsets.UTF_8)).contains("Only a successful grammar snapshot can export a generated project");
    }
}
