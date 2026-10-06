package com.example.editor.policy;

import java.time.Instant;
import java.util.Map;

/** Revocation of one package, mirror, adapter or attestation fingerprint. */
public record HydraRevocationEntry(String id, String subjectType, String subjectId, String subjectSha256,
        String reason, Instant revokedAt, Instant expiresAt, boolean active, Map<String,String> annotations) {
    public HydraRevocationEntry { revokedAt=revokedAt==null?Instant.now():revokedAt; annotations=annotations==null?Map.of():Map.copyOf(annotations); }
    /** Descripción: Ejecuta la operación «effectiveAt» correspondiente a la responsabilidad de HydraRevocationEntry. Entradas: instant: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean effectiveAt(Instant instant){return active&&(expiresAt==null||expiresAt.isAfter(instant));}
}
