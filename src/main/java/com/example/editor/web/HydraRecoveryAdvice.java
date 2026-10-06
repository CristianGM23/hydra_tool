package com.example.editor.web;

import java.util.List;
import java.util.Locale;

/** Catálogo transversal y conservador de impacto, reintento y recuperación para errores HTTP. */
final class HydraRecoveryAdvice {
    record Details(String impact,boolean retryable,List<HydraRecoveryAction> actions) {
        Details { impact=impact==null?"":impact.trim(); actions=actions==null?List.of():List.copyOf(actions); }
    }

    /** Descripción: Impide instanciar un catálogo puramente funcional. Entradas: no requiere parámetros. Salida: no aplica. */
    private HydraRecoveryAdvice() {}

    /** Descripción: Obtiene una recomendación de recuperación conservadora a partir del código estable del error. Entradas: code: código HTTP lógico de HYDRA. Salida: impacto, posibilidad de reintento y acciones declarativas; nunca marca como segura una mutación cuya idempotencia no pueda garantizarse. */
    static Details from(String code) {
        String normalized=code==null?"":code.trim().toUpperCase(Locale.ROOT);
        return switch(normalized) {
            case "CONFLICT","STATE_MISMATCH","PRECONDITION_FAILED" -> new Details(
                    "The requested write was not applied because the client state is older or incompatible with the current resource.", true,
                    List.of(refresh(), diagnostics()));
            case "NOT_FOUND","PROFILE_NOT_FOUND","CONNECTION_NOT_FOUND" -> new Details(
                    "No existing resource was modified. The requested reference no longer resolves in the current workspace.", false,
                    List.of(refresh(), diagnostics()));
            case "UNAUTHORIZED","REFRESH_REQUIRED","CREDENTIALS_MISSING","VAULT_UNAVAILABLE" -> new Details(
                    "The protected operation was stopped before remote data could be changed.", false,
                    List.of(new HydraRecoveryAction("reauthenticate","Reconnect the affected account and retry after authorization succeeds.","REAUTHENTICATE",false),diagnostics()));
            case "REMOTE_ERROR","TOKEN_EXCHANGE_FAILED","FLOW_EXPIRED","SERVICE_UNAVAILABLE" -> new Details(
                    "The external or dependent service did not complete the operation. HYDRA keeps the local project state unchanged unless the operation report explicitly states otherwise.", true,
                    List.of(new HydraRecoveryAction("retry","Retry after the external service is healthy; do not automate the retry for write operations.","RETRY",false),diagnostics()));
            case "FILE_TOO_LARGE","CAPACITY_EXCEEDED" -> new Details(
                    "The operation was rejected by a configured safety budget before unbounded work could continue.", true,
                    List.of(new HydraRecoveryAction("reduce-input","Reduce the input size or wait for capacity before trying again.","GUIDANCE",true),diagnostics()));
            case "STORAGE_ERROR" -> new Details(
                    "HYDRA could not complete the requested storage operation. A recovery journal or atomic-write backup may be available for mutating operations.", true,
                    List.of(new HydraRecoveryAction("inspect-recovery","Inspect pending recovery operations before retrying the write.","OPEN_RECOVERY",true),diagnostics()));
            case "INTERNAL_ERROR" -> new Details(
                    "HYDRA stopped at the API boundary. The final persistence state must be checked from the operation journal or audit trail before repeating a write.", false,
                    List.of(diagnostics()));
            default -> new Details(
                    "The operation was rejected before HYDRA could safely accept the submitted state.", false, List.of());
        };
    }

    /** Descripción: Construye la acción segura de refresco del estado actual. Entradas: no requiere parámetros. Salida: acción declarativa REFRESH. */
    private static HydraRecoveryAction refresh(){return new HydraRecoveryAction("refresh","Reload the current state before editing or retrying.","REFRESH",true);}
    /** Descripción: Construye la acción segura de consulta de diagnósticos correlacionados. Entradas: no requiere parámetros. Salida: acción declarativa OPEN_DIAGNOSTICS. */
    private static HydraRecoveryAction diagnostics(){return new HydraRecoveryAction("open-diagnostics","Open diagnostics using the correlation ID before changing configuration or data.","OPEN_DIAGNOSTICS",true);}
}
