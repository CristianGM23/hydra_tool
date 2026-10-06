package com.example.editor.remote.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/** Genera state y PKCE S256 con entropía criptográfica. */
public final class RemotePkce {
    private static final SecureRandom RANDOM=new SecureRandom();
    /** Descripción: Inicializa una instancia de RemotePkce con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private RemotePkce(){}
    /** Descripción: Ejecuta la operación «randomState» correspondiente a la responsabilidad de RemotePkce. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static String randomState(){return randomUrlToken(32);}
    /** Descripción: Ejecuta la operación «verifier» correspondiente a la responsabilidad de RemotePkce. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static String verifier(){return randomUrlToken(64);}
    /** Descripción: Ejecuta la operación «challenge» correspondiente a la responsabilidad de RemotePkce. Entradas: verifier: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String challenge(String verifier){
        try {return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));}
        catch(NoSuchAlgorithmException exception){throw new IllegalStateException("SHA-256 is unavailable",exception);}
    }
    /** Descripción: Ejecuta la operación «randomUrlToken» correspondiente a la responsabilidad de RemotePkce. Entradas: bytes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String randomUrlToken(int bytes){byte[] value=new byte[bytes];RANDOM.nextBytes(value);return Base64.getUrlEncoder().withoutPadding().encodeToString(value);}
}
