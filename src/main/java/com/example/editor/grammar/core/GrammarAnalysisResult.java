package com.example.editor.grammar.core;

import com.example.editor.grammar.diagnostic.GrammarCodeAction;
import com.example.editor.grammar.diagnostic.GrammarDiagnostic;
import java.util.List;
import java.util.Map;

/** Normalized provider-independent analysis response. */
public record GrammarAnalysisResult(String requestId, long draftRevision, String sourceHash, GrammarAnalysisMode mode,
        String providerInstanceId, boolean stale, List<GrammarDiagnostic> diagnostics, List<GrammarSymbol> symbols,
        GrammarSemanticBindingMap semanticCandidate, ProviderHealth providerHealth, List<GrammarCodeAction> codeActions,
        Map<String,String> metadata) {
    public GrammarAnalysisResult {
        requestId = text(requestId);
        draftRevision = Math.max(0, draftRevision);
        sourceHash = text(sourceHash);
        mode = mode == null ? GrammarAnalysisMode.FAST : mode;
        providerInstanceId = text(providerInstanceId);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        symbols = symbols == null ? List.of() : List.copyOf(symbols);
        semanticCandidate = semanticCandidate == null ? GrammarSemanticBindingMap.empty() : semanticCandidate;
        providerHealth = providerHealth == null ? ProviderHealth.healthy(providerInstanceId) : providerHealth;
        codeActions = codeActions == null ? List.of() : List.copyOf(codeActions);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** Compatibility constructor retained for the neutral analyzer. */
    /** Descripción: Inicializa una instancia de GrammarAnalysisResult con sus dependencias y datos de entrada. Entradas: draftRevision: dato de entrada necesario para la operación; sourceHash: dato de entrada necesario para la operación; mode: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación; symbols: dato de entrada necesario para la operación; semanticCandidate: dato de entrada necesario para la operación; providerHealth: dato de entrada necesario para la operación; codeActions: dato de entrada necesario para la operación; metadata: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public GrammarAnalysisResult(long draftRevision, String sourceHash, GrammarAnalysisMode mode,
            List<GrammarDiagnostic> diagnostics, List<GrammarSymbol> symbols, GrammarSemanticBindingMap semanticCandidate,
            ProviderHealth providerHealth, List<GrammarCodeAction> codeActions, Map<String,String> metadata) {
        this("", draftRevision, sourceHash, mode, providerHealth == null ? "" : providerHealth.providerInstanceId(), false,
                diagnostics, symbols, semanticCandidate, providerHealth, codeActions, metadata);
    }

    /** Descripción: Evalúa la condición «hasErrors» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean hasErrors(){return diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity()==GrammarSeverity.ERROR);}
    /** Descripción: Ejecuta la operación «withEnvelope» correspondiente a la responsabilidad de GrammarAnalysisResult. Entradas: requestId: identificador que delimita el recurso objetivo; providerInstanceId: identificador que delimita el recurso objetivo; stale: dato de entrada necesario para la operación; health: dato de entrada necesario para la operación; additionalMetadata: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public GrammarAnalysisResult withEnvelope(String requestId, String providerInstanceId, boolean stale, ProviderHealth health,
            Map<String,String> additionalMetadata) {
        java.util.LinkedHashMap<String,String> merged = new java.util.LinkedHashMap<>(metadata);
        if (additionalMetadata != null) merged.putAll(additionalMetadata);
        return new GrammarAnalysisResult(requestId, draftRevision, sourceHash, mode, providerInstanceId, stale, diagnostics,
                symbols, semanticCandidate, health, codeActions, Map.copyOf(merged));
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de GrammarAnalysisResult. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value==null?"":value.trim();}
}
