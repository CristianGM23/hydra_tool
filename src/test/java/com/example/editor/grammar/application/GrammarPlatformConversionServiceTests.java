package com.example.editor.grammar.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import com.example.editor.grammar.GrammarProperties;
import com.example.editor.grammar.core.GrammarAuthoringMode;
import com.example.editor.grammar.core.GrammarDefinitionModel;
import com.example.editor.grammar.core.GrammarPlatformConversionRequest;
import com.example.editor.grammar.core.GrammarSemanticBindingMap;
import com.example.editor.grammar.core.GrammarSourceDocument;
import com.example.editor.grammar.core.GrammarTechnology;
import com.example.editor.grammar.visualization.GrammarClassDiagramProjectionService;
import com.example.editor.metamodel.export.AntlrMetamodelExporter;
import com.example.editor.metamodel.export.LangiumMetamodelExporter;
import com.example.editor.metamodel.export.XtextMetamodelExporter;

class GrammarPlatformConversionServiceTests {

    @Test
    void convertsAcrossPlatformsAndRestoresThePreviousSourceByteForByte() {
        GrammarPlatformConversionService service = service(new GrammarProperties());
        String source = "grammar People\r\n// comentario que debe conservarse exactamente\r\nentry World: models+=Model*;\r\nModel: 'model' name=ID ('elements' elements+=Element*)?;\r\nElement: 'element' name=ID;\r\nhidden terminal WS: /\\s+/;\r\nterminal ID: /[_a-zA-Z][\\w_]*/;\r\n";
        GrammarDefinitionModel original = grammar("people", source);

        var xtext = service.prepare(new GrammarPlatformConversionRequest(original, GrammarTechnology.XTEXT));
        assertThat(xtext.semanticallyEquivalent()).isTrue();
        assertThat(xtext.convertedGrammar().documents().getFirst().content()).contains("HYDRA_TRANSPORT_RULE");

        var restored = service.prepare(new GrammarPlatformConversionRequest(xtext.convertedGrammar(), GrammarTechnology.LANGIUM));
        assertThat(restored.semanticallyEquivalent()).isTrue();
        assertThat(restored.exactSourceRestored()).isTrue();
        assertThat(restored.convertedGrammar().documents().getFirst().content()).isEqualTo(source);
    }

    @Test
    void preservesReferencesAndCommonMultiplicitiesThroughAntlrAndXtext() {
        GrammarPlatformConversionService service = service(new GrammarProperties());
        GrammarDefinitionModel source = grammar("people", """
                grammar People
                entry World: people+=Person*;
                Person: 'person' name=ID ('friend' friend=[Person])? ('children' children+=Person*)?;
                hidden terminal WS: /\\s+/;
                terminal ID: /[_a-zA-Z][\\w_]*/;
                """);

        var antlr = service.prepare(new GrammarPlatformConversionRequest(source, GrammarTechnology.ANTLR));
        assertThat(antlr.semanticallyEquivalent()).isTrue();
        assertThat(antlr.convertedGrammar().documents().getFirst().content()).contains("HYDRA_REFERENCE");

        var xtext = service.prepare(new GrammarPlatformConversionRequest(source, GrammarTechnology.XTEXT));
        assertThat(xtext.semanticallyEquivalent()).isTrue();
        assertThat(xtext.convertedGrammar().documents().getFirst().content()).contains("friend=[model::Person|ID]?");
    }

    @Test
    void includesFingerprintEvidenceForSuccessfulConversion() {
        GrammarProperties properties = new GrammarProperties();
        GrammarPlatformConversionService service = service(properties);
        GrammarDefinitionModel source = grammar("people", """
                grammar People
                entry World: people+=Person*;
                Person: 'person' name=ID;
                hidden terminal WS: /\\s+/;
                terminal ID: /[_a-zA-Z][\\w_]*/;
                """);
        var preview = service.prepare(new GrammarPlatformConversionRequest(source, GrammarTechnology.XTEXT));
        assertThat(preview.semanticallyEquivalent()).isTrue();
        assertThat(preview.validationEvidence()).anyMatch(item -> item.contains("Fingerprint"));
    }

    @Test
    void corruptHistoricalBackupFallsBackToNeutralRegenerationInsteadOfApplyingIt() {
        GrammarPlatformConversionService service = service(new GrammarProperties());
        GrammarDefinitionModel original = grammar("people", """
                grammar People
                entry World: people+=Person*;
                Person: 'person' name=ID ('friend' friend=[Person])?;
                hidden terminal WS: /\\s+/;
                terminal ID: /[_a-zA-Z][\\w_]*/;
                """);
        var xtext = service.prepare(new GrammarPlatformConversionRequest(original, GrammarTechnology.XTEXT));
        LinkedHashMap<String, String> annotations = new LinkedHashMap<>(xtext.convertedGrammar().annotations());
        annotations.put("hydra.conversion.backup.LANGIUM", "not-base64");
        GrammarDefinitionModel corrupted = withAnnotations(xtext.convertedGrammar(), annotations);

        var preview = service.prepare(new GrammarPlatformConversionRequest(corrupted, GrammarTechnology.LANGIUM));
        assertThat(preview.exactSourceRestored()).isFalse();
        assertThat(preview.semanticallyEquivalent()).isTrue();
        assertThat(preview.warnings()).anyMatch(item -> item.contains("copia exacta"));
    }


    @Test
    void preservesNeutralTextualCustomizationAcrossPlatformConversions() {
        GrammarPlatformConversionService service = service(new GrammarProperties());
        GrammarDefinitionModel source = grammar("custom", """
                grammar Custom
                entry World: people+=Person*;
                Person: 'person' name=ID;
                hidden terminal WS: /\\s+/;
                terminal ID: /[_a-zA-Z][\\w_]*/;
                """);
        Map<String,String> settings = Map.of(
                "hydra.textual.fontFamily", "Consolas, monospace",
                "hydra.textual.fontSize", "17",
                "hydra.textual.role.KEYWORD", "#123456|1|0",
                "hydra.textual.hover.Person.title", "Persona",
                "hydra.textual.hover.Person.description", "Elemento persona del lenguaje.");
        source = new GrammarDefinitionModel(source.schemaVersion(), source.id(), source.name(), source.technology(), source.authoringMode(),
                source.metamodelId(), source.toolchainProfileId(), source.entryDocumentId(), source.entryRule(), source.documents(),
                source.semanticBindings(), source.syntaxStyleDefinitionId(), settings, source.annotations());

        var xtext = service.prepare(new GrammarPlatformConversionRequest(source, GrammarTechnology.XTEXT));
        var antlr = service.prepare(new GrammarPlatformConversionRequest(source, GrammarTechnology.ANTLR));
        assertThat(xtext.convertedGrammar().settings()).containsAllEntriesOf(settings);
        assertThat(antlr.convertedGrammar().settings()).containsAllEntriesOf(settings);
    }

    @Test
    void enforcesExpandedBackupAndTraceLimits() {
        GrammarProperties limited = new GrammarProperties();
        limited.setMaximumConversionBackupExpandedBytes(1_024);
        GrammarPlatformConversionService service = service(limited);
        String source = "grammar Big\nentry World: people+=Person*;\nPerson: 'person' name=ID;\n//" + "x".repeat(5_000);
        assertThatThrownBy(() -> service.prepare(new GrammarPlatformConversionRequest(grammar("big", source), GrammarTechnology.XTEXT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expanded-size");

        GrammarProperties traceLimited = new GrammarProperties();
        traceLimited.setMaximumConversionTraceCharacters(1_024);
        var preview = service(traceLimited).prepare(new GrammarPlatformConversionRequest(grammar("trace", """
                grammar Trace
                entry World: people+=Person*;
                Person: 'person' name=ID;
                """), GrammarTechnology.XTEXT));
        assertThat(preview.traceMarkdown().length()).isLessThanOrEqualTo(1_024);
    }

    private static GrammarPlatformConversionService service(GrammarProperties properties) {
        return new GrammarPlatformConversionService(
                new GrammarClassDiagramProjectionService(properties),
                properties,
                new GrammarToolchainProfileService(),
                List.of(new XtextMetamodelExporter(), new LangiumMetamodelExporter(), new AntlrMetamodelExporter()));
    }

    private static GrammarDefinitionModel grammar(String id, String source) {
        GrammarSourceDocument document = new GrammarSourceDocument("main", "main.langium", "langium", source, "", true, false);
        return new GrammarDefinitionModel("1.0", id, id, GrammarTechnology.LANGIUM, GrammarAuthoringMode.GRAMMAR_FIRST,
                "", "langium-4.3-browser", "main", "World", List.of(document), GrammarSemanticBindingMap.empty(), "", Map.of(), Map.of());
    }

    private static GrammarDefinitionModel withAnnotations(GrammarDefinitionModel grammar, Map<String, String> annotations) {
        return new GrammarDefinitionModel(grammar.schemaVersion(), grammar.id(), grammar.name(), grammar.technology(), grammar.authoringMode(),
                grammar.metamodelId(), grammar.toolchainProfileId(), grammar.entryDocumentId(), grammar.entryRule(), grammar.documents(),
                grammar.semanticBindings(), grammar.syntaxStyleDefinitionId(), grammar.settings(), annotations);
    }
}
