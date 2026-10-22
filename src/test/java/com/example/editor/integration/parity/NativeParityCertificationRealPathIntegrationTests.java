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

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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

import com.example.editor.integration.compiler.TechnologyCompilationRequest;
import com.example.editor.integration.compiler.TechnologyCompilerService;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.metamodel.web.MetamodelHttpTestEnvironment;
import com.jayway.jsonpath.JsonPath;

import tools.jackson.databind.json.JsonMapper;

/**
 * integration_native_parity.req / .to / .cmp and x_integrate_technology_native.sec - the native-parity certification
 * endpoint end to end, with no native toolchain available.
 *
 * Full Spring context + MockMvc. The only external executable allowed for the context is {@code git}, so the generated
 * project's Maven step is reported as unavailable instead of being launched: structural validation, compilation and the
 * assessor are real. The compiler is wrapped by a spy that delegates to the real one and is only used to count calls.
 * Oracles: literal HTTP statuses, literal report values, the number of compile calls and a marker file that a smuggled
 * shell command would have created.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NativeParityCertificationRealPathIntegrationTests {

    private static final String PROFILE_ID = "people-emf-parity";
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

    @Test
    @DisplayName("integration_native_parity.req: an empty, repeated or unknown capability list is rejected before anything is compiled")
    void invalidCapabilityListsAreRejectedBeforeCompiling() throws Exception {
        seed();
        clearInvocations(compiler);

        certify("{\"capabilities\":[],\"timeoutSeconds\":0}", 400);
        certify("{\"capabilities\":[\"NATIVE_BUILD\",\"NATIVE_BUILD\"],\"timeoutSeconds\":0}", 400);
        certify("{\"capabilities\":[\"SHELL_ACCESS\"],\"timeoutSeconds\":0}", 400);

        verify(compiler, times(0)).compile(any(TechnologyCompilationRequest.class));
        assertThatThrownBy(() -> new NativeParityCertificationRequest(List.of(), 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NativeParityCertificationRequest(
                List.of(NativeParityCapability.METAMODEL, NativeParityCapability.METAMODEL), 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("integration_native_parity.to: timeout zero or negative becomes 180 seconds, 900 is accepted and 901 is rejected over HTTP and in the request itself")
    void timeoutDefaultsToThreeMinutesAndIsCappedAtNineHundredSeconds() throws Exception {
        seed();
        List<NativeParityCapability> one = List.of(NativeParityCapability.NATIVE_BUILD);

        assertThat(new NativeParityCertificationRequest(one, 0).timeoutSeconds()).isEqualTo(180);
        assertThat(new NativeParityCertificationRequest(one, -1).timeoutSeconds()).isEqualTo(180);
        assertThat(new NativeParityCertificationRequest(one, 5).timeoutSeconds()).isEqualTo(5);
        assertThat(new NativeParityCertificationRequest(one, 900).timeoutSeconds()).isEqualTo(900);
        assertThatThrownBy(() -> new NativeParityCertificationRequest(one, 901)).isInstanceOf(IllegalArgumentException.class);

        certify("{\"capabilities\":[\"NATIVE_BUILD\"],\"timeoutSeconds\":901}", 400);
        certify("{\"capabilities\":[\"NATIVE_BUILD\"],\"timeoutSeconds\":900}", 200);
        certify("{\"capabilities\":[\"NATIVE_BUILD\"],\"timeoutSeconds\":-1}", 200);
    }

    @Test
    @DisplayName("integration_native_parity.cmp: the project is compiled once, validated as native-required and a capability without a generator fixture stays unverified")
    void compilesOnceAndLeavesAnUncoveredCapabilityUnverified() throws Exception {
        seed();
        clearInvocations(compiler);

        String body = certify("{\"capabilities\":[\"GRAPHICAL_EDITOR\"],\"timeoutSeconds\":5}", 200);

        verify(compiler, times(1)).compile(any(TechnologyCompilationRequest.class));
        assertThat((Boolean) JsonPath.read(body, "$.certified")).isFalse();
        assertThat((Integer) JsonPath.read(body, "$.provenCount")).isZero();
        assertThat((Integer) JsonPath.read(body, "$.requestedCount")).isEqualTo(1);
        assertThat((String) JsonPath.read(body, "$.technology")).isEqualTo(IntegrationTechnology.EMF.name());
        assertThat((String) JsonPath.read(body, "$.capabilities[0].capability")).isEqualTo("GRAPHICAL_EDITOR");
        assertThat((String) JsonPath.read(body, "$.capabilities[0].status")).isEqualTo("UNVERIFIED");
        assertThat((String) JsonPath.read(body, "$.capabilities[0].explanation")).contains("No generator-owned native behavioral fixture");
        // A structural-only validation of the same project passes; the native-required run fails because the build tool is unavailable.
        assertThat((String) JsonPath.read(body, "$.validationStatus")).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("x_integrate_technology_native.sec / integration_native_parity.req: all fifteen capabilities are accepted; the only registered proof is reported as failed because its executor is unavailable; nothing is certified")
    void allCapabilitiesAreAcceptedAndOnlyTheRegisteredProofIsReported() throws Exception {
        seed();
        List<String> all = Arrays.stream(NativeParityCapability.values()).map(Enum::name).collect(Collectors.toList());
        assertThat(all).hasSize(15);
        String names = all.stream().map(name -> "\"" + name + "\"").collect(Collectors.joining(","));

        String body = certify("{\"capabilities\":[" + names + "],\"timeoutSeconds\":0}", 200);

        assertThat((Integer) JsonPath.read(body, "$.requestedCount")).isEqualTo(15);
        assertThat((Integer) JsonPath.read(body, "$.provenCount")).isZero();
        assertThat((Boolean) JsonPath.read(body, "$.certified")).isFalse();
        List<String> build = JsonPath.read(body, "$.capabilities[?(@.capability=='NATIVE_BUILD')].status");
        assertThat(build).containsExactly("FAILED");
        List<List<String>> failedProofs = JsonPath.read(body, "$.capabilities[?(@.capability=='NATIVE_BUILD')].failedProofs");
        assertThat(failedProofs).containsExactly(List.of("compile-emf-module"));
        List<String> unverified = JsonPath.read(body, "$.capabilities[?(@.status=='UNVERIFIED')].capability");
        assertThat(unverified).hasSize(14).doesNotContain("NATIVE_BUILD");
        // The proof catalog is fixed: the client can name capabilities, never proofs.
        NativeParityProofCatalog catalog = new NativeParityProofCatalog();
        assertThat(catalog.proofIds(IntegrationTechnology.EMF, NativeParityCapability.NATIVE_BUILD)).containsExactly("compile-emf-module");
        assertThat(catalog.proofIds(IntegrationTechnology.EMF, NativeParityCapability.GRAPHICAL_EDITOR)).isEmpty();
    }

    @Test
    @DisplayName("x_integrate_technology_native.sec: commands or proof identifiers smuggled in the request never run anything")
    void smuggledCommandsAreNeverExecuted() throws Exception {
        seed();
        Path marker = root.resolve("smuggled-command-marker.txt");
        String body = "{\"capabilities\":[\"NATIVE_BUILD\"],\"timeoutSeconds\":5,"
                + "\"command\":[\"git\",\"init\",\"" + marker.getParent().resolve("smuggled-repo").toString().replace("\\", "/") + "\"],"
                + "\"proofIds\":[\"anything; touch " + marker.toString().replace("\\", "/") + "\"]}";

        int result = mockMvc.perform(post(technology(PROFILE_ID) + "/native-parity/certify").contentType(JSON).content(body))
                .andReturn().getResponse().getStatus();

        assertThat(result).isIn(200, 400);
        assertThat(marker).doesNotExist();
        assertThat(root.resolve("smuggled-repo")).doesNotExist();
    }
}
