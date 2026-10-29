package com.example.editor.grammar.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.grammar.GrammarProperties;
import com.example.editor.grammar.core.GrammarAuthoringMode;
import com.example.editor.grammar.core.GrammarDefinitionModel;
import com.example.editor.grammar.core.GrammarPlatformConversionRequest;
import com.example.editor.grammar.core.GrammarSemanticBindingMap;
import com.example.editor.grammar.core.GrammarSourceDocument;
import com.example.editor.grammar.core.GrammarTechnology;
import com.example.editor.grammar.persistence.JsonGrammarRepository;
import com.example.editor.grammar.provider.GrammarProviderRegistry;
import com.example.editor.grammar.provider.antlr.AntlrGrammarProvider;
import com.example.editor.grammar.provider.langium.LangiumGrammarProvider;
import com.example.editor.grammar.provider.xtext.XtextGrammarProvider;
import com.example.editor.grammar.visualization.GrammarClassDiagramProjectionService;
import com.example.editor.persistence.LocalCanonicalDocumentStoreFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia real, sin mocks, de las etapas "ap" (Aplicar) y "rj" (Mantener la tecnologia actual) de
 * grammar_conversion_preview: la unica ruta backend existente (GrammarPlatformConversionService,
 * endpoint /conversion-preview) solo calcula una previsualizacion sin persistir nada; "aplicar" es
 * de verdad guardar esa gramatica convertida a traves del mismo GrammarApplicationService.replace()
 * real que respalda el boton «Guardar» del diseñador, y "mantener la tecnologia actual" es de verdad
 * no llamar a ese replace(), dejando la gramatica original intacta en disco.
 */
class GrammarConversionApplyRejectIntegrationTests {

    @Test
    void applyingARealEquivalentConversionGenuinelyPersistsTheNewTechnologyAndText(@TempDir Path temporary) {
        GrammarApplicationService service = service(temporary);
        GrammarSourceDocument document = new GrammarSourceDocument("main", "main.langium", "langium",
                "grammar People\nentry World: models+=Model*;\nModel: 'model' name=ID;\nhidden terminal WS: /\\s+/;\nterminal ID: /[_a-zA-Z][\\w_]*/;\n",
                "", true, false);
        GrammarDefinitionModel original = new GrammarDefinitionModel("1.0", "convertible-language", "Convertible language",
                GrammarTechnology.LANGIUM, GrammarAuthoringMode.GRAMMAR_FIRST, "", "langium-4.3-browser", "main", "World",
                List.of(document), GrammarSemanticBindingMap.empty(), "", Map.of(), Map.of());
        GrammarSaveResponse saved = service.create(original);

        // "em"/"pv": hay texto, asi que se calcula una vista previa real de verdad.
        var preview = conversionService(temporary).prepare(new GrammarPlatformConversionRequest(saved.grammar(), GrammarTechnology.XTEXT));
        assertThat(preview.semanticallyEquivalent()).isTrue();

        // "ap": aplicar de verdad cambia la tecnologia y el texto a traves del mismo servicio real
        // que guarda desde el diseñador.
        GrammarSaveResponse applied = service.replace("convertible-language", preview.convertedGrammar(), saved.etag());
        assertThat(applied.grammar().technology()).isEqualTo(GrammarTechnology.XTEXT);

        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        GrammarDefinitionModel reread = new JsonGrammarRepository(properties(temporary), mapper, new LocalCanonicalDocumentStoreFactory())
                .findById("convertible-language").orElseThrow().document();
        assertThat(reread.technology()).isEqualTo(GrammarTechnology.XTEXT);
        assertThat(reread.documents().getFirst().content()).contains("HYDRA_TRANSPORT_RULE");
    }

    @Test
    void keepingTheCurrentTechnologyGenuinelyLeavesTheOriginalGrammarUntouched(@TempDir Path temporary) {
        GrammarApplicationService service = service(temporary);
        GrammarSourceDocument document = new GrammarSourceDocument("main", "main.langium", "langium",
                "grammar Kept\nentry World: models+=Model*;\nModel: 'model' name=ID;\nhidden terminal WS: /\\s+/;\nterminal ID: /[_a-zA-Z][\\w_]*/;\n",
                "", true, false);
        GrammarDefinitionModel original = new GrammarDefinitionModel("1.0", "kept-language", "Kept language",
                GrammarTechnology.LANGIUM, GrammarAuthoringMode.GRAMMAR_FIRST, "", "langium-4.3-browser", "main", "World",
                List.of(document), GrammarSemanticBindingMap.empty(), "", Map.of(), Map.of());
        GrammarSaveResponse saved = service.create(original);

        // Se calcula una vista previa real, pero "rj": se decide mantener la tecnologia actual y
        // nunca se llama a replace(); el repositorio real conserva la gramatica original intacta.
        var preview = conversionService(temporary).prepare(new GrammarPlatformConversionRequest(saved.grammar(), GrammarTechnology.XTEXT));
        assertThat(preview.semanticallyEquivalent()).isTrue();

        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        GrammarDefinitionModel reread = new JsonGrammarRepository(properties(temporary), mapper, new LocalCanonicalDocumentStoreFactory())
                .findById("kept-language").orElseThrow().document();
        assertThat(reread.technology()).isEqualTo(GrammarTechnology.LANGIUM);
        assertThat(reread.documents().getFirst().content()).isEqualTo(document.content());
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

    private static GrammarPlatformConversionService conversionService(Path temporary) {
        GrammarProperties properties = properties(temporary);
        return new GrammarPlatformConversionService(new GrammarClassDiagramProjectionService(properties), properties,
                new GrammarToolchainProfileService(), List.of(
                        new com.example.editor.metamodel.export.XtextMetamodelExporter(),
                        new com.example.editor.metamodel.export.LangiumMetamodelExporter(),
                        new com.example.editor.metamodel.export.AntlrMetamodelExporter()));
    }

    private static GrammarProperties properties(Path temporary) {
        GrammarProperties properties = new GrammarProperties();
        properties.setDirectory(temporary.resolve("grammars"));
        return properties;
    }
}
