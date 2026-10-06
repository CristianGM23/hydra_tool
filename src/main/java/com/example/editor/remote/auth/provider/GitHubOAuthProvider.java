package com.example.editor.remote.auth.provider;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.example.editor.remote.auth.*;
import com.example.editor.remote.storage.RemoteStorageProperties;
import com.example.editor.remote.storage.RemoteStorageProviderType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** OAuth GitHub; prioriza Authorization Code + PKCE para el flujo interactivo. Device Flow queda disponible
 * únicamente cuando el administrador lo habilita explícitamente para entornos sin navegador. */
@Component
public class GitHubOAuthProvider implements RemoteOAuthProvider {
    private final RemoteOAuthHttpClient http;private final ObjectMapper mapper;
    /** Descripción: Inicializa una instancia de GitHubOAuthProvider con sus dependencias y datos de entrada. Entradas: http: dato de entrada necesario para la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public GitHubOAuthProvider(RemoteOAuthHttpClient http,ObjectMapper mapper){this.http=http;this.mapper=mapper;}
    /** Descripción: Ejecuta la operación «type» correspondiente a la responsabilidad de GitHubOAuthProvider. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public RemoteStorageProviderType type(){return RemoteStorageProviderType.GITHUB;}
    /** Descripción: Ejecuta la operación «scopes» correspondiente a la responsabilidad de GitHubOAuthProvider. Entradas: storage: dato de entrada necesario para la operación; auth: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public List<String> scopes(RemoteStorageProperties.Profile storage,RemoteAuthProperties.Provider auth){return auth.getScopes().isEmpty()?List.of("repo"):List.copyOf(auth.getScopes());}
    /** Descripción: Ejecuta la operación «useDeviceFlow» correspondiente a la responsabilidad de GitHubOAuthProvider. Entradas: storage: dato de entrada necesario para la operación; auth: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ @Override public boolean useDeviceFlow(RemoteStorageProperties.Profile storage,RemoteAuthProperties.Provider auth){requireClient(auth,false);return auth.isDeviceFlowPreferred();}
    /** Descripción: Ejecuta la operación «beginDeviceAuthorization» correspondiente a la responsabilidad de GitHubOAuthProvider. Entradas: storage: dato de entrada necesario para la operación; auth: dato de entrada necesario para la operación; scopes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public RemoteDeviceAuthorization beginDeviceAuthorization(RemoteStorageProperties.Profile storage,RemoteAuthProperties.Provider auth,List<String> scopes){
        requireClient(auth,false);Map<String,String> values=new LinkedHashMap<>();values.put("client_id",auth.getClientId());values.put("scope",String.join(" ",scopes));
        JsonNode json=RemoteOAuthProviderErrorMapper.requireSuccessJson(type(),mapper,http.postForm(RemoteOAuthJsonSupport.uri(auth.getDeviceAuthorizationUrl()),values,Map.of("Accept","application/json")),"GitHub device authorization");
        String deviceCode=json.path("device_code").asText("");String userCode=json.path("user_code").asText("");String verification=json.path("verification_uri_complete").asText("");if(verification.isBlank())verification=json.path("verification_uri").asText("");long expires=Math.max(1L,json.path("expires_in").asLong(900L));int interval=Math.max(5,json.path("interval").asInt(5));
        if(deviceCode.isBlank()||userCode.isBlank()||verification.isBlank())throw new RemoteAuthException(RemoteAuthException.Reason.TOKEN_EXCHANGE_FAILED,"GitHub device authorization returned an incomplete response");
        URI verificationUri=RemoteNetworkPolicy.requireHttps(RemoteOAuthJsonSupport.uri(verification));
        return new RemoteDeviceAuthorization(deviceCode,userCode,verificationUri,Instant.now().plusSeconds(expires),interval);
    }
    /** Descripción: Ejecuta la operación «pollDeviceAuthorization» correspondiente a la responsabilidad de GitHubOAuthProvider. Entradas: storage: dato de entrada necesario para la operación; auth: dato de entrada necesario para la operación; deviceCode: dato de entrada necesario para la operación; scopes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public RemoteDevicePollResult pollDeviceAuthorization(RemoteStorageProperties.Profile storage,RemoteAuthProperties.Provider auth,String deviceCode,List<String> scopes){
        requireClient(auth,false);Map<String,String> values=new LinkedHashMap<>();values.put("client_id",auth.getClientId());values.put("device_code",deviceCode);values.put("grant_type","urn:ietf:params:oauth:grant-type:device_code");
        RemoteOAuthHttpClient.Response response=http.postForm(RemoteOAuthJsonSupport.uri(auth.getTokenUrl()),values,Map.of());
        JsonNode json;try{json=mapper.readTree(response.body());}catch(tools.jackson.core.JacksonException exception){throw new RemoteAuthException(RemoteAuthException.Reason.TOKEN_EXCHANGE_FAILED,"GitHub device authorization returned invalid JSON",exception);}String error=json.path("error").asText("");
        if(response.successful()&&error.isBlank()){RemoteCredentialMaterial token=RemoteOAuthJsonSupport.token(type(),json,scopes,"","");return RemoteDevicePollResult.authorized(account(auth,token));}
        int interval=Math.max(5,json.path("interval").asInt(5));
        return switch(error){case "authorization_pending"->RemoteDevicePollResult.pending(interval);case "slow_down"->RemoteDevicePollResult.slowDown(interval+5);case "access_denied"->RemoteDevicePollResult.denied();case "expired_token"->RemoteDevicePollResult.expired();default->throw RemoteOAuthProviderErrorMapper.map(type(),error,json.path("error_description").asText(error));};
    }
    /** Descripción: Ejecuta la operación «authorizationUri» correspondiente a la responsabilidad de GitHubOAuthProvider. Entradas: storage: dato de entrada necesario para la operación; auth: dato de entrada necesario para la operación; redirect: dato de entrada necesario para la operación; state: dato de entrada necesario para la operación; challenge: dato de entrada necesario para la operación; scopes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public URI authorizationUri(RemoteStorageProperties.Profile storage,RemoteAuthProperties.Provider auth,String redirect,String state,String challenge,List<String> scopes){
        requireClient(auth,true);
        Map<String,String> values=new LinkedHashMap<>();values.put("client_id",auth.getClientId());values.put("redirect_uri",redirect);values.put("scope",String.join(" ",scopes));values.put("state",state);values.put("code_challenge",challenge);values.put("code_challenge_method","S256");
        return RemoteOAuthJsonSupport.uri(auth.getAuthorizationUrl()+"?"+RemoteOAuthJsonSupport.query(values));
    }
    /** Descripción: Ejecuta la operación «exchange» correspondiente a la responsabilidad de GitHubOAuthProvider. Entradas: storage: dato de entrada necesario para la operación; auth: dato de entrada necesario para la operación; redirect: dato de entrada necesario para la operación; code: dato de entrada necesario para la operación; verifier: dato de entrada necesario para la operación; scopes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public RemoteCredentialMaterial exchange(RemoteStorageProperties.Profile storage,RemoteAuthProperties.Provider auth,String redirect,String code,String verifier,List<String> scopes){
        requireClient(auth,true);Map<String,String> values=common(auth);values.put("code",code);values.put("redirect_uri",redirect);values.put("code_verifier",verifier);
        JsonNode json=RemoteOAuthProviderErrorMapper.requireSuccessJson(type(),mapper,http.postForm(RemoteOAuthJsonSupport.uri(auth.getTokenUrl()),values,Map.of("Accept","application/json")),"GitHub token exchange");
        RemoteCredentialMaterial token=RemoteOAuthJsonSupport.token(type(),json,scopes,"","");return account(auth,token);
    }
    /** Descripción: Ejecuta la operación «refresh» correspondiente a la responsabilidad de GitHubOAuthProvider. Entradas: storage: dato de entrada necesario para la operación; auth: dato de entrada necesario para la operación; current: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public RemoteCredentialMaterial refresh(RemoteStorageProperties.Profile storage,RemoteAuthProperties.Provider auth,RemoteCredentialMaterial current){
        if(!current.refreshable())return current;if(auth.getClientSecret().isBlank())throw new RemoteAuthException(RemoteAuthException.Reason.REFRESH_REQUIRED,"GitHub connection must be authorized again before its expiring token can be refreshed");requireClient(auth,true);Map<String,String> values=common(auth);values.put("grant_type","refresh_token");values.put("refresh_token",current.refreshToken());
        JsonNode json=RemoteOAuthProviderErrorMapper.requireSuccessJson(type(),mapper,http.postForm(RemoteOAuthJsonSupport.uri(auth.getTokenUrl()),values,Map.of("Accept","application/json")),"GitHub token refresh");
        RemoteCredentialMaterial refreshed=RemoteOAuthJsonSupport.token(type(),json,current.scopes(),current.accountId(),current.accountLabel());
        if(refreshed.refreshToken().isBlank())refreshed=new RemoteCredentialMaterial(type(),refreshed.accessToken(),current.refreshToken(),refreshed.tokenType(),refreshed.expiresAt(),refreshed.scopes(),current.accountId(),current.accountLabel(),refreshed.issuedAt());
        return refreshed;
    }
    /** Descripción: Ejecuta la operación «revoke» correspondiente a la responsabilidad de GitHubOAuthProvider. Entradas: storage: dato de entrada necesario para la operación; auth: dato de entrada necesario para la operación; current: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ @Override public void revoke(RemoteStorageProperties.Profile storage,RemoteAuthProperties.Provider auth,RemoteCredentialMaterial current){
        if(current==null||current.accessToken().isBlank()||auth.getRevokeUrl().isBlank()||auth.getClientId().isBlank()||auth.getClientSecret().isBlank())return;
        String url=RemoteOAuthJsonSupport.template(auth.getRevokeUrl(),auth.getTenant(),auth.getClientId());String basic=Base64.getEncoder().encodeToString((auth.getClientId()+":"+auth.getClientSecret()).getBytes(StandardCharsets.UTF_8));
        byte[] body=("{\"access_token\":\""+jsonEscape(current.accessToken())+"\"}").getBytes(StandardCharsets.UTF_8);
        RemoteOAuthHttpClient.Response response=http.deleteJson(RemoteOAuthJsonSupport.uri(url),body,Map.of("Authorization","Basic "+basic));
        if(response.statusCode()!=204&&response.statusCode()!=404)throw new RemoteAuthException(RemoteAuthException.Reason.REMOTE_ERROR,"GitHub token revocation failed with HTTP "+response.statusCode()+": "+response.safeText());
    }
    /** Descripción: Ejecuta la operación «account» correspondiente a la responsabilidad de GitHubOAuthProvider. Entradas: auth: dato de entrada necesario para la operación; token: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteCredentialMaterial account(RemoteAuthProperties.Provider auth,RemoteCredentialMaterial token){
        JsonNode json=RemoteOAuthJsonSupport.parse(mapper,http.get(RemoteOAuthJsonSupport.uri(auth.getAccountUrl()),Map.of("Authorization","Bearer "+token.accessToken(),"Accept","application/vnd.github+json")),"GitHub account lookup");
        return RemoteOAuthJsonSupport.withAccount(token,json.path("id").asText(""),json.path("login").asText("GitHub account"));
    }
    /** Descripción: Ejecuta la operación «common» correspondiente a la responsabilidad de GitHubOAuthProvider. Entradas: auth: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String,String> common(RemoteAuthProperties.Provider auth){Map<String,String> values=new LinkedHashMap<>();values.put("client_id",auth.getClientId());if(!auth.getClientSecret().isBlank())values.put("client_secret",auth.getClientSecret());return values;}
    /** Descripción: Valida las condiciones necesarias para «requireClient» y rechaza entradas no válidas. Entradas: auth: dato de entrada necesario para la operación; secret: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireClient(RemoteAuthProperties.Provider auth,boolean secret){if(auth==null||!auth.isEnabled()||auth.getClientId().isBlank()||(secret&&auth.getClientSecret().isBlank()))throw new RemoteAuthException(RemoteAuthException.Reason.PROVIDER_NOT_CONFIGURED,secret?"GitHub Authorization Code OAuth requires a configured client ID and client secret":"GitHub authorization requires a configured client ID");}
    /** Descripción: Ejecuta la operación «jsonEscape» correspondiente a la responsabilidad de GitHubOAuthProvider. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String jsonEscape(String value){return value.replace("\\","\\\\").replace("\"","\\\"");}
}
