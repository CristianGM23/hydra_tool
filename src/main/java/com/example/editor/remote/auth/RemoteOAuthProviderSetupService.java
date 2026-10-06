package com.example.editor.remote.auth;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import com.example.editor.remote.storage.RemoteStorageProviderType;

/** Applies safe public OAuth application settings while secrets remain environment/vault managed. */
@Service
public class RemoteOAuthProviderSetupService {
    private static final Pattern CLIENT_ID=Pattern.compile("[^\\p{Cntrl}]{1,256}");
    private static final Pattern MICROSOFT_CLIENT_ID=Pattern.compile("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern GITHUB_CLIENT_ID=Pattern.compile("[A-Za-z0-9._-]{8,128}");
    private static final Pattern GITLAB_CLIENT_ID=Pattern.compile("[A-Za-z0-9_-]{8,128}");
    private static final Pattern GOOGLE_CLIENT_ID=Pattern.compile("[A-Za-z0-9_-]+\\.apps\\.googleusercontent\\.com");
    private static final Pattern DROPBOX_CLIENT_ID=Pattern.compile("[A-Za-z0-9_-]{8,64}");
    private static final Pattern TENANT=Pattern.compile("[A-Za-z0-9._-]{1,120}");
    private final RemoteAuthProperties properties;
    private final RemoteOAuthProviderSetupStore store;
    private final Map<RemoteStorageProviderType,RemoteOAuthProviderSetupValue> baseline=new EnumMap<>(RemoteStorageProviderType.class);
    private final Map<RemoteStorageProviderType,RemoteOAuthProviderSetupValue> overrides=new EnumMap<>(RemoteStorageProviderType.class);

    /** Descripción: Inicializa una instancia de RemoteOAuthProviderSetupService con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; store: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public RemoteOAuthProviderSetupService(RemoteAuthProperties properties,RemoteOAuthProviderSetupStore store){this.properties=properties;this.store=store;}

    @PostConstruct public synchronized void initialize(){
        for(RemoteStorageProviderType provider:supported())baseline.put(provider,snapshot(properties.provider(provider)));
        if(!properties.isAllowUiProviderSetup())return;
        try {
            for(Map.Entry<RemoteStorageProviderType,RemoteOAuthProviderSetupValue> entry:store.load().entrySet()){
                if(!supported().contains(entry.getKey()))continue;
                try {
                    RemoteOAuthProviderSetupValue checked=validated(entry.getKey(),new RemoteOAuthProviderSetupRequest(entry.getValue().clientId(),entry.getValue().tenant(),entry.getValue().scopes(),entry.getValue().deviceFlowPreferred()));
                    overrides.put(entry.getKey(),checked);apply(entry.getKey(),checked);
                } catch(RuntimeException exception){
                    com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/remote/auth/RemoteOAuthProviderSetupService.java#"+entry.getKey(),exception);
                }
            }
        } catch(RuntimeException exception){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/remote/auth/RemoteOAuthProviderSetupService.java#store",exception);}
    }

    /** Descripción: Obtiene la información correspondiente a «list» dentro de RemoteOAuthProviderSetupService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public synchronized List<RemoteOAuthProviderSetupDescriptor> list(){return supported().stream().map(this::descriptor).toList();}

    /** Descripción: Genera la ficha pública y segura que HYDRA puede aportar al registrar su aplicación en un proveedor externo. Entradas: provider: proveedor reconocido. Salida: devuelve únicamente URL de administración, callback, scopes, tipo de aplicación y nombres de variables; jamás devuelve secretos, tokens ni credenciales. */ public synchronized RemoteOAuthRegistrationKit registrationKit(RemoteStorageProviderType provider){
        requireSupported(provider);RemoteAuthProperties.Provider value=properties.provider(provider);String callback=properties.callbackUrl();boolean loopback=RemoteOAuthConfigurationRules.loopbackCallback(properties);
        boolean deviceSupported=provider==RemoteStorageProviderType.GITHUB||provider==RemoteStorageProviderType.GITLAB||provider==RemoteStorageProviderType.ONEDRIVE;
        boolean devicePreferred=deviceSupported&&value.isDeviceFlowPreferred();boolean callbackRequired=!devicePreferred&&!(provider==RemoteStorageProviderType.GOOGLE_DRIVE&&loopback);
        boolean secretRequired=(provider==RemoteStorageProviderType.GITHUB&&!devicePreferred)||(provider==RemoteStorageProviderType.GOOGLE_DRIVE&&!loopback)||(provider==RemoteStorageProviderType.ONEDRIVE&&!devicePreferred&&!loopback);
        return new RemoteOAuthRegistrationKit(provider,displayName(provider),administrationUrl(provider),applicationType(provider,loopback,devicePreferred),
                "HYDRA - "+displayName(provider),clientIdLabel(provider),callback,callbackRequired,true,deviceSupported,devicePreferred,secretRequired,secretEnvironmentVariable(provider),value.getScopes());
    }

    /** Descripción: Obtiene el nombre visible del proveedor para la ficha pública de registro. Entradas: provider: proveedor reconocido. Salida: devuelve un nombre estable sin datos de usuario. */ private String displayName(RemoteStorageProviderType provider){return switch(provider){case GITHUB->"GitHub";case GITLAB->"GitLab";case ONEDRIVE->"Microsoft / OneDrive";case GOOGLE_DRIVE->"Google Drive";case DROPBOX->"Dropbox";case GENERIC_GIT->"Git";};}
    /** Descripción: Obtiene la consola oficial donde se registra la aplicación. Entradas: provider: proveedor reconocido. Salida: devuelve una URL fija controlada por HYDRA, no una URL suministrada por el usuario. */ private String administrationUrl(RemoteStorageProviderType provider){return switch(provider){case GITHUB->"https://github.com/settings/developers";case GITLAB->"https://gitlab.com/-/profile/applications";case ONEDRIVE->"https://entra.microsoft.com/#view/Microsoft_AAD_RegisteredApps/ApplicationsListBlade";case GOOGLE_DRIVE->"https://console.cloud.google.com/apis/credentials";case DROPBOX->"https://www.dropbox.com/developers/apps";case GENERIC_GIT->"";};}
    /** Descripción: Explica el tipo de aplicación que debe crearse según el proveedor y el despliegue. Entradas: provider, loopback y preferencia de flujo de dispositivo. Salida: devuelve una etiqueta técnica pública. */ private String applicationType(RemoteStorageProviderType provider,boolean loopback,boolean devicePreferred){return switch(provider){case GITHUB->devicePreferred?"OAuth App with Device Flow":"OAuth App";case GITLAB->devicePreferred?"OAuth application with Device Authorization":"OAuth application / public PKCE client";case ONEDRIVE->devicePreferred?"Public client application / device code":loopback?"Desktop/public client with PKCE":"Web application / confidential server client";case GOOGLE_DRIVE->loopback?"Desktop app OAuth client":"Web application OAuth client";case DROPBOX->"Scoped access OAuth app";case GENERIC_GIT->"";};}
    /** Descripción: Obtiene el nombre del identificador público que el usuario copiará desde la consola del proveedor. Entradas: provider: proveedor reconocido. Salida: devuelve una etiqueta sin material confidencial. */ private String clientIdLabel(RemoteStorageProviderType provider){return switch(provider){case GITHUB,GOOGLE_DRIVE->"Client ID";case GITLAB->"Application ID";case ONEDRIVE->"Application (client) ID";case DROPBOX->"App key";case GENERIC_GIT->"";};}
    /** Descripción: Obtiene exclusivamente el nombre de la variable de entorno donde un administrador puede aportar un secreto cuando el flujo lo requiere. Entradas: provider: proveedor reconocido. Salida: devuelve el nombre de la variable, nunca su valor. */ private String secretEnvironmentVariable(RemoteStorageProviderType provider){return switch(provider){case GITHUB->"HYDRA_GITHUB_OAUTH_CLIENT_SECRET";case GITLAB->"HYDRA_GITLAB_OAUTH_CLIENT_SECRET";case ONEDRIVE->"HYDRA_MICROSOFT_OAUTH_CLIENT_SECRET";case GOOGLE_DRIVE->"HYDRA_GOOGLE_OAUTH_CLIENT_SECRET";case DROPBOX->"HYDRA_DROPBOX_OAUTH_CLIENT_SECRET";case GENERIC_GIT->"";};}

    /** Descripción: Actualiza el estado gestionado por RemoteOAuthProviderSetupService mediante la operación «update». Entradas: provider: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public synchronized RemoteOAuthProviderSetupDescriptor update(RemoteStorageProviderType provider,RemoteOAuthProviderSetupRequest request){
        requireUiSetup();requireSupported(provider);RemoteOAuthProviderSetupValue next=validated(provider,request);store.put(provider,next);overrides.put(provider,next);apply(provider,next);return descriptor(provider);
    }

    /** Descripción: Ejecuta la operación «reset» correspondiente a la responsabilidad de RemoteOAuthProviderSetupService. Entradas: provider: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public synchronized RemoteOAuthProviderSetupDescriptor reset(RemoteStorageProviderType provider){
        requireUiSetup();requireSupported(provider);store.delete(provider);overrides.remove(provider);apply(provider,baseline.get(provider));return descriptor(provider);
    }

    /** Descripción: Ejecuta la operación «descriptor» correspondiente a la responsabilidad de RemoteOAuthProviderSetupService. Entradas: provider: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteOAuthProviderSetupDescriptor descriptor(RemoteStorageProviderType provider){
        RemoteAuthProperties.Provider value=properties.provider(provider);boolean override=overrides.containsKey(provider);
        boolean ready=properties.oauthReady(provider,value);
        String callback=properties.callbackUrl();
        return new RemoteOAuthProviderSetupDescriptor(provider,value.getClientId(),value.getTenant(),value.getScopes(),value.isDeviceFlowPreferred(),override,ready,!value.getClientSecret().isBlank(),properties.isAllowUiProviderSetup(),callback);
    }

    /** Descripción: Valida las condiciones necesarias para «validated» y rechaza entradas no válidas. Entradas: provider: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteOAuthProviderSetupValue validated(RemoteStorageProviderType provider,RemoteOAuthProviderSetupRequest request){
        if(request==null)throw invalid("OAuth provider setup is required");String clientId=request.clientId()==null?"":request.clientId().trim();
        if(!CLIENT_ID.matcher(clientId).matches())throw invalid("OAuth client ID must contain 1 to 256 printable characters");
        validateProviderClientId(provider,clientId);
        String tenant=request.tenant()==null?"":request.tenant().trim();
        if(provider==RemoteStorageProviderType.ONEDRIVE){
            if(tenant.isBlank())tenant="common";
            if(tenant.equalsIgnoreCase("common")||tenant.equalsIgnoreCase("organizations")||tenant.equalsIgnoreCase("consumers"))tenant=tenant.toLowerCase(java.util.Locale.ROOT);
            if(!TENANT.matcher(tenant).matches())throw invalid("Microsoft organization identifier must contain only letters, numbers, dot, underscore or hyphen");
            if(tenant.equalsIgnoreCase(clientId))throw invalid("Microsoft organization identifier must be the organization directory ID or verified organization domain, not the Application (client) ID");
        }
        else tenant=properties.provider(provider).getTenant();
        List<String> scopes=normalizeScopes(request.scopes(),properties.provider(provider).getScopes());
        boolean device=request.deviceFlowPreferred()==null?properties.provider(provider).isDeviceFlowPreferred():request.deviceFlowPreferred();
        if(provider!=RemoteStorageProviderType.GITHUB&&provider!=RemoteStorageProviderType.GITLAB&&provider!=RemoteStorageProviderType.ONEDRIVE)device=false;
        return new RemoteOAuthProviderSetupValue(clientId,tenant,scopes,device);
    }


    /** Descripción: Valida las condiciones necesarias para «validateProviderClientId» y rechaza entradas no válidas. Entradas: provider: dato de entrada necesario para la operación; clientId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateProviderClientId(RemoteStorageProviderType provider,String clientId){
        switch(provider){
            case ONEDRIVE -> {if(!MICROSOFT_CLIENT_ID.matcher(clientId).matches())throw invalid("Microsoft Application (client) ID must be the GUID shown on the Entra application Overview page");}
            case GITHUB -> {if(!GITHUB_CLIENT_ID.matcher(clientId).matches())throw invalid("GitHub Client ID must be the public identifier shown on the OAuth App page, not the client secret");}
            case GITLAB -> {if(!GITLAB_CLIENT_ID.matcher(clientId).matches())throw invalid("GitLab Application ID must be the public application identifier, not the Secret");}
            case GOOGLE_DRIVE -> {if(!GOOGLE_CLIENT_ID.matcher(clientId).matches())throw invalid("Google Client ID must be the OAuth client identifier ending in .apps.googleusercontent.com");}
            case DROPBOX -> {if(!DROPBOX_CLIENT_ID.matcher(clientId).matches())throw invalid("Dropbox App key must be the public App key from the Dropbox App Console, not the App secret");}
            case GENERIC_GIT -> throw invalid("Provider does not support guided OAuth setup");
        }
    }

    /** Descripción: Ejecuta la operación «normalizeScopes» correspondiente a la responsabilidad de RemoteOAuthProviderSetupService. Entradas: requested: solicitud con los datos necesarios para ejecutar la operación; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> normalizeScopes(List<String> requested,List<String> fallback){
        List<String> source=requested==null||requested.isEmpty()?fallback:requested;List<String> result=new ArrayList<>();
        for(String raw:source){String value=raw==null?"":raw.trim();if(value.isBlank())continue;if(value.length()>200||value.chars().anyMatch(Character::isISOControl))throw invalid("OAuth scopes contain an invalid value");if(!result.contains(value))result.add(value);if(result.size()>32)throw invalid("OAuth provider setup cannot contain more than 32 scopes");}
        if(result.isEmpty())throw invalid("At least one OAuth scope is required");return List.copyOf(result);
    }

    /** Descripción: Actualiza el estado gestionado por RemoteOAuthProviderSetupService mediante la operación «apply». Entradas: provider: dato de entrada necesario para la operación; setup: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void apply(RemoteStorageProviderType provider,RemoteOAuthProviderSetupValue setup){RemoteAuthProperties.Provider target=properties.provider(provider);target.setClientId(setup.clientId());target.setTenant(setup.tenant());target.setScopes(setup.scopes());target.setDeviceFlowPreferred(setup.deviceFlowPreferred());}
    /** Descripción: Ejecuta la operación «snapshot» correspondiente a la responsabilidad de RemoteOAuthProviderSetupService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteOAuthProviderSetupValue snapshot(RemoteAuthProperties.Provider value){return new RemoteOAuthProviderSetupValue(value.getClientId(),value.getTenant(),value.getScopes(),value.isDeviceFlowPreferred());}
    /** Descripción: Ejecuta la operación «supported» correspondiente a la responsabilidad de RemoteOAuthProviderSetupService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private List<RemoteStorageProviderType> supported(){return List.of(RemoteStorageProviderType.GITHUB,RemoteStorageProviderType.GITLAB,RemoteStorageProviderType.ONEDRIVE,RemoteStorageProviderType.GOOGLE_DRIVE,RemoteStorageProviderType.DROPBOX);}
    /** Descripción: Valida las condiciones necesarias para «requireSupported» y rechaza entradas no válidas. Entradas: provider: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireSupported(RemoteStorageProviderType provider){if(provider==null||!supported().contains(provider))throw invalid("Provider does not support guided OAuth setup");}
    /** Descripción: Valida las condiciones necesarias para «requireUiSetup» y rechaza entradas no válidas. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireUiSetup(){if(!properties.isAllowUiProviderSetup())throw new RemoteAuthException(RemoteAuthException.Reason.DISABLED,"OAuth provider setup from the HYDRA UI is disabled by the active server profile");}
    /** Descripción: Ejecuta la operación «invalid» correspondiente a la responsabilidad de RemoteOAuthProviderSetupService. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteAuthException invalid(String message){return new RemoteAuthException(RemoteAuthException.Reason.INVALID_REQUEST,message);}
}
