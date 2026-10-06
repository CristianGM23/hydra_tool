package com.example.editor.grammar.core;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Versioned and cancelable analysis envelope. sourceHash is verified by the coordinator before execution. */
public record GrammarAnalysisRequest(String requestId, String grammarId, String documentId, long draftRevision,
        GrammarAnalysisMode mode, List<String> changedDocuments, List<GrammarSourceDocument> documents,
        String toolchainProfileId, String sourceHash, Map<String,String> settings) {
    public GrammarAnalysisRequest {
        requestId = text(requestId);
        if (requestId.isBlank()) requestId = "grammar-analysis:" + UUID.randomUUID();
        grammarId = text(grammarId);
        documentId = text(documentId);
        draftRevision = Math.max(0, draftRevision);
        mode = mode == null ? GrammarAnalysisMode.FAST : mode;
        changedDocuments = changedDocuments == null ? List.of() : List.copyOf(changedDocuments);
        documents = documents == null ? List.of() : List.copyOf(documents);
        toolchainProfileId = text(toolchainProfileId);
        String calculated = GrammarHashing.sources(documents);
        sourceHash = text(sourceHash);
        if (!sourceHash.isBlank() && !documents.isEmpty() && !sourceHash.equalsIgnoreCase(calculated)) {
            throw new IllegalArgumentException("Analysis sourceHash does not match supplied documents");
        }
        if (sourceHash.isBlank() && !documents.isEmpty()) sourceHash = calculated;
        settings = settings == null ? Map.of() : Map.copyOf(settings);
    }

    /** Compatibility constructor retained for V4.32.97 draft clients. */
    /** Descripción: Inicializa una instancia de GrammarAnalysisRequest con sus dependencias y datos de entrada. Entradas: grammarId: identificador que delimita el recurso objetivo; draftRevision: dato de entrada necesario para la operación; mode: dato de entrada necesario para la operación; changedDocuments: dato de entrada necesario para la operación; documents: dato de entrada necesario para la operación; toolchainProfileId: identificador que delimita el recurso objetivo. Salida: deja la instancia inicializada y lista para su uso. */ public GrammarAnalysisRequest(String grammarId, long draftRevision, GrammarAnalysisMode mode,
            List<String> changedDocuments, List<GrammarSourceDocument> documents, String toolchainProfileId) {
        this("", grammarId, changedDocuments == null || changedDocuments.isEmpty() ? "" : changedDocuments.get(0),
                draftRevision, mode, changedDocuments, documents, toolchainProfileId, "", Map.of());
    }

    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de GrammarAnalysisRequest. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value==null?"":value.trim();}
}
