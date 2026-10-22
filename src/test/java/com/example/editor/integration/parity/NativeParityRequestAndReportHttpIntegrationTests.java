package com.example.editor.integration.parity;

import static com.example.editor.integration.web.IntegrationDeliveryHttpTestSupport.PROFILES;
import static com.example.editor.integration.web.IntegrationDeliveryHttpTestSupport.createProfile;
import static com.example.editor.integration.web.IntegrationDeliveryHttpTestSupport.emfProfile;
import static com.example.editor.integration.web.IntegrationDeliveryHttpTestSupport.ensureMetamodel;
import static com.example.editor.integration.web.IntegrationDeliveryHttpTestSupport.technology;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.compiler.TechnologyCompilationRequest;
import com.example.editor.integration.compiler.TechnologyCompilerService;
import com.example.editor.metamodel.web.MetamodelHttpTestEnvironment;
import com.jayway.jsonpath.JsonPath;

import tools.jackson.databind.json.JsonMapper;

/**
 * integration_native_parity.req / .to / .cmp / .ko: la peticion de certificacion nativa y su informe, sin ninguna
 * herramienta nativa disponible (el unico ejecutable permitido es git).
 *
 * Contexto Spring completo con el controlador, el compilador, la validacion y el evaluador reales; el compilador solo
 * esta envuelto por un espia que delega en el real para contar invocaciones. Oraculos: codigos HTTP literales, contenido
 * de los directorios de trabajo de validacion leido con java.nio antes y despues, numero de compilaciones y valores
 * literales del informe. La verificacion nativa en si no se ejecuta.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NativeParityRequestAndReportHttpIntegrationTests {

    private static final String PROFILE_ID = "people-emf-parity-request";
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @TempDir
    static Path root;

    @DynamicPropertySource
    static void directories(DynamicPropertyRegistry registry) {
        MetamodelHttpTestEnvironment.register(registry, root);
        registry.add("hydra.security.external-process.allowed-executables", () -> "git");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper mapper;
    @MockitoSpyBean private TechnologyCompilerService compiler;

    private void seed() throws Exception {
        ensureMetamodel(mockMvc, mapper);
        if (mockMvc.perform(get(PROFILES + "/" + PROFILE_ID)).andReturn().getResponse().getStatus() == 404) {
            createProfile(mockMvc, mapper, emfProfile(PROFILE_ID, "people-emf", "people-emf", false, List.of()));
        }
    }

    private String certify(String body, int expectedStatus) throws Exception {
        return mockMvc.perform(post(technology(PROFILE_ID) + "/native-parity/certify").contentType(JSON).content(body))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
    }

    private static String quoted(List<String> names) {
        return names.stream().map(name -> "\"" + name + "\"").collect(Collectors.joining(","));
    }

    private static List<String> entriesUnder(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return List.of();
        try (Stream<Path> walk = Files.walk(directory)) {
            return walk.map(path -> directory.relativize(path).toString()).sorted().toList();
        }
    }

    @Test
    @DisplayName("integration_native_parity.req: lista vacia, repetida, desconocida o de 16 elementos se rechaza con 400 sin compilar ni crear espacios de trabajo")
    void invalidCapabilityListsAreRejectedWithoutCompilingOrCreatingWorkspaces() throws Exception {
        seed();
        List<String> all = Arrays.stream(NativeParityCapability.values()).map(Enum::name).toList();
        List<String> sixteen = new ArrayList<>(all);
        sixteen.add("PERSISTENCE");
        assertThat(all).hasSize(15);
        assertThat(sixteen).hasSize(16);
        List<String> workspacesBefore = entriesUnder(root.resolve("integration-validation"));
        List<String> processWorkspacesBefore = entriesUnder(root.resolve("external-process-workspaces"));
        clearInvocations(compiler);

        certify("{\"capabilities\":[],\"timeoutSeconds\":5}", 400);
        certify("{\"capabilities\":[\"NATIVE_BUILD\",\"NATIVE_BUILD\"],\"timeoutSeconds\":5}", 400);
        certify("{\"capabilities\":[\"SHELL_ACCESS\"],\"timeoutSeconds\":5}", 400);
        certify("{\"capabilities\":[" + quoted(sixteen) + "],\"timeoutSeconds\":5}", 400);

        verify(compiler, times(0)).compile(any(TechnologyCompilationRequest.class));
        assertThat(entriesUnder(root.resolve("integration-validation"))).isEqualTo(workspacesBefore);
        assertThat(entriesUnder(root.resolve("external-process-workspaces"))).isEqualTo(processWorkspacesBefore);
        assertThatThrownBy(() -> new NativeParityCertificationRequest(sixteen.stream().map(NativeParityCapability::valueOf).toList(), 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("integration_native_parity.to: plazo 0 o negativo vale 180 s, 900 se acepta y 901 se rechaza por HTTP sin compilar")
    void timeoutIsDefaultedCappedAndRejectedBeforeCompiling() throws Exception {
        seed();
        List<NativeParityCapability> one = List.of(NativeParityCapability.NATIVE_BUILD);

        assertThat(new NativeParityCertificationRequest(one, 0).timeoutSeconds()).isEqualTo(180);
        assertThat(new NativeParityCertificationRequest(one, -1).timeoutSeconds()).isEqualTo(180);
        assertThat(new NativeParityCertificationRequest(one, 900).timeoutSeconds()).isEqualTo(900);
        assertThatThrownBy(() -> new NativeParityCertificationRequest(one, 901)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Native validation timeout exceeds 900 seconds");

        clearInvocations(compiler);
        certify("{\"capabilities\":[\"NATIVE_BUILD\"],\"timeoutSeconds\":901}", 400);
        verify(compiler, times(0)).compile(any(TechnologyCompilationRequest.class));
        certify("{\"capabilities\":[\"NATIVE_BUILD\"],\"timeoutSeconds\":900}", 200);
        certify("{\"capabilities\":[\"NATIVE_BUILD\"],\"timeoutSeconds\":0}", 200);
        verify(compiler, times(2)).compile(any(TechnologyCompilationRequest.class));
    }

    @Test
    @DisplayName("integration_native_parity.cmp: las quince capacidades en una sola peticion compilan el proyecto una unica vez")
    void fifteenCapabilitiesInOneRequestCompileTheProjectExactlyOnce() throws Exception {
        seed();
        List<String> all = Arrays.stream(NativeParityCapability.values()).map(Enum::name).toList();
        clearInvocations(compiler);

        String body = certify("{\"capabilities\":[" + quoted(all) + "],\"timeoutSeconds\":5}", 200);

        verify(compiler, times(1)).compile(any(TechnologyCompilationRequest.class));
        assertThat((Integer) JsonPath.read(body, "$.requestedCount")).isEqualTo(15);
        List<String> reported = JsonPath.read(body, "$.capabilities[*].capability");
        assertThat(reported).containsExactlyElementsOf(all);
    }

    @Test
    @DisplayName("integration_native_parity.ko: el informe no certificado nombra la capacidad sin verificar y la que fallo, con sus motivos")
    void uncertifiedReportNamesTheUnverifiedAndTheFailedCapability() throws Exception {
        seed();

        String body = certify("{\"capabilities\":[\"GRAPHICAL_EDITOR\",\"NATIVE_BUILD\"],\"timeoutSeconds\":5}", 200);

        assertThat((Boolean) JsonPath.read(body, "$.certified")).isFalse();
        assertThat((Integer) JsonPath.read(body, "$.provenCount")).isZero();
        assertThat((Integer) JsonPath.read(body, "$.requestedCount")).isEqualTo(2);
        assertThat((String) JsonPath.read(body, "$.technology")).isEqualTo(IntegrationTechnology.EMF.name());
        assertThat((String) JsonPath.read(body, "$.validationStatus")).isEqualTo("FAILED");
        assertThat((String) JsonPath.read(body, "$.capabilities[0].capability")).isEqualTo("GRAPHICAL_EDITOR");
        assertThat((String) JsonPath.read(body, "$.capabilities[0].status")).isEqualTo("UNVERIFIED");
        assertThat((String) JsonPath.read(body, "$.capabilities[0].explanation"))
                .isEqualTo("No generator-owned native behavioral fixture was provided");
        assertThat((String) JsonPath.read(body, "$.capabilities[1].capability")).isEqualTo("NATIVE_BUILD");
        assertThat((String) JsonPath.read(body, "$.capabilities[1].status")).isEqualTo("FAILED");
        assertThat((String) JsonPath.read(body, "$.capabilities[1].explanation")).isEqualTo("Native project validation did not pass");
        List<String> failedProofs = JsonPath.read(body, "$.capabilities[1].failedProofs");
        assertThat(failedProofs).containsExactly("compile-emf-module");
    }
}
