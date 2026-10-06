package com.example.editor.remote.auth;

import java.util.Locale;
import com.example.editor.remote.storage.RemoteStorageProviderType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Convierte errores OAuth conocidos en instrucciones de configuración estables y sin material sensible. */
public final class RemoteOAuthProviderErrorMapper {
    /** Descripción: Inicializa una instancia de RemoteOAuthProviderErrorMapper con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private RemoteOAuthProviderErrorMapper(){}

    /**
     * Parses an OAuth response without leaking tokens and maps provider errors before callers
     * attempt to interpret the success payload. Some providers return OAuth errors with HTTP 200.
     */
    /** Descripción: Valida las condiciones necesarias para «requireSuccessJson» y rechaza entradas no válidas. Entradas: provider: dato de entrada necesario para la operación; mapper: componente de conversión utilizado durante el procesamiento; response: dato de entrada necesario para la operación; operation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static JsonNode requireSuccessJson(RemoteStorageProviderType provider,ObjectMapper mapper,RemoteOAuthHttpClient.Response response,String operation){
        JsonNode json=null;
        try {json=mapper.readTree(response.body());} catch(JacksonException ignored) { /* handled below */ }
        String error=json==null?"":json.path("error").asText("");
        String detail=json==null?response.safeText():first(json.path("error_description").asText(""),json.path("error_summary").asText(""),json.path("error").asText(""),response.safeText());
        if(!response.successful()||!error.isBlank())throw map(provider,error,detail);
        if(json==null)throw new RemoteAuthException(RemoteAuthException.Reason.TOKEN_EXCHANGE_FAILED,operation+" returned invalid JSON");
        return json;
    }

    /** Descripción: Ejecuta la operación «map» correspondiente a la responsabilidad de RemoteOAuthProviderErrorMapper. Entradas: provider: dato de entrada necesario para la operación; error: dato de entrada necesario para la operación; detail: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static RemoteAuthException map(RemoteStorageProviderType provider,String error,String detail){
        String code=normalize(error);String safe=RemoteSecretRedactor.redact(detail==null||detail.isBlank()?error:detail);String lower=safe.toLowerCase(Locale.ROOT);
        if(provider==RemoteStorageProviderType.ONEDRIVE){
            if(safe.contains("AADSTS50059"))return configuration("Microsoft could not identify the organization for this application. In Installation setup, choose the Microsoft account type that matches the Entra app. If the app is limited to one organization, choose Only this organization and enter its organization directory ID or verified domain from the Entra Overview page.");
            if(safe.contains("AADSTS50194"))return configuration("The Microsoft application is registered for one organization, but the connection is using a shared Microsoft sign-in mode. In Installation setup, choose Only this organization and enter its organization directory ID or verified domain, or change the Entra app to accept accounts from several organizations.");
            if(safe.contains("AADSTS90002"))return configuration("The configured Microsoft organization was not found. Check the organization directory ID or verified organization domain in Installation setup and copy it from the Entra application Overview page.");
        }
        if(provider==RemoteStorageProviderType.GITHUB){
            if(code.equals("device_flow_disabled"))return configuration("GitHub Device Flow is disabled for this OAuth App. In GitHub OAuth App settings enable Device Flow, or disable device sign-in in HYDRA and configure the server-side client secret (HYDRA_GITHUB_OAUTH_CLIENT_SECRET) for browser authorization.");
            if(code.equals("incorrect_client_credentials")||code.equals("invalid_client")||lower.contains("client id"))return configuration("GitHub rejected the OAuth application identity. Copy the Client ID from the GitHub OAuth App used by this HYDRA installation. If browser authorization is selected, the server must also have the matching client secret configured as HYDRA_GITHUB_OAUTH_CLIENT_SECRET.");
            if(code.contains("redirect")||lower.contains("redirect_uri")||lower.contains("callback"))return configuration("GitHub rejected the callback URL. Register the exact HYDRA callback URL in the OAuth App, including scheme, host, port and path.");
        }
        if(provider==RemoteStorageProviderType.GITLAB){
            if(code.equals("unsupported_grant_type")||lower.contains("authorize_device")||lower.contains("device authorization")||lower.contains("device flow"))return configuration("This GitLab server does not provide the selected Device Authorization flow. Use browser authorization with PKCE, or use GitLab 17.9+ where Device Authorization is generally available.");
            if(code.equals("invalid_client")||code.equals("unauthorized_client")||lower.contains("client authentication"))return configuration("GitLab rejected the OAuth application identity. Copy the Application ID from the GitLab application. For PKCE/public-client use, register the application so it does not require a secret; otherwise configure the matching client secret on the HYDRA server as HYDRA_GITLAB_OAUTH_CLIENT_SECRET.");
            if(code.equals("invalid_scope"))return configuration("GitLab rejected one or more OAuth scopes. Check the scopes configured for the GitLab application and the HYDRA connection.");
            if(code.contains("redirect")||lower.contains("redirect_uri")||lower.contains("redirect uri"))return configuration("GitLab rejected the redirect URI. Register the exact HYDRA callback URL in the GitLab application.");
        }
        if(provider==RemoteStorageProviderType.GOOGLE_DRIVE){
            if(code.equals("redirect_uri_mismatch")||lower.contains("redirect_uri_mismatch")||lower.contains("redirect uri mismatch"))return configuration("Google rejected the redirect URI. For a local HYDRA desktop/loopback installation use a Desktop OAuth client. For a hosted HYDRA server use a Web application OAuth client and register the exact callback URL shown in Installation setup.");
            if(code.equals("invalid_client")||code.equals("unauthorized_client")||lower.contains("oauth client was deleted"))return configuration("Google rejected the OAuth client. Copy the Client ID from the OAuth client created for this HYDRA installation. The Web application also requires its matching client secret on the HYDRA server as HYDRA_GOOGLE_OAUTH_CLIENT_SECRET.");
            if(code.equals("org_internal")||lower.contains("org_internal"))return configuration("The Google OAuth application is restricted to an organization that does not include this account. Update the OAuth consent/audience configuration or sign in with an allowed account.");
            if(code.equals("admin_policy_enforced")||lower.contains("admin_policy_enforced"))return configuration("Google Workspace policy blocked this OAuth authorization. Ask the Workspace administrator to allow the application or required Drive scopes.");
            if(code.equals("invalid_scope"))return configuration("Google rejected one or more OAuth scopes. Check the Google consent-screen/API configuration and the scopes configured in HYDRA.");
        }
        if(provider==RemoteStorageProviderType.DROPBOX){
            if(code.equals("redirect_uri_mismatch")||lower.contains("redirect_uri_mismatch")||lower.contains("redirect uri"))return configuration("Dropbox rejected the redirect URI. Add the exact HYDRA callback URL shown in Installation setup to the Dropbox app Redirect URIs.");
            if(code.equals("invalid_client")||lower.contains("invalid_client")||lower.contains("app key"))return configuration("Dropbox rejected the application identity. Copy the App key from the Dropbox App Console for the application used by this HYDRA installation.");
            if(code.equals("invalid_scope")||lower.contains("scope"))return configuration("Dropbox rejected one or more OAuth scopes. Enable the required permissions in the Dropbox app and keep HYDRA scopes consistent with them.");
        }
        if(code.equals("access_denied")||code.equals("authorization_declined"))return new RemoteAuthException(RemoteAuthException.Reason.TOKEN_EXCHANGE_FAILED,"Authorization was cancelled or denied on the provider page");
        String providerName=provider==null?"OAuth provider":provider.name().replace('_',' ');
        String suffix=safe.isBlank()?(code.isBlank()?"The provider rejected the OAuth request":code):safe;
        return new RemoteAuthException(RemoteAuthException.Reason.TOKEN_EXCHANGE_FAILED,providerName+" authorization failed: "+suffix);
    }

    /** Descripción: Ejecuta la operación «first» correspondiente a la responsabilidad de RemoteOAuthProviderErrorMapper. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String first(String... values){for(String value:values)if(value!=null&&!value.isBlank())return value;return "";}
    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de RemoteOAuthProviderErrorMapper. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalize(String value){return value==null?"":value.trim().toLowerCase(Locale.ROOT);}
    /** Descripción: Ejecuta la operación «configuration» correspondiente a la responsabilidad de RemoteOAuthProviderErrorMapper. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static RemoteAuthException configuration(String message){return new RemoteAuthException(RemoteAuthException.Reason.PROVIDER_NOT_CONFIGURED,message);}
}
