package com.example.editor.transformation.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.config.StorageProperties;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.semantic.expression.ExpressionDependencyAnalyzer;
import com.example.editor.semantic.expression.ExpressionTestFixtures;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
import com.example.editor.transformation.TransformationDefinitionChecker;
import com.example.editor.transformation.core.TransformationBinding;
import com.example.editor.transformation.core.TransformationBindingKind;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationEndpoint;
import com.example.editor.transformation.core.TransformationEndpointRole;
import com.example.editor.transformation.core.TransformationGenerationRule;
import com.example.editor.transformation.core.TransformationKind;
import com.example.editor.transformation.core.TransformationRule;
import com.example.editor.transformation.core.TransformationTargetPattern;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia real de x_transformation_run_and_sync.exp para el lenguaje Java: el servicio de exportacion real escribe el
 * paquete en un directorio temporal, el compilador del JDK (javax.tools) compila de verdad la clase exportada, se carga y
 * se invoca, y el plan exportado se ejecuta con un interprete minimo escrito en la propia prueba (independiente del motor
 * de HYDRA) cuyo resultado se compara con un modelo esperado escrito a mano. El puente Java oficial que ejecutaria el plan es
 * una herramienta gestionada externa y no se usa aqui; ATL, ETL, EGL y Acceleo siguen sin ejecutarse.
 */
class TransformationExportCompiledJavaRuntimeTests {
    private static final String NAME = ExpressionTestFixtures.NAME;
    private static final String AGE = ExpressionTestFixtures.AGE;
    private static final ObjectMapper PLAIN = new ObjectMapper();

    @Test
    @DisplayName("x_transformation_run_and_sync.exp: la clase Java exportada compila con javax.tools y su plan, interpretado aparte, produce el modelo esperado")
    void theExportedJavaClassCompilesAndItsPlanProducesTheExpectedModel(@TempDir Path temporary) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "se necesita un JDK con el compilador del sistema");
        TransformationExportService exports = exportService(temporary);

        TransformationExportResult result = exports.export(new TransformationExportRequest(m2m(false), TransformationExportLanguage.JAVA));

        assertThat(result.compatible()).isTrue();
        assertThat(result.fidelity()).isEqualTo(TransformationExportFidelity.EXACT);
        Path bundle = write(temporary.resolve("bundle-m2m"), result);

        // Compilacion real de la clase exportada y ejecucion de sus metodos publicos.
        Path classes = Files.createDirectories(temporary.resolve("classes-m2m"));
        compile(compiler, bundle.resolve("src/main/java/generated/HydraTransformation.java"), classes);
        try (URLClassLoader loader = new URLClassLoader(new URL[] { classes.toUri().toURL() })) {
            Class<?> exported = loader.loadClass("generated.HydraTransformation");
            assertThat(exported.getMethod("transformationId").invoke(null)).isEqualTo("transformation-export-java-m2m");
            assertThat(exported.getMethod("transformationKind").invoke(null)).isEqualTo("M2M");
            Path definition = (Path) exported.getMethod("canonicalDefinition", Path.class).invoke(null, bundle);
            assertThat(definition).isEqualTo(bundle.resolve("transformation.json")).isRegularFile();
            assertThat(PLAIN.readTree(Files.readString(definition)).get("id").asString()).isEqualTo("transformation-export-java-m2m");
        }

        // Ejecucion del plan exportado con un interprete propio y comparacion con el resultado esperado a mano.
        JsonNode plan = PLAIN.readTree(Files.readString(bundle.resolve("native/native-plan.json")));
        assertThat(plan.get("kind").asString()).isEqualTo("M2M");
        List<Map<String, Object>> produced = interpretM2m(plan, ExpressionTestFixtures.model());
        // Alice es Person y Bob es Employee (hereda de Person): ambos producen una Person; la empresa no encaja en la regla.
        assertThat(produced).hasSize(2);
        assertThat(produced.get(0)).containsEntry("class", ExpressionTestFixtures.PERSON).containsEntry(NAME, "Alice").containsEntry(AGE, 35);
        assertThat(produced.get(1)).containsEntry("class", ExpressionTestFixtures.PERSON).containsEntry(NAME, "Bob").containsEntry(AGE, -2);
    }

    @Test
    @DisplayName("x_transformation_run_and_sync.exp: la exportacion Java de una generacion de texto compila y su plan genera los ficheros esperados")
    void theExportedTextGenerationClassCompilesAndItsPlanRendersTheExpectedFiles(@TempDir Path temporary) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "se necesita un JDK con el compilador del sistema");
        TransformationExportService exports = exportService(temporary);

        TransformationExportResult result = exports.export(new TransformationExportRequest(m2t(), TransformationExportLanguage.JAVA));

        assertThat(result.compatible()).isTrue();
        Path bundle = write(temporary.resolve("bundle-m2t"), result);
        Path classes = Files.createDirectories(temporary.resolve("classes-m2t"));
        compile(compiler, bundle.resolve("src/main/java/generated/HydraTransformation.java"), classes);
        try (URLClassLoader loader = new URLClassLoader(new URL[] { classes.toUri().toURL() })) {
            Class<?> exported = loader.loadClass("generated.HydraTransformation");
            assertThat(exported.getMethod("transformationKind").invoke(null)).isEqualTo("M2T");
        }

        JsonNode plan = PLAIN.readTree(Files.readString(bundle.resolve("native/native-plan.json")));
        CanonicalModelElement ana = new CanonicalModelElement("object:ana", ExpressionTestFixtures.PERSON, Map.of(NAME, "Ana"), Map.of());
        CanonicalModelElement luis = new CanonicalModelElement("object:luis", ExpressionTestFixtures.PERSON, Map.of(NAME, "Luis"), Map.of());
        CanonicalModelDocument model = new CanonicalModelDocument("1.0", "text-source", ExpressionTestFixtures.METAMODEL_ID, "", "Text source",
                List.of("object:ana", "object:luis"), List.of(ana, luis), List.of(), Map.of());
        Map<String, String> files = interpretM2t(plan, model);
        assertThat(files).containsOnlyKeys("out/Ana.txt", "out/Luis.txt");
        assertThat(files).containsEntry("out/Ana.txt", "Hello Ana").containsEntry("out/Luis.txt", "Hello Luis");
    }

    @Test
    @DisplayName("x_transformation_run_and_sync.exp: una regla con condicion no se puede exportar a Java, queda bloqueada y no hay nada que compilar")
    void aGuardedRuleIsBlockedAndLeavesNothingToCompile(@TempDir Path temporary) throws Exception {
        TransformationExportService exports = exportService(temporary);

        TransformationExportResult blocked = exports.export(new TransformationExportRequest(m2m(true), TransformationExportLanguage.JAVA));

        assertThat(blocked.compatible()).isFalse();
        assertThat(blocked.artifacts()).isEmpty();
        assertThat(blocked.diagnostics()).extracting(TransformationExportDiagnostic::code).contains("GUARD_NOT_NATIVE");
        assertThat(blocked.diagnostics()).allSatisfy(diagnostic -> assertThat(diagnostic.severity()).isEqualTo("ERROR"));
        Path directory = write(temporary.resolve("bundle-blocked"), blocked);
        try (var walk = Files.walk(directory)) {
            assertThat(walk.filter(Files::isRegularFile).toList()).isEmpty();
        }
    }

    // ----------------------------------------------------------------------- interpretes independientes

    private static void compile(JavaCompiler compiler, Path source, Path classes) {
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int code = compiler.run(null, null, errors, "-d", classes.toString(), source.toString());
        assertThat(code).as("salida del compilador: " + errors.toString(StandardCharsets.UTF_8)).isZero();
        assertThat(classes.resolve("generated/HydraTransformation.class")).isRegularFile();
    }

    /** Interprete minimo del plan M2M: copia atributos DIRECT, asigna CONSTANT y respeta la herencia declarada en el plan. */
    private static List<Map<String, Object>> interpretM2m(JsonNode plan, CanonicalModelDocument source) {
        List<Map<String, Object>> produced = new ArrayList<>();
        JsonNode rules = plan.get("rules");
        for (CanonicalModelElement element : source.elements()) {
            for (int r = 0; r < rules.size(); r++) {
                JsonNode rule = rules.get(r);
                boolean matches = false;
                for (int t = 0; t < rule.get("sourceTypeIds").size(); t++) {
                    if (rule.get("sourceTypeIds").get(t).asString().equals(element.classId())) matches = true;
                }
                if (!matches) continue;
                Map<String, Object> created = new LinkedHashMap<>();
                created.put("class", rule.get("targetTypeId").asString());
                JsonNode bindings = rule.get("bindings");
                for (int b = 0; b < bindings.size(); b++) {
                    JsonNode binding = bindings.get(b);
                    if ("DIRECT".equals(binding.get("kind").asString())) {
                        created.put(binding.get("targetFeatureId").asString(), element.attributes().get(binding.get("sourceFeatureId").asString()));
                    }
                }
                produced.add(created);
            }
        }
        return produced;
    }

    /** Interprete minimo del plan M2T: sustituye el token de atributo en la ruta y en el contenido. */
    private static Map<String, String> interpretM2t(JsonNode plan, CanonicalModelDocument source) {
        Map<String, String> files = new LinkedHashMap<>();
        JsonNode rules = plan.get("generationRules");
        for (CanonicalModelElement element : source.elements()) {
            for (int r = 0; r < rules.size(); r++) {
                JsonNode rule = rules.get(r);
                if (!rule.get("contextTypeId").asString().equals(element.classId())) continue;
                String token = "{{attr:" + NAME + "}}";
                String value = String.valueOf(element.attributes().get(NAME));
                files.put(rule.get("outputPathTemplate").asString().replace(token, value), rule.get("contentTemplate").asString().replace(token, value));
            }
        }
        return files;
    }

    private static Path write(Path directory, TransformationExportResult result) throws IOException {
        Files.createDirectories(directory);
        for (TransformationExportArtifact artifact : result.artifacts()) {
            Path file = directory.resolve(artifact.path()).normalize();
            if (!file.startsWith(directory)) throw new IllegalStateException("artifact escapes the export directory: " + artifact.path());
            Files.createDirectories(file.getParent());
            Files.writeString(file, artifact.content(), StandardCharsets.UTF_8);
        }
        return directory;
    }

    // ----------------------------------------------------------------------------------- fixtures

    private static TransformationDefinitionDocument m2m(boolean guarded) {
        List<TransformationBinding> bindings = List.of(
                new TransformationBinding("binding:name", "person", NAME, TransformationBindingKind.DIRECT, NAME, null, null, null, Map.of()),
                new TransformationBinding("binding:age", "person", AGE, TransformationBindingKind.DIRECT, AGE, null, null, null, Map.of()));
        TransformationRule rule = new TransformationRule("rule:person", "Person to Person", true, "source", ExpressionTestFixtures.PERSON,
                "personSource", guarded ? ExpressionTestFixtures.positiveAge() : null,
                List.of(new TransformationTargetPattern("target:person", "target", ExpressionTestFixtures.PERSON, "person", Map.of())),
                bindings, Map.of());
        return new TransformationDefinitionDocument("1.0", "transformation-export-java-m2m", "Java export", TransformationKind.M2M,
                List.of(new TransformationEndpoint("source", TransformationEndpointRole.SOURCE, ExpressionTestFixtures.METAMODEL_ID, "", "source", true, Map.of())),
                List.of(new TransformationEndpoint("target", TransformationEndpointRole.TARGET, ExpressionTestFixtures.METAMODEL_ID, "", "target", true, Map.of())),
                List.of(rule), List.of(), null, null, null, Map.of());
    }

    private static TransformationDefinitionDocument m2t() {
        TransformationGenerationRule rule = new TransformationGenerationRule("gen:person", "Generate person", true, "source",
                ExpressionTestFixtures.PERSON, null, "out/{{attr:" + NAME + "}}.txt", "Hello {{attr:" + NAME + "}}", null, Map.of());
        return new TransformationDefinitionDocument("1.0", "transformation-export-java-m2t", "Java text export", TransformationKind.M2T,
                List.of(new TransformationEndpoint("source", TransformationEndpointRole.SOURCE, ExpressionTestFixtures.METAMODEL_ID, "", "source", true, Map.of())),
                List.of(), List.of(), List.of(rule), null, null, null, Map.of());
    }

    private static TransformationExportService exportService(Path temporary) {
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        StorageProperties storage = new StorageProperties(temporary.resolve("metamodels"), temporary.resolve("models"),
                temporary.resolve("notations"), temporary.resolve("validations"), temporary.resolve("quickfixes"),
                temporary.resolve("generators"), temporary.resolve("assets"), temporary.resolve("exports"), true);
        CanonicalMetamodelApplicationService metamodels = new CanonicalMetamodelApplicationService(
                new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());
        metamodels.create(ExpressionTestFixtures.metamodel());
        TransformationDefinitionChecker checker = new TransformationDefinitionChecker(new ExpressionTypeChecker(new ExpressionDependencyAnalyzer()));
        TransformationLanguageAdapterRegistry registry = new TransformationLanguageAdapterRegistry(List.of(new JavaTransformationLanguageAdapter(PLAIN)));
        return new TransformationExportService(registry, checker, metamodels);
    }
}
