package com.example.editor.codegen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.example.editor.definition.CodeGenerationFeatureOptions;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.importing.sql.SqlDdlImportPipeline;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Eleccion del destino de generacion y forma exacta de la peticion (un metamodelo, un modelo, un destino).
 *
 * Etapas: x_generate_certify_distribute.tgt (directa), publication_author_codegen.t (solo la parte de servicio: el
 * dialogo de seleccion de destino es interfaz y no se ejerce aqui).
 * Oraculos: lista literal de los 21 destinos con su extension, catalogo leido por HTTP y artefactos generados uno a uno
 * con nombre, tipo de medio y contenido comprobados contra literales; mensajes de rechazo literales.
 */
class ModelCodeGenerationTargetRequestRuntimeTests {
    /** Destino -> extension del fichero generado, escrito a mano (no se pide al catalogo de produccion). */
    private static final Map<String, String> EXPECTED_EXTENSIONS = new LinkedHashMap<>();
    static {
        EXPECTED_EXTENSIONS.put("ecore", ".model.ecore.xml");
        EXPECTED_EXTENSIONS.put("emf", ".model.emf");
        EXPECTED_EXTENSIONS.put("xmi", ".xmi");
        EXPECTED_EXTENSIONS.put("json", ".model.json");
        EXPECTED_EXTENSIONS.put("xml", ".model.xml");
        EXPECTED_EXTENSIONS.put("drawio", ".drawio");
        EXPECTED_EXTENSIONS.put("langium", ".langium.model");
        EXPECTED_EXTENSIONS.put("xtext", ".xtext.model");
        EXPECTED_EXTENSIONS.put("antlr", ".g4.model");
        EXPECTED_EXTENSIONS.put("sql-ddl", ".sql");
        EXPECTED_EXTENSIONS.put("java", ".java");
        EXPECTED_EXTENSIONS.put("typescript", ".ts");
        EXPECTED_EXTENSIONS.put("javascript", ".js");
        EXPECTED_EXTENSIONS.put("go", ".go");
        EXPECTED_EXTENSIONS.put("python", ".py");
        EXPECTED_EXTENSIONS.put("blockly-json", ".blockly.json");
        EXPECTED_EXTENSIONS.put("blockly-javascript", ".blockly.js");
        EXPECTED_EXTENSIONS.put("glsp", ".glsp.json");
        EXPECTED_EXTENSIONS.put("plantuml", ".puml");
        EXPECTED_EXTENSIONS.put("markdown", ".md");
        EXPECTED_EXTENSIONS.put("markdown-agent", ".agent.md");
    }

    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
    private final ModelCodeGenerationService service = new ModelCodeGenerationService(json);

    private MetaModel sqlMetamodel() {
        return new SqlDdlImportPipeline()
                .importDdl("CREATE TABLE users(id BIGINT PRIMARY KEY, name VARCHAR(80) NOT NULL);", "users.sql", "users-schema")
                .metamodel();
    }

    private CanonicalModelDocument sqlModel(MetaModel metamodel) {
        String classId = metamodel.classes().get(0).id();
        return new CanonicalModelDocument(null, "model1", "users-schema", "", "UsersData", List.of("user1"),
                List.of(new CanonicalModelElement("user1", classId, Map.of("id", 7L, "name", "O'Reilly"), Map.of())),
                List.of(), Map.of());
    }

    private MetaModel peopleMetamodel() {
        return new MetaModel("2.1", "people", "People",
                List.of(new MetaClass("Person", "Person", MetaClassKind.CLASS, List.of(), List.of(), Map.of())),
                List.of(), List.of(), List.of(), Map.of());
    }

    private CanonicalModelDocument peopleModel() {
        var person = new CanonicalModelElement("person-one", "Person", Map.of("name", "Alice"), Map.of());
        return new CanonicalModelDocument("1.0", "people-model", "people", "", "PeopleModel", List.of("person-one"),
                List.of(person), List.of(), Map.of());
    }

    @Test
    @DisplayName("x_generate_certify_distribute.tgt / publication_author_codegen.t: el catalogo HTTP ofrece los 21 destinos y cada uno genera su propio artefacto")
    void theHttpCatalogOffersEveryTargetAndEachChosenTargetProducesItsOwnArtifact() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ModelCodeGenerationCatalogController(service)).build();
        MockHttpServletResponse response = mvc.perform(get("/api/code-generation/targets")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode catalog = json.readTree(response.getContentAsString(StandardCharsets.UTF_8));
        List<String> ids = new ArrayList<>();
        for (JsonNode item : catalog) {
            String id = item.get("id").asString();
            ids.add(id);
            assertThat(item.get("extension").asString()).as(id).isEqualTo(EXPECTED_EXTENSIONS.get(id));
        }
        assertThat(ids).containsExactlyInAnyOrderElementsOf(EXPECTED_EXTENSIONS.keySet());
        assertThat(EXPECTED_EXTENSIONS).hasSize(21);

        MetaModel metamodel = sqlMetamodel();
        CanonicalModelDocument model = sqlModel(metamodel);
        ModelCodeGenerationOptions options = new ModelCodeGenerationOptions("example.generated", "UsersData", true,
                CodeGenerationFeatureOptions.none());
        for (Map.Entry<String, String> expected : EXPECTED_EXTENSIONS.entrySet()) {
            GeneratedModelArtifact artifact = service.generate(metamodel, model, expected.getKey(), options);
            assertThat(artifact.fileName()).as(expected.getKey()).isEqualTo("UsersData" + expected.getValue());
            String text = new String(artifact.content(), StandardCharsets.UTF_8);
            if (expected.getKey().equals("sql-ddl")) {
                assertThat(artifact.mediaType()).isEqualTo("text/sql");
                assertThat(text).contains("INSERT INTO \"users\"").contains("'O''Reilly'");
            } else {
                assertThat(text).as(expected.getKey()).isNotBlank();
                assertThat(text.contains("user1") || text.contains("Reilly")).as(expected.getKey() + " carries the model").isTrue();
            }
        }
        assertThat(service.generate(metamodel, model, "json", options).mediaType()).isEqualTo("application/json");
        assertThat(service.generate(metamodel, model, "xmi", options).mediaType()).isEqualTo("application/xml");
        assertThat(service.generate(metamodel, model, "java", options).mediaType()).isEqualTo("text/x-java-source");
    }

    @Test
    @DisplayName("x_generate_certify_distribute.tgt: la peticion exige un metamodelo, un modelo del mismo metamodelo y un destino conocido")
    void aRequestNeedsOneMetamodelOneMatchingModelAndOneKnownTarget() {
        MetaModel people = peopleMetamodel();
        CanonicalModelDocument model = peopleModel();
        ModelCodeGenerationOptions options = ModelCodeGenerationOptions.defaults();

        assertThatThrownBy(() -> service.generate(null, model, "json", options))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Metamodel and model are required for code generation");
        assertThatThrownBy(() -> service.generate(people, null, "json", options))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Metamodel and model are required for code generation");

        MetaModel other = new MetaModel("2.1", "other", "Other", List.of(), List.of(), List.of(), List.of(), Map.of());
        assertThatThrownBy(() -> service.generate(other, model, "json", options))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The model does not belong to the active metamodel");

        assertThatThrownBy(() -> service.generate(people, model, "cobol", options))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported model code generation target: cobol");
        assertThatThrownBy(() -> service.preview(people, model, "cobol", options, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported model code generation target: cobol");

        // La misma peticion con los tres datos correctos si genera: el rechazo no es un fallo general.
        assertThat(service.generate(people, model, "json", options).fileName()).isEqualTo("GeneratedModel.model.json");
    }

    @Test
    @DisplayName("x_generate_certify_distribute.tgt: namespace, nombre, trazabilidad y previsualizacion son opcionales y se aplican al destino elegido")
    void optionalNamespaceNameTraceabilityAndPreviewApplyToTheChosenTarget() {
        MetaModel people = peopleMetamodel();
        CanonicalModelDocument model = peopleModel();

        // Sin opciones: valores por defecto literales.
        GeneratedModelArtifact defaults = service.generate(people, model, "java", null);
        assertThat(defaults.fileName()).isEqualTo("GeneratedModel.java");
        String defaultSource = new String(defaults.content(), StandardCharsets.UTF_8);
        assertThat(defaultSource).contains("generated.model").contains("HYDRA trace:");

        // Con opciones: namespace y nombre escritos por quien pide, sin trazabilidad.
        ModelCodeGenerationOptions custom = new ModelCodeGenerationOptions("example.generated", "Registry", false,
                CodeGenerationFeatureOptions.none());
        GeneratedModelArtifact named = service.generate(people, model, "java", custom);
        assertThat(named.fileName()).isEqualTo("Registry.java");
        String namedSource = new String(named.content(), StandardCharsets.UTF_8);
        assertThat(namedSource).contains("example.generated").doesNotContain("generated.model").doesNotContain("HYDRA trace:");

        // Previsualizar con casilla de tests: devuelve la fuente principal, no el paquete con tests.
        ModelCodeGenerationOptions withTests = new ModelCodeGenerationOptions("example.generated", "Registry", false,
                new CodeGenerationFeatureOptions(true, false, false, false, false));
        ModelCodeGenerationPreview preview = service.preview(people, model, "java", withTests, List.of());
        assertThat(preview.fileName()).isEqualTo("Registry.java");
        assertThat(preview.mediaType()).isEqualTo("text/x-java-source");
        assertThat(preview.selectedElementCount()).isEqualTo(1);
        assertThat(preview.content()).contains("example.generated").doesNotContain("Generated model collections must exist");
        GeneratedModelArtifact bundle = service.generate(people, model, "java", withTests);
        assertThat(bundle.fileName()).isEqualTo("Registry-java.zip");
        assertThat(bundle.mediaType()).isEqualTo("application/zip");
    }

    @Test
    @DisplayName("publication_author_codegen.t: el catalogo se puede acotar a los destinos permitidos del editor exportado")
    void theCatalogCanBeNarrowedToTheTargetsAnExportedEditorAllows() {
        assertThat(service.targets(Set.of("java", "json")).stream().map(ModelCodeGenerationTarget::id).toList())
                .containsExactlyInAnyOrder("java", "json");
        assertThat(service.targets(Set.of())).hasSize(21);
        assertThat(service.targets(null)).hasSize(21);
        assertThat(service.targets(Set.of("not-a-target"))).isEmpty();
    }
}
