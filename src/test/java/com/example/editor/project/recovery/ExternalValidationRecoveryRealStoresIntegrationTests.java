package com.example.editor.project.recovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.importing.MetamodelImportOptions;
import com.example.editor.metamodel.importing.MetamodelImportService;
import com.example.editor.metamodel.web.MetamodelHttpTestEnvironment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * recovery_lifecycle.a4 / recovery_lifecycle.v - reglas de validacion y correcciones recuperadas de un proyecto externo.
 *
 * Contexto Spring real: el servicio de recuperacion de validaciones se ejecuta con los servicios de aplicacion de
 * expresiones, acciones y validaciones reales sobre almacenes en un directorio temporal, para el .odesign y el .evl del
 * fixture Sirius completo. El oraculo son los documentos que quedan en disco, leidos con Jackson, y los recuentos que
 * hace el propio test sobre el texto del .evl y del .odesign. A diferencia del test con dobles, aqui se afirma lo que
 * realmente se guarda. La primera prueba (fuente enorme o binaria) va antes porque exige un disco sin documentos.
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExternalValidationRecoveryRealStoresIntegrationTests {
    private static final String FIXTURE = "/fixtures/sirius-complete/";
    private static final String ECORE = "com.hydra.sirius.fixture.metamodel/model/hydratest.ecore";
    private static final String ODESIGN = "com.hydra.sirius.fixture.design/description/hydratest.odesign";
    private static final String EVL = "com.hydra.sirius.fixture.validation/validation/hydratest.evl";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @TempDir
    static Path root;

    @DynamicPropertySource
    static void directories(DynamicPropertyRegistry registry) {
        MetamodelHttpTestEnvironment.register(registry, root);
    }

    @Autowired private ExternalValidationRecoveryService recovery;
    @Autowired private MetamodelImportService metamodelImports;

    private static byte[] resource(String relative) throws IOException {
        try (InputStream input = ExternalValidationRecoveryRealStoresIntegrationTests.class.getResourceAsStream(FIXTURE + relative)) {
            if (input == null) throw new IOException("Missing fixture resource: " + relative);
            return input.readAllBytes();
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static ExternalProjectArchiveEntry entry(String relative) throws Exception {
        byte[] bytes = resource(relative);
        return new ExternalProjectArchiveEntry(relative, bytes, bytes.length, sha256(bytes));
    }

    private static MetamodelImportOptions options() { return new MetamodelImportOptions("auto", "", false, true, true, false); }

    /** Documentos con este sufijo en cualquier almacen del directorio temporal. */
    private static List<Path> documents(String suffix) throws IOException {
        try (var walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(suffix)).sorted().toList();
        }
    }

    private static JsonNode readSingle(String suffix) throws IOException {
        List<Path> found = documents(suffix);
        assertThat(found).as("un unico documento %s", suffix).hasSize(1);
        return JSON.readTree(Files.readString(found.get(0)));
    }

    private static String text(JsonNode node) { return node == null || node.isNull() ? "" : node.asString(); }

    private static JsonNode byName(JsonNode array, String name) {
        for (JsonNode item : array) if (name.equals(text(item.get("name")))) return item;
        throw new AssertionError("No hay elemento con nombre " + name);
    }

    private static String annotation(JsonNode item, String key) { return text(item.get("annotations").get(key)); }

    private static long count(Pattern pattern, String source) {
        Matcher matcher = pattern.matcher(source);
        long total = 0;
        while (matcher.find()) total++;
        return total;
    }

    @Test
    @Order(1)
    @DisplayName("recovery_lifecycle.a4: una fuente enorme o binaria no se decodifica, no genera reglas y no deja documentos")
    void anOversizedOrBinarySourceProducesNoRulesAndNoDocuments() throws Exception {
        MetaModel metamodel = metamodelImports.preview("hydratest.ecore", "application/xml", resource(ECORE), options()).metamodel();
        byte[] oversized = new byte[StaticSourceInspectionPolicy.MAX_TEXT_SOURCE_BYTES + 1];

        ExternalValidationRecoveryResult result = recovery.recover(metamodel.id(), metamodel, List.of(
                new ExternalProjectArchiveEntry("validator.ts", oversized, oversized.length, sha256(oversized)),
                new ExternalProjectArchiveEntry("validator.bin", new byte[] {0, 1, 2, 3}, 4, "binary")));

        assertThat(result.rulesImported()).isZero();
        assertThat(result.quickFixesImported()).isZero();
        assertThat(result.diagnostics()).anyMatch(value -> value.contains("exceeds") && value.contains("validator.ts"));
        assertThat(documents(".validations.json")).isEmpty();
        assertThat(documents(".actions.json")).isEmpty();
        assertThat(documents(".expressions.json")).isEmpty();
    }

    @Test
    @Order(2)
    @DisplayName("recovery_lifecycle.a4 / v: las reglas y acciones recuperadas quedan en disco; solo lo traducido de forma segura esta activo y el resto queda en revision")
    void onlySafelyTranslatedRulesAndFixesAreEnabledOnDisk() throws Exception {
        var saved = metamodelImports.importAndSave("hydratest.ecore", "application/xml", resource(ECORE), options());
        MetaModel metamodel = saved.preview().metamodel();
        String evl = new String(resource(EVL), StandardCharsets.UTF_8);
        String odesign = new String(resource(ODESIGN), StandardCharsets.UTF_8);
        // Oraculo independiente: recuento de reglas y correcciones hecho por el test sobre el texto de las fuentes.
        Set<String> evlRuleNames = new LinkedHashSet<>();
        Matcher ruleMatcher = Pattern.compile("(?m)^\\s*(?:constraint|critique)\\s+([A-Za-z_][\\w-]*)\\s*\\{").matcher(evl);
        while (ruleMatcher.find()) evlRuleNames.add(ruleMatcher.group(1));
        long expectedFixes = count(Pattern.compile("(?m)^\\s*fix\\s*\\{"), evl) + count(Pattern.compile("<fixes\\s"), odesign);
        assertThat(evlRuleNames).as("reglas del .evl del fixture").hasSize(11);
        assertThat(expectedFixes).as("correcciones del .evl y del .odesign").isEqualTo(6);

        ExternalValidationRecoveryResult result = recovery.recover(saved.id(), metamodel, List.of(entry(ODESIGN), entry(EVL)));

        // Reglas de validacion guardadas.
        JsonNode validationDocument = readSingle(".validations.json");
        JsonNode rules = validationDocument.get("rules");
        Set<String> storedRuleNames = new LinkedHashSet<>();
        for (JsonNode rule : rules) storedRuleNames.add(text(rule.get("name")));
        assertThat(storedRuleNames).isEqualTo(evlRuleNames);
        assertThat(result.rulesImported()).isEqualTo(evlRuleNames.size());
        int disabledRules = 0;
        for (JsonNode rule : rules) {
            boolean enabled = rule.get("enabled").asBoolean();
            assertThat(annotation(rule, "hydra.import.safeTranslation")).as(text(rule.get("name"))).isEqualTo(Boolean.toString(enabled));
            if (enabled) {
                assertThat(text(rule.get("executionMode"))).isEqualTo("INCREMENTAL");
                assertThat(text(rule.get("conversionStatus"))).isEqualTo("FULL");
            } else {
                disabledRules++;
                assertThat(text(rule.get("executionMode"))).as(text(rule.get("name"))).isEqualTo("EXTERNAL");
                assertThat(annotation(rule, "hydra.import.reviewRequired")).isEqualTo("true");
            }
        }
        assertThat(byName(rules, "ProgressRange").get("enabled").asBoolean()).isTrue();
        assertThat(byName(rules, "PositiveDuration").get("enabled").asBoolean()).isTrue();
        JsonNode guarded = byName(rules, "NoSelfLoop");
        assertThat(guarded.get("enabled").asBoolean()).as("una guarda EVL no se traduce por conjetura").isFalse();
        assertThat(annotation(guarded, "hydra.import.originalGuard")).isNotBlank();
        assertThat(disabledRules).as("hay reglas que quedan en revision").isPositive();
        assertThat(readSingle(".expressions.json").get("expressions").size()).isPositive();

        // Acciones (correcciones) guardadas.
        JsonNode actions = readSingle(".actions.json").get("actions");
        assertThat(actions.size()).isEqualTo((int) expectedFixes);
        assertThat(result.quickFixesImported()).isEqualTo((int) expectedFixes);
        int reviewOnly = 0;
        for (JsonNode action : actions) {
            boolean enabled = action.get("enabled").asBoolean();
            assertThat(action.get("operations").size() > 0).as(text(action.get("name"))).isEqualTo(enabled);
            assertThat(annotation(action, "hydra.import.safeTranslation")).isEqualTo(Boolean.toString(enabled));
            assertThat(annotation(action, "hydra.import.reviewOnly")).isEqualTo(Boolean.toString(!enabled));
            if (!enabled) reviewOnly++;
        }
        assertThat(result.reviewOnlyQuickFixes()).isEqualTo(reviewOnly);
        assertThat(byName(actions, "Clamp progress to 100").get("enabled").asBoolean()).isTrue();
        assertThat(byName(actions, "Set duration to 1 minute").get("enabled").asBoolean()).isTrue();
        JsonNode generatedName = byName(actions, "Set generated name");
        assertThat(generatedName.get("enabled").asBoolean()).isFalse();
        assertThat(annotation(generatedName, "hydra.import.reviewOnly")).isEqualTo("true");

        // La regla ProgressRange queda enlazada a su correccion segura.
        String clampId = text(byName(actions, "Clamp progress to 100").get("id"));
        List<String> progressFixes = new java.util.ArrayList<>();
        for (JsonNode fixId : byName(rules, "ProgressRange").get("quickFixIds")) progressFixes.add(text(fixId));
        assertThat(progressFixes).contains(clampId);
    }
}
