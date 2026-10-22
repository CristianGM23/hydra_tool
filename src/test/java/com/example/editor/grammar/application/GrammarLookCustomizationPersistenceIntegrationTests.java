package com.example.editor.grammar.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.grammar.GrammarProperties;
import com.example.editor.grammar.core.CompilationStatus;
import com.example.editor.grammar.core.GrammarAuthoringMode;
import com.example.editor.grammar.core.GrammarCompilationRequest;
import com.example.editor.grammar.core.GrammarDefinitionModel;
import com.example.editor.grammar.core.GrammarSemanticBindingMap;
import com.example.editor.grammar.core.GrammarSourceDocument;
import com.example.editor.grammar.core.GrammarTechnology;
import com.example.editor.grammar.core.GrammarTextualEditorCustomization;
import com.example.editor.grammar.persistence.JsonGrammarActivationRepository;
import com.example.editor.grammar.persistence.JsonGrammarCompilationRepository;
import com.example.editor.grammar.persistence.JsonGrammarRepository;
import com.example.editor.grammar.provider.GrammarProviderRegistry;
import com.example.editor.grammar.provider.antlr.AntlrGrammarProvider;
import com.example.editor.grammar.provider.langium.LangiumGrammarProvider;
import com.example.editor.grammar.provider.xtext.XtextGrammarProvider;
import com.example.editor.grammar.runtime.GrammarCompilationSnapshot;
import com.example.editor.grammar.visualization.GrammarClassDiagramProjectionService;
import com.example.editor.integration.verification.ExternalToolCircuitBreaker;
import com.example.editor.integration.verification.ExternalToolProcessRunner;
import com.example.editor.persistence.LocalCanonicalDocumentStoreFactory;
import com.example.editor.security.process.HydraExternalProcessPolicy;
import com.example.editor.security.process.HydraExternalProcessSecurityProperties;
import com.example.editor.security.process.HydraTemporaryWorkspace;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia real de la personalizacion del aspecto del editor textual guardada en los ajustes de la
 * gramatica: grammar_author_look.f (fuente y tamano), .k (palabras clave), .ro (estilos por rol y por
 * regla/terminal), .h (descripciones emergentes) y .sv (guardado y relectura desde disco, con las claves
 * ajenas conservadas y sin efecto sobre la compilacion). Los valores peligrosos se guardan tal cual y se
 * acotan al restaurarlos; los literales esperados del test son el oraculo.
 */
class GrammarLookCustomizationPersistenceIntegrationTests {

    private static final String DEFAULT_FONT = "ui-monospace, SFMono-Regular, Menlo, Consolas, monospace";
    private static final String SOURCE = "grammar Stable\nentry Model: nodes+=Node*;\nNode: 'node' name=ID;\n";

    private GrammarApplicationService grammars;
    private GrammarCompilationService service;

    private void wire(Path temporary) {
        GrammarProperties properties = new GrammarProperties();
        properties.setDirectory(temporary.resolve("grammars"));
        properties.setCompilationDirectory(temporary.resolve("grammar-compilations"));
        properties.setActivationDirectory(temporary.resolve("grammar-activations"));
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        LocalCanonicalDocumentStoreFactory stores = new LocalCanonicalDocumentStoreFactory();
        GrammarToolchainProfileService profiles = new GrammarToolchainProfileService();
        GrammarProviderRegistry providers = new GrammarProviderRegistry(List.of(
                new AntlrGrammarProvider(properties), new LangiumGrammarProvider(properties), new XtextGrammarProvider(properties)));
        grammars = new GrammarApplicationService(new JsonGrammarRepository(properties, mapper, stores), properties, profiles, providers);
        GrammarAnalysisCoordinator analysis = new GrammarAnalysisCoordinator(profiles, providers, new GrammarAnalysisCache(properties),
                new com.example.editor.grammar.runtime.GrammarProviderHealthService(properties), properties);
        GrammarNativeToolchainService nativeToolchain = new GrammarNativeToolchainService(
                new ExternalToolProcessRunner(new ExternalToolCircuitBreaker()),
                new HydraTemporaryWorkspace(new HydraExternalProcessPolicy(new HydraExternalProcessSecurityProperties())), properties);
        service = new GrammarCompilationService(grammars, profiles, providers, analysis,
                new JsonGrammarCompilationRepository(properties, mapper, stores), new JsonGrammarActivationRepository(properties, mapper, stores),
                nativeToolchain, new GrammarClassDiagramProjectionService(properties), properties, new GrammarCompilationProgressService(properties));
    }

    private static GrammarSourceDocument document() {
        return new GrammarSourceDocument("main", "main.langium", "langium", SOURCE, "", true, false);
    }

    private static GrammarDefinitionModel grammar(String id, Map<String, String> settings) {
        return new GrammarDefinitionModel("1.0", id, id, GrammarTechnology.LANGIUM, GrammarAuthoringMode.GRAMMAR_FIRST, "",
                "langium-4.3-browser", "main", "Model", List.of(document()), GrammarSemanticBindingMap.empty(), "", settings, Map.of());
    }

    private static Map<String, String> validSettings() {
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("custom.other.key", "keep-me");
        settings.put("hydra.textual.fontFamily", "Fira Code, monospace");
        settings.put("hydra.textual.fontSize", "18");
        settings.put("hydra.textual.lineHeight", "28");
        settings.put("hydra.textual.foreground", "#112233");
        settings.put("hydra.textual.background", "#FAFAFA");
        settings.put("hydra.textual.keywords", "entity relation");
        settings.put("hydra.textual.role.RULE", "#ff0000|1|1");
        settings.put("hydra.textual.role.KEYWORD", "#0000ff|1|0");
        settings.put("hydra.textual.symbol.RULE.Node", "#00ff00|0|1");
        settings.put("hydra.textual.symbol.TERMINAL.ID", "#123456|1|0");
        settings.put("hydra.textual.hover.Node.title", "Nodo");
        settings.put("hydra.textual.hover.Node.description", "Un nodo del modelo");
        return settings;
    }

    private static Map<String, String> dangerousSettings() {
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("custom.other.key", "keep-me");
        settings.put("hydra.textual.fontFamily", "javascript:alert(1)");
        settings.put("hydra.textual.fontSize", "99");
        settings.put("hydra.textual.lineHeight", "abc");
        settings.put("hydra.textual.foreground", "javascript:alert(1)");
        settings.put("hydra.textual.background", "url(x)");
        settings.put("hydra.textual.keywords", "good 9bad <script> x;y");
        settings.put("hydra.textual.role.KEYWORD", "javascript:alert(1)|1|0");
        settings.put("hydra.textual.role.BOGUS", "#ffffff|0|0");
        settings.put("hydra.textual.symbol.RULE.Node", "expression(alert(1))|1|1");
        settings.put("hydra.textual.symbol.BAD.Name", "#000000|0|0");
        settings.put("hydra.textual.symbol.RULE.1bad", "#000000|0|0");
        settings.put("hydra.textual.hover.Node.title", "T\nitle");
        settings.put("hydra.textual.hover.Node.description", "x".repeat(700));
        return settings;
    }

    @Test
    @DisplayName("grammar_author_look.f/.k/.ro/.h/.sv: los valores validos se guardan, se releen del disco y se restauran tal cual")
    void validLookSettingsSurviveSaveAndRestore(@TempDir Path temporary) {
        wire(temporary);
        Map<String, String> saved = validSettings();
        grammars.create(grammar("look-valid", saved));

        GrammarDefinitionModel reread = grammars.find("look-valid").grammar();
        assertThat(reread.settings()).isEqualTo(saved);

        GrammarTextualEditorCustomization look = GrammarTextualEditorCustomization.from(reread);
        assertThat(look.fontFamily()).isEqualTo("Fira Code, monospace");
        assertThat(look.fontSize()).isEqualTo(18);
        assertThat(look.lineHeight()).isEqualTo(28);
        assertThat(look.foreground()).isEqualTo("#112233");
        assertThat(look.background()).isEqualTo("#fafafa");
        assertThat(Arrays.asList(look.keywords().split(","))).contains("entity", "relation", "node");
        assertThat(look.roleStyles()).containsEntry("RULE", "#ff0000|1|1").containsEntry("KEYWORD", "#0000ff|1|0");
        assertThat(look.symbolStyles()).containsEntry("RULE:Node", "#00ff00|0|1").containsEntry("TERMINAL:ID", "#123456|1|0");
        assertThat(look.hovers().get("Node").title()).isEqualTo("Nodo");
        assertThat(look.hovers().get("Node").description()).isEqualTo("Un nodo del modelo");

        Map<String, String> applied = look.applyTo(reread.settings());
        assertThat(applied).containsEntry("custom.other.key", "keep-me")
                .containsEntry("hydra.textual.fontSize", "18")
                .containsEntry("hydra.textual.symbol.RULE.Node", "#00ff00|0|1")
                .containsEntry("hydra.textual.hover.Node.title", "Nodo");
    }

    @Test
    @DisplayName("grammar_author_look.f/.k/.ro/.h: los valores peligrosos o invalidos se acotan al restaurar y las claves ajenas se conservan")
    void dangerousLookSettingsAreBoundedOnRestoreAndForeignKeysAreKept(@TempDir Path temporary) {
        wire(temporary);
        Map<String, String> saved = dangerousSettings();
        grammars.create(grammar("look-danger", saved));

        GrammarDefinitionModel reread = grammars.find("look-danger").grammar();
        assertThat(reread.settings()).isEqualTo(saved);

        GrammarTextualEditorCustomization look = GrammarTextualEditorCustomization.from(reread);
        assertThat(look.fontFamily()).isEqualTo(DEFAULT_FONT);
        assertThat(look.fontSize()).isEqualTo(14);
        assertThat(look.lineHeight()).isEqualTo(21);
        assertThat(look.foreground()).isEqualTo("#1f2937");
        assertThat(look.background()).isEqualTo("#ffffff");
        assertThat(Arrays.asList(look.keywords().split(","))).contains("good").doesNotContain("9bad", "<script>", "x;y");
        assertThat(look.roleStyles()).containsEntry("KEYWORD", "#1f2937|1|0").doesNotContainKey("BOGUS");
        assertThat(look.symbolStyles()).containsEntry("RULE:Node", "#7c3aed|1|1")
                .doesNotContainKey("BAD:Name").doesNotContainKey("RULE:1bad");
        assertThat(look.hovers().get("Node").title()).isEqualTo("T itle");
        assertThat(look.hovers().get("Node").description()).isEqualTo("x".repeat(600));

        Map<String, String> applied = look.applyTo(reread.settings());
        assertThat(applied).containsEntry("custom.other.key", "keep-me").containsEntry("hydra.textual.fontFamily", DEFAULT_FONT);
        assertThat(applied.values()).noneMatch(value -> value.contains("javascript") || value.contains("expression(") || value.contains("url("));
    }

    @Test
    @DisplayName("grammar_author_look.sv: cambiar el aspecto y guardar no cambia el proyecto generado por la compilacion")
    void changingTheLookDoesNotAffectTheGeneratedProject(@TempDir Path temporary) {
        wire(temporary);
        var created = grammars.create(grammar("look-compile", Map.of("custom.other.key", "keep-me")));
        GrammarCompilationSnapshot plain = service.compile("look-compile", new GrammarCompilationRequest("look-plain", "look-compile", 1,
                List.of(document()), "langium-4.3-browser", "", false, Map.of()));
        assertThat(plain.status()).isEqualTo(CompilationStatus.SUCCESS);

        grammars.replace("look-compile", grammar("look-compile", dangerousSettings()), created.etag());
        assertThat(grammars.find("look-compile").grammar().settings()).isEqualTo(dangerousSettings());
        GrammarCompilationSnapshot styled = service.compile("look-compile", new GrammarCompilationRequest("look-styled", "look-compile", 1,
                List.of(document()), "langium-4.3-browser", "", false, Map.of()));

        assertThat(styled.status()).isEqualTo(CompilationStatus.SUCCESS);
        assertThat(styled.id()).isNotEqualTo(plain.id());
        assertThat(styled.sourceHash()).isEqualTo(plain.sourceHash());
        assertThat(plain.generatedProject().files()).isNotEmpty();
        assertThat(styled.generatedProject().files()).isEqualTo(plain.generatedProject().files());
    }
}
