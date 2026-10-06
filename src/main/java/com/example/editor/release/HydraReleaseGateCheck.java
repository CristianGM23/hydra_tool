package com.example.editor.release;

/** Explainable check with stable code and evidence fingerprint. */
public record HydraReleaseGateCheck(String code, HydraReleaseGateStatus status,
        String message, String evidence) {
    public HydraReleaseGateCheck {
        code = text(code); status = status == null ? HydraReleaseGateStatus.FAIL : status;
        message = text(message); evidence = text(evidence);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de HydraReleaseGateCheck. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value == null ? "" : value.trim();}
}
