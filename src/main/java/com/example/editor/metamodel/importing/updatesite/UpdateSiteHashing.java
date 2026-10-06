package com.example.editor.metamodel.importing.updatesite;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Utilidades SHA-256 deterministas para artefactos y análisis. */
public final class UpdateSiteHashing {
    /** Descripción: Inicializa una instancia de UpdateSiteHashing con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private UpdateSiteHashing() { }

    /** Descripción: Ejecuta la operación «sha256» correspondiente a la responsabilidad de UpdateSiteHashing. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch(NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
