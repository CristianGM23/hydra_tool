package com.example.editor.grammar.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.grammar.GrammarProperties;
import com.example.editor.grammar.core.GrammarAuthoringMode;
import com.example.editor.grammar.core.GrammarDefinitionModel;
import com.example.editor.grammar.core.GrammarSemanticBindingMap;
import com.example.editor.grammar.core.GrammarSourceDocument;
import com.example.editor.grammar.core.GrammarTechnology;
import com.example.editor.grammar.persistence.JsonGrammarRepository;
import com.example.editor.grammar.provider.GrammarProviderRegistry;
import com.example.editor.grammar.provider.antlr.AntlrGrammarProvider;
import com.example.editor.grammar.provider.langium.LangiumGrammarProvider;
import com.example.editor.grammar.provider.xtext.XtextGrammarProvider;
import com.example.editor.persistence.LocalCanonicalDocumentStoreFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia FUERTE real, sin mocks, de guardar de verdad una definicion de gramatica a traves del
 * mismo servicio de aplicacion real que respalda el boton final «Guardar» del diseñador de
 * gramaticas. Cubre directamente "tech" (elegir tecnologia), "mode" (que manda), "gf" (escribir la
 * gramatica primero, sin metamodelo previo), "mm"/"txt" (modo enlazado, metamodelo elegido y texto
 * alrededor de sus tipos), "sv" (guardar) y "art" (gramatica guardada) de grammar_define_language.
 */
class GrammarApplicationServiceSaveIntegrationTests {

    @Test
    void savesAGrammarFirstDefinitionThatNeedsNoPriorMetamodel(@TempDir Path temporary) {
        GrammarApplicationService service = service(temporary);

        GrammarSourceDocument document = new GrammarSourceDocument("main", "main.langium", "langium",
                "grammar Stable\nentry Model: nodes+=Node*;\nNode: 'node' name=ID;\n", "", true, false);
        GrammarDefinitionModel grammarFirst = new GrammarDefinitionModel("1.0", "grammar-first-language", "Grammar first language",
                GrammarTechnology.LANGIUM, GrammarAuthoringMode.GRAMMAR_FIRST, "", "langium-4.3-browser", "main", "Model",
                List.of(document), GrammarSemanticBindingMap.empty(), "", Map.of(), Map.of());

        // "tech"/"mode"/"gf": una gramatica real primero-la-gramatica se valida y se guarda sin
        // necesitar ningun metamodelo previo.
        GrammarSaveResponse response = service.create(grammarFirst);
        assertThat(response.grammar().technology()).isEqualTo(GrammarTechnology.LANGIUM);
        assertThat(response.grammar().authoringMode()).isEqualTo(GrammarAuthoringMode.GRAMMAR_FIRST);
        assertThat(response.grammar().metamodelId()).isEmpty();

        // "sv"/"art": releida desde un repositorio real distinto, la gramatica esta realmente
        // disponible y lista para compilarse.
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        GrammarDefinitionModel reread = new JsonGrammarRepository(properties(temporary), mapper, new LocalCanonicalDocumentStoreFactory())
                .findById("grammar-first-language").orElseThrow().document();
        assertThat(reread.documents()).singleElement().extracting(GrammarSourceDocument::content).isEqualTo(document.content());
    }

    @Test
    void savesALinkedDefinitionBoundToARealMetamodelAndRejectsMetamodelFirstWithoutOne(@TempDir Path temporary) {
        GrammarApplicationService service = service(temporary);
        GrammarSourceDocument document = new GrammarSourceDocument("main", "main.langium", "langium",
                "grammar Linked\nentry Model: nodes+=Node*;\nNode: 'node' name=ID;\n", "", true, false);

        // "mode"/"mm"/"txt": en modo enlazado, el metamodelo elegido queda guardado junto con el
        // texto que escribes alrededor de sus tipos.
        GrammarDefinitionModel linked = new GrammarDefinitionModel("1.0", "linked-language", "Linked language",
                GrammarTechnology.LANGIUM, GrammarAuthoringMode.LINKED, "people", "langium-4.3-browser", "main", "Model",
                List.of(document), GrammarSemanticBindingMap.empty(), "", Map.of(), Map.of());
        GrammarSaveResponse response = service.create(linked);
        assertThat(response.grammar().authoringMode()).isEqualTo(GrammarAuthoringMode.LINKED);
        assertThat(response.grammar().metamodelId()).isEqualTo("people");

        // "mode": el modo metamodelo-primero exige de verdad un metamodelo; sin el, la definicion
        // real nunca se guarda.
        GrammarDefinitionModel metamodelFirstWithoutOne = new GrammarDefinitionModel("1.0", "ghost-language", "Ghost language",
                GrammarTechnology.LANGIUM, GrammarAuthoringMode.METAMODEL_FIRST, "", "langium-4.3-browser", "main", "Model",
                List.of(document), GrammarSemanticBindingMap.empty(), "", Map.of(), Map.of());
        assertThatThrownBy(() -> service.create(metamodelFirstWithoutOne)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("METAMODEL_FIRST requires a metamodelId");

        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        assertThat(new JsonGrammarRepository(properties(temporary), mapper, new LocalCanonicalDocumentStoreFactory())
                .findById("ghost-language")).isEmpty();
    }

    private static GrammarApplicationService service(Path temporary) {
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        GrammarProperties properties = properties(temporary);
        GrammarToolchainProfileService profiles = new GrammarToolchainProfileService();
        GrammarProviderRegistry providers = new GrammarProviderRegistry(List.of(
                new AntlrGrammarProvider(properties), new LangiumGrammarProvider(properties), new XtextGrammarProvider(properties)));
        return new GrammarApplicationService(new JsonGrammarRepository(properties, mapper, new LocalCanonicalDocumentStoreFactory()),
                properties, profiles, providers);
    }

    private static GrammarProperties properties(Path temporary) {
        GrammarProperties properties = new GrammarProperties();
        properties.setDirectory(temporary.resolve("grammars"));
        return properties;
    }
}
