package com.example.editor.remote.auth.provider;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.example.editor.remote.auth.*;
import com.example.editor.remote.storage.RemoteStorageProviderType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class RemoteOAuthJsonSupport {
    /** Descripción: Inicializa una instancia de RemoteOAuthJsonSupport con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private RemoteOAuthJsonSupport(){}
    /** Descripción: Ejecuta la operación «parse» correspondiente a la responsabilidad de RemoteOAuthJsonSupport. Entradas: mapper: componente de conversión utilizado durante el procesamiento; response: dato de entrada necesario para la operación; operation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static JsonNode parse(ObjectMapper mapper,RemoteOAuthHttpClient.Response response,String operation){
        if(!response.successful())throw new RemoteAuthException(RemoteAuthException.Reason.TOKEN_EXCHANGE_FAILED,operation+" failed with HTTP "+response.statusCode()+": "+response.safeText());
        try{return mapper.readTree(response.body());}
        catch(JacksonException exception){throw new RemoteAuthException(RemoteAuthException.Reason.TOKEN_EXCHANGE_FAILED,operation+" returned invalid JSON",exception);}
    }
    /** Descripción: Ejecuta la operación «token» correspondiente a la responsabilidad de RemoteOAuthJsonSupport. Entradas: provider: dato de entrada necesario para la operación; json: dato de entrada necesario para la operación; requested: solicitud con los datos necesarios para ejecutar la operación; accountId: identificador que delimita el recurso objetivo; accountLabel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static RemoteCredentialMaterial token(RemoteStorageProviderType provider,JsonNode json,List<String> requested,String accountId,String accountLabel){
        String access=json.path("access_token").asText("");
        if(access.isBlank())throw new RemoteAuthException(RemoteAuthException.Reason.TOKEN_EXCHANGE_FAILED,"OAuth provider did not return an access token");
        String refresh=json.path("refresh_token").asText("");
        long expires=json.path("expires_in").asLong(0L);
        Instant expiresAt=expires>0?Instant.now().plusSeconds(Math.max(1L,expires)):null;
        String scope=json.path("scope").asText("");
        List<String> scopes=scope.isBlank()?requested:List.of(scope.trim().split("[ ,]+"));
        return new RemoteCredentialMaterial(provider,access,refresh,json.path("token_type").asText("Bearer"),expiresAt,scopes,accountId,accountLabel,Instant.now());
    }
    /** Descripción: Ejecuta la operación «withAccount» correspondiente a la responsabilidad de RemoteOAuthJsonSupport. Entradas: token: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; label: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static RemoteCredentialMaterial withAccount(RemoteCredentialMaterial token,String id,String label){return new RemoteCredentialMaterial(token.provider(),token.accessToken(),token.refreshToken(),token.tokenType(),token.expiresAt(),token.scopes(),id,label,token.issuedAt());}
    /** Descripción: Ejecuta la operación «query» correspondiente a la responsabilidad de RemoteOAuthJsonSupport. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ static String query(Map<String,String> values){List<String> parts=new ArrayList<>();values.forEach((key,value)->parts.add(enc(key)+"="+enc(value)));return String.join("&",parts);}
    /** Descripción: Ejecuta la operación «uri» correspondiente a la responsabilidad de RemoteOAuthJsonSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ static URI uri(String value){try{return URI.create(value);}catch(IllegalArgumentException exception){throw new RemoteAuthException(RemoteAuthException.Reason.INVALID_REQUEST,"OAuth endpoint URL is invalid",exception);}}
    /** Descripción: Ejecuta la operación «template» correspondiente a la responsabilidad de RemoteOAuthJsonSupport. Entradas: value: valor de entrada que debe procesarse; tenant: dato de entrada necesario para la operación; clientId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ static String template(String value,String tenant,String clientId){return value.replace("{tenant}",encPath(tenant)).replace("{clientId}",encPath(clientId));}
    /** Descripción: Ejecuta la operación «enc» correspondiente a la responsabilidad de RemoteOAuthJsonSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ static String enc(String value){return URLEncoder.encode(value==null?"":value,StandardCharsets.UTF_8).replace("+","%20");}
    /** Descripción: Ejecuta la operación «encPath» correspondiente a la responsabilidad de RemoteOAuthJsonSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ static String encPath(String value){return enc(value).replace("%2F","/");}
}
