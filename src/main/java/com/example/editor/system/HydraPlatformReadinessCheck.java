package com.example.editor.system;

/** Resultado explicable de una comprobación de readiness de la plataforma. */
public record HydraPlatformReadinessCheck(String code, HydraPlatformReadinessCheckStatus status, String message) {
    public HydraPlatformReadinessCheck {
        code = code == null ? "" : code.trim();
        status = status == null ? HydraPlatformReadinessCheckStatus.FAIL : status;
        message = message == null ? "" : message.trim();
    }
}
