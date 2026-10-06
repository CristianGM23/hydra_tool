package com.example.editor.remote.auth;

import java.time.Instant;
import java.util.List;
import com.example.editor.remote.storage.RemoteStorageProviderType;

/** Credencial delegada cifrada en reposo. Nunca se serializa por la API web. */
public record RemoteCredentialMaterial(RemoteStorageProviderType provider,String accessToken,String refreshToken,String tokenType,
        Instant expiresAt,List<String> scopes,String accountId,String accountLabel,Instant issuedAt) {
    public RemoteCredentialMaterial {
        accessToken=accessToken==null?"":accessToken;
        refreshToken=refreshToken==null?"":refreshToken;
        tokenType=tokenType==null||tokenType.isBlank()?"Bearer":tokenType.trim();
        scopes=scopes==null?List.of():List.copyOf(scopes);
        accountId=accountId==null?"":accountId.trim();
        accountLabel=accountLabel==null?"":accountLabel.trim();
        issuedAt=issuedAt==null?Instant.now():issuedAt;
    }
    /** Descripción: Ejecuta la operación «refreshable» correspondiente a la responsabilidad de RemoteCredentialMaterial. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean refreshable(){return !refreshToken.isBlank();}
}
