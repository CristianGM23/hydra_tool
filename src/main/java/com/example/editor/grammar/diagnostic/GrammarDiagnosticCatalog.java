package com.example.editor.grammar.diagnostic;

import java.util.Map;
import com.example.editor.grammar.core.GrammarSeverity;
import com.example.editor.grammar.core.TextRange;

/** Catálogo neutral de explicaciones: la UI no debe exponer mensajes crípticos del provider como explicación principal. */
public final class GrammarDiagnosticCatalog {
    public static final String SYNTAX_ERROR = "GRAMMAR_SYNTAX_ERROR";
    public static final String DUPLICATE_SYMBOL = "GRAMMAR_DUPLICATE_SYMBOL";
    public static final String INVALID_IMPORT = "GRAMMAR_INVALID_IMPORT";
    public static final String TOOLCHAIN_UNAVAILABLE = "GRAMMAR_TOOLCHAIN_UNAVAILABLE";
    public static final String SEMANTIC_BINDING_INVALID = "GRAMMAR_SEMANTIC_BINDING_INVALID";
    public static final String ENTRY_RULE_NOT_FOUND = "GRAMMAR_ENTRY_RULE_NOT_FOUND";
    public static final String MULTIPLE_ENTRY_RULES = "GRAMMAR_MULTIPLE_ENTRY_RULES";
    public static final String UNUSED_RULE = "GRAMMAR_UNUSED_RULE";
    public static final String RECURSION_SAFE = "GRAMMAR_RECURSION_SAFE";
    public static final String LEFT_RECURSION = "GRAMMAR_LEFT_RECURSION";
    public static final String LEFT_RECURSION_NATIVE = "GRAMMAR_LEFT_RECURSION_NATIVE_SUPPORTED";

    /** Descripción: Inicializa una instancia de GrammarDiagnosticCatalog con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private GrammarDiagnosticCatalog() { }

    /** Descripción: Ejecuta la operación «reference» correspondiente a la responsabilidad de GrammarDiagnosticCatalog. Entradas: code: dato de entrada necesario para la operación; reference: dato de entrada necesario para la operación; documentId: identificador que delimita el recurso objetivo; range: dato de entrada necesario para la operación; rule: dato de entrada necesario para la operación; property: dato de entrada necesario para la operación; expectedType: dato de entrada necesario para la operación; candidates: dato de entrada necesario para la operación; actionIds: dato de entrada necesario para la operación; nativeCode: dato de entrada necesario para la operación; technicalDetail: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static GrammarDiagnostic reference(ReferenceDiagnosticCode code, String reference, String documentId, TextRange range,
            String rule, String property, String expectedType, java.util.List<ReferenceCandidate> candidates, java.util.List<String> actionIds,
            String nativeCode, String technicalDetail) {
        String quoted = reference == null || reference.isBlank() ? "la referencia" : "\"" + reference + "\"";
        String title;
        String cause;
        String solution;
        switch (code) {
            case REFERENCE_CASE_MISMATCH -> {
                title = "La referencia usa mayúsculas o minúsculas diferentes";
                cause = "Existe un elemento con el mismo nombre lógico, pero su escritura no coincide exactamente.";
                solution = "Usar la escritura exacta del candidato compatible.";
            }
            case REFERENCE_AMBIGUOUS -> {
                title = "La referencia es ambigua";
                cause = "Más de un elemento visible puede resolver " + quoted + ".";
                solution = "Elegir un candidato o completar el nombre cualificado para eliminar la ambigüedad.";
            }
            case REFERENCE_IMPORT_MISSING -> {
                title = "Falta el import que hace visible la referencia";
                cause = "HYDRA ha encontrado un destino compatible fuera de los documentos importados desde este punto.";
                solution = "Añadir el import correspondiente o revisar la organización de los documentos.";
            }
            case REFERENCE_NOT_VISIBLE -> {
                title = "La referencia existe, pero no es visible desde este punto";
                cause = "El destino existe en la gramática, pero queda fuera del scope aplicable.";
                solution = "Revisar imports, qualified names o reglas de scope.";
            }
            case REFERENCE_WRONG_TYPE -> {
                title = "La referencia apunta a un tipo incompatible";
                cause = quoted + " identifica un elemento, pero no del tipo esperado" + suffix(expectedType) + ".";
                solution = "Seleccionar un destino del tipo esperado o revisar el mapping semántico de la relación.";
            }
            case REFERENCE_MAPPING_MISMATCH -> {
                title = "El mapping semántico no coincide con la referencia";
                cause = "La relación textual y GrammarSemanticBindingMap describen destinos incompatibles.";
                solution = "Revisar el binding semántico y volver a reconciliar la gramática antes de compilar.";
            }
            case REFERENCE_CYCLE -> {
                title = "Se ha detectado un ciclo de referencias";
                cause = "La cadena de imports o bindings vuelve a un elemento ya visitado.";
                solution = "Romper el ciclo o introducir un punto de indirection soportado por el provider.";
            }
            case REFERENCE_TARGET_DELETED -> {
                title = "El destino de la referencia ya no existe";
                cause = "La referencia conserva la identidad de un elemento eliminado o sustituido.";
                solution = "Seleccionar un destino actual o eliminar la referencia obsoleta.";
            }
            case REFERENCE_STALE -> {
                title = "La referencia procede de una revisión anterior";
                cause = "El borrador cambió después de calcular la resolución de esta referencia.";
                solution = "Volver a analizar el borrador y aplicar únicamente acciones calculadas para la revisión actual.";
            }
            default -> {
                title = "No se encuentra la referencia " + quoted;
                cause = quoted + " no identifica ningún elemento compatible visible desde este punto.";
                solution = candidates == null || candidates.isEmpty()
                        ? "Corregir el nombre, crear el elemento, añadir el import necesario o revisar el scope."
                        : "Revisar los candidatos encontrados y aplicar la corrección únicamente si el destino es inequívoco.";
            }
        }
        return new GrammarDiagnostic(code.name(), GrammarSeverity.ERROR, title,
                quoted + " no puede resolverse correctamente.", cause,
                "El borrador puede conservarse, pero no sustituirá al último modelo semántico válido.", solution,
                documentId, range, rule, property, expectedType, nativeCode, technicalDetail,
                candidates, actionIds, Map.of("reference", reference == null ? "" : reference));
    }

    /** Descripción: Ejecuta la operación «simple» correspondiente a la responsabilidad de GrammarDiagnosticCatalog. Entradas: code: dato de entrada necesario para la operación; severity: dato de entrada necesario para la operación; title: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; cause: dato de entrada necesario para la operación; consequence: dato de entrada necesario para la operación; solution: dato de entrada necesario para la operación; documentId: identificador que delimita el recurso objetivo; range: dato de entrada necesario para la operación; metadata: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static GrammarDiagnostic simple(String code, GrammarSeverity severity, String title, String message,
            String cause, String consequence, String solution, String documentId, TextRange range, Map<String,String> metadata) {
        return new GrammarDiagnostic(code, severity, title, message, cause, consequence, solution, documentId, range,
                "", "", "", "", "", java.util.List.of(), java.util.List.of(), metadata);
    }


    /** Diagnóstico de recursión con explicación de alto nivel y evidencia técnica separada. */
    /** Descripción: Ejecuta la operación «recursion» correspondiente a la responsabilidad de GrammarDiagnosticCatalog. Entradas: code: dato de entrada necesario para la operación; severity: dato de entrada necesario para la operación; title: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; cause: dato de entrada necesario para la operación; consequence: dato de entrada necesario para la operación; solution: dato de entrada necesario para la operación; documentId: identificador que delimita el recurso objetivo; range: dato de entrada necesario para la operación; actionIds: dato de entrada necesario para la operación; metadata: dato de entrada necesario para la operación; technicalDetail: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static GrammarDiagnostic recursion(String code, GrammarSeverity severity, String title, String message, String cause,
            String consequence, String solution, String documentId, TextRange range, java.util.List<String> actionIds,
            Map<String,String> metadata, String technicalDetail) {
        return new GrammarDiagnostic(code, severity, title, message, cause, consequence, solution, documentId, range,
                metadata == null ? "" : metadata.getOrDefault("rule", ""), "", "", "", technicalDetail,
                java.util.List.of(), actionIds, metadata);
    }

    /** Descripción: Ejecuta la operación «suffix» correspondiente a la responsabilidad de GrammarDiagnosticCatalog. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String suffix(String value) { return value == null || value.isBlank() ? "" : " (" + value + ")"; }
}
