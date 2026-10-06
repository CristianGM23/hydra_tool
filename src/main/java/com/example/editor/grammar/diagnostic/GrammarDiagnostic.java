package com.example.editor.grammar.diagnostic;

import java.util.List;
import java.util.Map;
import com.example.editor.grammar.core.GrammarSeverity;
import com.example.editor.grammar.core.TextRange;

/** Diagnóstico normalizado. El mensaje nativo queda en technicalDetail y nunca sustituye la explicación de HYDRA. */
public record GrammarDiagnostic(
        String code,
        GrammarSeverity severity,
        String title,
        String message,
        String cause,
        String consequence,
        String solution,
        String documentId,
        TextRange range,
        String rule,
        String property,
        String expectedType,
        String nativeCode,
        String technicalDetail,
        List<ReferenceCandidate> candidates,
        List<String> codeActionIds,
        Map<String, String> metadata) {
    public GrammarDiagnostic {
        code = text(code);
        severity = severity == null ? GrammarSeverity.ERROR : severity;
        title = text(title);
        message = text(message);
        cause = text(cause);
        consequence = text(consequence);
        solution = text(solution);
        documentId = text(documentId);
        range = range == null ? new TextRange(null, null) : range;
        rule = text(rule);
        property = text(property);
        expectedType = text(expectedType);
        nativeCode = text(nativeCode);
        technicalDetail = text(technicalDetail);
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        codeActionIds = codeActionIds == null ? List.of() : List.copyOf(codeActionIds);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de GrammarDiagnostic. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value) { return value == null ? "" : value.trim(); }
}
