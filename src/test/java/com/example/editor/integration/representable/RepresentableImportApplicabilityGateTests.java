package com.example.editor.integration.representable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Puerta «¿la vista previa es aplicable?» de la via de cambios representables: aplicar vuelve a calcular la vista previa
 * y se niega, sin escribir nada, cuando no es aplicable.
 *
 * <p>Etapa certificada (directa): {@code recovery_persist_rollback.d2}.</p>
 *
 * <p>Colaboradores reales: servicio de importacion representable, registro de adaptadores (Ecore y Xtext) y almacen en
 * disco sobre un {@code @TempDir}. Oraculo: el directorio se lista en el test antes y despues, y el paquete guardado se lee
 * del disco y se compara con literales escritos a mano.</p>
 */
class RepresentableImportApplicabilityGateTests {
    @TempDir Path temporary;

    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private RepresentableImportService service;

    @BeforeEach
    void setUp() {
        service = new RepresentableImportService(
                new RepresentableImportMapperRegistry(List.of(new EcoreRepresentableImportMapper(), new XtextRepresentableImportMapper())),
                new RepresentableImportStore(mapper), temporary.toString());
    }

    @Test
    @DisplayName("recovery_persist_rollback.d2: una vista previa no aplicable se rechaza al aplicar y no se escribe nada")
    void aPreviewThatIsNotApplicableIsRefusedWhenApplyingAndNothingIsWritten() throws Exception {
        // Un elemento representable junto a otro sin equivalente neutral: el conjunto no es aplicable.
        RepresentableImportRequest unsupported = new RepresentableImportRequest("ECORE", "shop", "SAFE", List.of(
                new ExternalChangeCandidate("class", ExternalChangeKind.ADD, "EClass", "shop.Customer", Map.of("abstract", false)),
                new ExternalChangeCandidate("annotation", ExternalChangeKind.ADD, "EAnnotation", "shop.Customer.source", Map.of())), Map.of());
        assertThat(service.preview(unsupported).applicable()).isFalse();
        assertThatThrownBy(() -> service.apply(unsupported))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("blocking diagnostics");
        assertThat(storedFiles()).isEmpty();

        // Reutilizar un elemento existente sin indicar cual tampoco es aplicable.
        RepresentableImportRequest missingTarget = new RepresentableImportRequest("ECORE", "shop", "SAFE", List.of(
                new ExternalChangeCandidate("customer", ExternalChangeKind.ADD, "EClass", "external.Customer",
                        Map.of("hydra:resolution", "MAP_EXISTING"))), Map.of());
        RepresentableImportPreview preview = service.preview(missingTarget);
        assertThat(preview.applicable()).isFalse();
        assertThat(preview.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("TARGET_ELEMENT_MISSING");
            assertThat(diagnostic.representability()).isEqualTo(Representability.NONE);
        });
        assertThatThrownBy(() -> service.apply(missingTarget)).isInstanceOf(IllegalStateException.class);
        assertThat(storedFiles()).isEmpty();
    }

    @Test
    @DisplayName("recovery_persist_rollback.d2: una vista previa aplicable se vuelve a calcular y se guarda como un unico paquete neutral")
    void anApplicablePreviewIsRecomputedAndSavedAsOneNeutralPackage() throws Exception {
        RepresentableImportRequest request = new RepresentableImportRequest("ECORE", "shop", "STRICT", List.of(
                new ExternalChangeCandidate("b-name", ExternalChangeKind.UPDATE, "EAttribute", "shop.Customer.name", Map.of("type", "EString")),
                new ExternalChangeCandidate("a-class", ExternalChangeKind.ADD, "EClass", "shop.Customer", Map.of("abstract", false))), Map.of());

        // Previsualizar no guarda.
        RepresentableImportPreview preview = service.preview(request);
        assertThat(preview.applicable()).isTrue();
        assertThat(storedFiles()).isEmpty();

        // Aplicar recalcula la misma vista previa y la guarda.
        RepresentableImportPreview applied = service.apply(request);
        assertThat(applied.applicable()).isTrue();
        assertThat(applied.previewId()).isEqualTo(preview.previewId()).matches("[0-9a-f]{24}");
        assertThat(storedFiles()).containsExactly(applied.previewId() + ".representable-import.json");

        JsonNode stored = mapper.readTree(Files.readString(temporary.resolve(applied.previewId() + ".representable-import.json")));
        assertThat(stored.get("applicable").asBoolean()).isTrue();
        assertThat(stored.get("technology").asString()).isEqualTo("ECORE");
        assertThat(stored.get("metamodelId").asString()).isEqualTo("shop");
        List<String> externalIds = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        List<String> kinds = new ArrayList<>();
        for (JsonNode operation : stored.get("operations")) {
            externalIds.add(operation.get("externalId").asString());
            paths.add(operation.get("neutralPath").asString());
            kinds.add(operation.get("elementKind").asString());
        }
        assertThat(externalIds).containsExactly("a-class", "b-name");
        assertThat(paths).containsExactly("shop.Customer", "shop.Customer.name");
        assertThat(kinds).containsExactly("CLASS", "ATTRIBUTE");
        assertThat(stored.get("operations").get(0).get("values").get("abstract").asBoolean()).isFalse();
        assertThat(stored.get("operations").get(1).get("values").get("type").asString()).isEqualTo("EString");
        assertThat(stored.get("diagnostics").size()).isEqualTo(2);
        for (JsonNode diagnostic : stored.get("diagnostics")) {
            assertThat(diagnostic.get("representability").asString()).isEqualTo("FULL");
        }
    }

    @Test
    @DisplayName("recovery_persist_rollback.d2: la estrategia estricta rechaza lo parcialmente representable que la segura acepta")
    void theStrictStrategyRefusesPartiallyRepresentableChangesThatTheSafeStrategyAccepts() throws Exception {
        List<ExternalChangeCandidate> changes = List.of(new ExternalChangeCandidate("rule", ExternalChangeKind.ADD, "ParserRule",
                "shop.Customer", Map.of()));

        // Estricta primero, con el directorio vacio: se rechaza y sigue vacio.
        RepresentableImportRequest strict = new RepresentableImportRequest("XTEXT", "shop", "STRICT", changes, Map.of());
        assertThat(service.preview(strict).applicable()).isFalse();
        assertThatThrownBy(() -> service.apply(strict)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("blocking diagnostics");
        assertThat(storedFiles()).isEmpty();

        // Segura: es aplicable, se guarda y el paquete declara la representacion parcial.
        RepresentableImportPreview applied = service.apply(new RepresentableImportRequest("XTEXT", "shop", "SAFE", changes, Map.of()));
        assertThat(applied.applicable()).isTrue();
        assertThat(storedFiles()).containsExactly(applied.previewId() + ".representable-import.json");
        JsonNode stored = mapper.readTree(Files.readString(temporary.resolve(applied.previewId() + ".representable-import.json")));
        assertThat(stored.get("diagnostics").size()).isEqualTo(1);
        assertThat(stored.get("diagnostics").get(0).get("representability").asString()).isEqualTo("PARTIAL");
        assertThat(stored.get("diagnostics").get(0).get("code").asString()).isEqualTo("REPRESENTATION_PARTIAL");
        assertThat(stored.get("operations").get(0).get("elementKind").asString()).isEqualTo("GRAMMAR_RULE");
    }

    private List<String> storedFiles() throws IOException {
        try (Stream<Path> files = Files.list(temporary)) {
            return files.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }
}
