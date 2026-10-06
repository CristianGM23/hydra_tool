package com.example.editor.integration.projection.canonical;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

/** Codificación y escaping común de metadatos de proyección. */
final class CanonicalProjectionSupport {
    /** Descripción: Inicializa una instancia de CanonicalProjectionSupport con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private CanonicalProjectionSupport(){ }
    /** Descripción: Ejecuta la operación «sha256» correspondiente a la responsabilidad de CanonicalProjectionSupport. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ static String sha256(byte[] content){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));}catch(NoSuchAlgorithmException exception){throw new IllegalStateException(exception);}}
    /** Descripción: Ejecuta la operación «base64» correspondiente a la responsabilidad de CanonicalProjectionSupport. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ static String base64(byte[] content){return Base64.getEncoder().encodeToString(content);}
    /** Descripción: Ejecuta la operación «decode» correspondiente a la responsabilidad de CanonicalProjectionSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ static byte[] decode(String value){return Base64.getDecoder().decode(value);}
    /** Descripción: Ejecuta la operación «slug» correspondiente a la responsabilidad de CanonicalProjectionSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ static String slug(String value){String result = value == null ? "artifact" : value.toLowerCase().replaceAll("[^a-z0-9._-]+", "-").replaceAll("^-+|-+$", ""); return result.isBlank() ? "artifact" : result;}
    /** Descripción: Ejecuta la operación «xml» correspondiente a la responsabilidad de CanonicalProjectionSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ static String xml(String value){return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");}
    /** Descripción: Ejecuta la operación «plant» correspondiente a la responsabilidad de CanonicalProjectionSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ static String plant(String value){return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", " ").replace("\n", " ");}
}
