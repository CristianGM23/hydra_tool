package com.example.editor.grammar.provider;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import com.example.editor.grammar.GrammarProperties;
import com.example.editor.grammar.application.GrammarSourceAnalyzer;
import com.example.editor.grammar.core.*;

/** Common deterministic baseline. Native providers may enrich it without changing the normalized contract. */
public abstract class AbstractNeutralGrammarProvider implements GrammarProvider {
    private final GrammarSourceAnalyzer analyzer;
    /** Descripción: Inicializa una instancia de AbstractNeutralGrammarProvider con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ protected AbstractNeutralGrammarProvider(GrammarProperties properties) { this.analyzer = new GrammarSourceAnalyzer(properties); }

    /** Descripción: Ejecuta la operación «analyze» correspondiente a la responsabilidad de AbstractNeutralGrammarProvider. Entradas: grammar: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public GrammarAnalysisResult analyze(GrammarDefinitionModel grammar, GrammarAnalysisRequest request) {
        return analyzer.analyze(grammar, request);
    }

    /** Descripción: Ejecuta la operación «project» correspondiente a la responsabilidad de AbstractNeutralGrammarProvider. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; scaffolding: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ protected GeneratedGrammarProject project(GrammarProjectGenerationRequest request, Map<String,String> scaffolding) {
        if (request == null || request.grammar() == null || request.profile() == null) throw new IllegalArgumentException("Grammar project generation requires grammar and toolchain profile");
        Map<String,String> files = new LinkedHashMap<>(scaffolding == null ? Map.of() : scaffolding);
        for (GrammarSourceDocument document : request.documents()) {
            String previous = files.put(document.relativePath(), document.content());
            if (previous != null && !previous.equals(document.content())) throw new IllegalArgumentException("Generated scaffold collides with grammar source: " + document.relativePath());
        }
        return new GeneratedGrammarProject(request.grammar().id() + "-" + descriptor().technology().name().toLowerCase(Locale.ROOT), files,
                Map.of("technology", descriptor().technology().name(), "provider", descriptor().id(),
                        "providerVersion", descriptor().version(), "toolchainProfile", request.profile().id(),
                        "toolchainHash", GrammarHashing.toolchain(request.profile()), "sourceHash", GrammarHashing.sources(request.documents()),
                        "sourceCount", Integer.toString(request.documents().size()), "generatedBy", "HYDRA"));
    }
}
