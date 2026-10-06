package com.example.editor.integration.bridge;
import java.util.List;
import com.example.editor.integration.HostElementReference;
/** Diagnóstico neutral que puede proyectarse en Eclipse, VS Code, BESSER u otra UI. */
public record HostDiagnosticMessage(String id, String code, String severity, String message,
        HostElementReference target, List<String> quickFixIds) {
    public HostDiagnosticMessage {
        id = normalize(id); code = normalize(code); severity = normalize(severity); message = normalize(message);
        quickFixIds = quickFixIds == null ? List.of() : List.copyOf(quickFixIds);
    }
    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de HostDiagnosticMessage. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalize(String value) { return value == null ? "" : value.trim(); }
}
