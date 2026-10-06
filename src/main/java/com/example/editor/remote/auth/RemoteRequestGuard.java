package com.example.editor.remote.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;

/** Protege mutaciones remotas contra peticiones cross-site dirigidas al backend local. */
@Component
public class RemoteRequestGuard {
    private final String token=RemotePkce.randomState()+RemotePkce.randomState();
    /** Descripción: Ejecuta la operación «context» correspondiente a la responsabilidad de RemoteRequestGuard. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public RemoteRequestSecurityContext context(){return new RemoteRequestSecurityContext(token);}
    /** Descripción: Valida las condiciones necesarias para «verify» y rechaza entradas no válidas. Entradas: supplied: dato de entrada necesario para la operación; fetchSite: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void verify(String supplied,String fetchSite){
        if(fetchSite!=null&&fetchSite.equalsIgnoreCase("cross-site"))throw new RemoteAuthException(RemoteAuthException.Reason.INVALID_REQUEST,"Cross-site remote operations are not accepted");
        byte[] expected=token.getBytes(StandardCharsets.UTF_8);byte[] actual=(supplied==null?"":supplied).getBytes(StandardCharsets.UTF_8);
        if(!MessageDigest.isEqual(expected,actual))throw new RemoteAuthException(RemoteAuthException.Reason.INVALID_REQUEST,"Remote operation request token is missing or invalid");
    }
}
