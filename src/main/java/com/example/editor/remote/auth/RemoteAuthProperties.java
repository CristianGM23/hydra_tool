package com.example.editor.remote.auth;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import com.example.editor.remote.storage.RemoteStorageProviderType;

/** Configuración local y server-side de OAuth, PKCE y del almacén cifrado de credenciales. */
@Component
@ConfigurationProperties(prefix="hydra.remote-auth")
public class RemoteAuthProperties {
    private boolean enabled=true;
    private String callbackBaseUrl="http://127.0.0.1:8081";
    private Duration flowTtl=Duration.ofMinutes(5);
    private Duration deviceFlowTtl=Duration.ofMinutes(15);
    private int maximumPendingFlows=32;
    private int devicePollLockStripes=256;
    private Duration devicePollLockWait=Duration.ofMillis(250);
    private int refreshLockStripes=256;
    private Duration refreshLockWait=Duration.ofSeconds(2);
    private Duration tokenRefreshSkew=Duration.ofMinutes(2);
    private Duration requestTimeout=Duration.ofSeconds(30);
    private Duration connectTimeout=Duration.ofSeconds(10);
    private boolean allowLoopbackHttp=true;
    private boolean allowUiProviderSetup=true;
    private Path providerSetupFile=Path.of("./data/remote-auth/provider-setups.json");
    private final Vault vault=new Vault();
    private Map<RemoteStorageProviderType,Provider> providers=new EnumMap<>(RemoteStorageProviderType.class);

    /** Descripción: Inicializa una instancia de RemoteAuthProperties con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ public RemoteAuthProperties(){
        providers.put(RemoteStorageProviderType.GITHUB,githubDefaults());
        providers.put(RemoteStorageProviderType.GITLAB,gitlabDefaults());
        providers.put(RemoteStorageProviderType.ONEDRIVE,microsoftDefaults());
        providers.put(RemoteStorageProviderType.GOOGLE_DRIVE,googleDriveDefaults());
        providers.put(RemoteStorageProviderType.DROPBOX,dropboxDefaults());
        providers.put(RemoteStorageProviderType.GENERIC_GIT,genericGitDefaults());
    }

    /** Descripción: Evalúa la condición «isEnabled» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isEnabled(){return enabled;}
    /** Descripción: Actualiza el estado gestionado por Vault mediante la operación «setEnabled». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setEnabled(boolean value){enabled=value;}
    /** Descripción: Obtiene la información correspondiente a «getCallbackBaseUrl» dentro de Vault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getCallbackBaseUrl(){return callbackBaseUrl;}
    /** Descripción: Actualiza el estado gestionado por Vault mediante la operación «setCallbackBaseUrl». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setCallbackBaseUrl(String value){callbackBaseUrl=normalize(value);}
    /** Descripción: Obtiene la información correspondiente a «getFlowTtl» dentro de Vault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Duration getFlowTtl(){return flowTtl;}
    /** Descripción: Actualiza el estado gestionado por Vault mediante la operación «setFlowTtl». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setFlowTtl(Duration value){flowTtl=value==null?Duration.ofMinutes(5):value;}
    /** Descripción: Obtiene la información correspondiente a «getDeviceFlowTtl» dentro de Vault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Duration getDeviceFlowTtl(){return deviceFlowTtl;}
    /** Descripción: Actualiza el estado gestionado por Vault mediante la operación «setDeviceFlowTtl». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setDeviceFlowTtl(Duration value){deviceFlowTtl=value==null?Duration.ofMinutes(15):value;}
    /** Descripción: Obtiene la información correspondiente a «getMaximumPendingFlows» dentro de Vault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumPendingFlows(){return maximumPendingFlows;}
    /** Descripción: Actualiza el estado gestionado por Vault mediante la operación «setMaximumPendingFlows». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumPendingFlows(int value){maximumPendingFlows=Math.max(1,Math.min(4_096,value));}
    /** Descripción: Obtiene el número fijo de stripes usado para serializar polling de un mismo state sin mapas de locks crecientes. Entradas: ninguna. Salida: número de stripes. */ public int getDevicePollLockStripes(){return devicePollLockStripes;}
    /** Descripción: Configura el número fijo de stripes de polling aplicando límites defensivos. Entradas: value. Salida: actualiza la configuración. */ public void setDevicePollLockStripes(int value){devicePollLockStripes=Math.max(16,Math.min(4_096,value));}
    /** Descripción: Obtiene la espera máxima para adquirir el lock de polling de un state. Entradas: ninguna. Salida: duración acotada. */ public Duration getDevicePollLockWait(){return devicePollLockWait;}
    /** Descripción: Configura una espera corta y acotada para evitar polling duplicado sin bloquear peticiones indefinidamente. Entradas: value. Salida: actualiza la configuración. */ public void setDevicePollLockWait(Duration value){long millis=value==null?250L:value.toMillis();devicePollLockWait=Duration.ofMillis(Math.max(0L,Math.min(5_000L,millis)));}
    /** Descripción: Obtiene el número fijo de stripes usado para serializar el refresh de una conexión sin mantener un mapa creciente de locks. Entradas: ninguna. Salida: número de stripes. */ public int getRefreshLockStripes(){return refreshLockStripes;}
    /** Descripción: Configura el número fijo de stripes de refresh aplicando límites defensivos. Entradas: value. Salida: actualiza la configuración. */ public void setRefreshLockStripes(int value){refreshLockStripes=Math.max(16,Math.min(4_096,value));}
    /** Descripción: Obtiene la espera máxima de un solicitante para incorporarse al refresh single-flight de una conexión. Entradas: ninguna. Salida: duración acotada. */ public Duration getRefreshLockWait(){return refreshLockWait;}
    /** Descripción: Configura la espera máxima del lock de refresh para evitar bloqueos indefinidos detrás de un proveedor lento. Entradas: value. Salida: actualiza la configuración. */ public void setRefreshLockWait(Duration value){long millis=value==null?2_000L:value.toMillis();refreshLockWait=Duration.ofMillis(Math.max(0L,Math.min(30_000L,millis)));}
    /** Descripción: Obtiene la información correspondiente a «getTokenRefreshSkew» dentro de Vault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Duration getTokenRefreshSkew(){return tokenRefreshSkew;}
    /** Descripción: Actualiza el estado gestionado por Vault mediante la operación «setTokenRefreshSkew». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setTokenRefreshSkew(Duration value){tokenRefreshSkew=value==null?Duration.ofMinutes(2):value;}
    /** Descripción: Obtiene la información correspondiente a «getRequestTimeout» dentro de Vault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Duration getRequestTimeout(){return requestTimeout;}
    /** Descripción: Actualiza el estado gestionado por Vault mediante la operación «setRequestTimeout». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setRequestTimeout(Duration value){requestTimeout=value==null?Duration.ofSeconds(30):value;}
    /** Descripción: Obtiene la información correspondiente a «getConnectTimeout» dentro de Vault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Duration getConnectTimeout(){return connectTimeout;}
    /** Descripción: Actualiza el estado gestionado por Vault mediante la operación «setConnectTimeout». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setConnectTimeout(Duration value){connectTimeout=value==null?Duration.ofSeconds(10):value;}
    /** Descripción: Evalúa la condición «isAllowLoopbackHttp» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isAllowLoopbackHttp(){return allowLoopbackHttp;}
    /** Descripción: Actualiza el estado gestionado por Vault mediante la operación «setAllowLoopbackHttp». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setAllowLoopbackHttp(boolean value){allowLoopbackHttp=value;}
    /** Descripción: Evalúa la condición «isAllowUiProviderSetup» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isAllowUiProviderSetup(){return allowUiProviderSetup;}
    /** Descripción: Actualiza el estado gestionado por Vault mediante la operación «setAllowUiProviderSetup». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setAllowUiProviderSetup(boolean value){allowUiProviderSetup=value;}
    /** Descripción: Obtiene la información correspondiente a «getProviderSetupFile» dentro de Vault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Path getProviderSetupFile(){return providerSetupFile;}
    /** Descripción: Actualiza el estado gestionado por Vault mediante la operación «setProviderSetupFile». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setProviderSetupFile(Path value){providerSetupFile=value==null?Path.of("./data/remote-auth/provider-setups.json"):value;}
    /** Descripción: Obtiene la información correspondiente a «getVault» dentro de Vault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Vault getVault(){return vault;}
    /** Descripción: Obtiene la información correspondiente a «getProviders» dentro de Vault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Map<RemoteStorageProviderType,Provider> getProviders(){return providers;}
    /** Descripción: Actualiza el estado gestionado por Vault mediante la operación «setProviders». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setProviders(Map<RemoteStorageProviderType,Provider> value){providers=value==null?new EnumMap<>(RemoteStorageProviderType.class):new EnumMap<>(value);}
    /** Descripción: Ejecuta la operación «provider» correspondiente a la responsabilidad de Vault. Entradas: type: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public Provider provider(RemoteStorageProviderType type){
        Provider value=providers.computeIfAbsent(type,ignored->defaults(type));
        applyDefaults(type,value);
        return value;
    }

    /**
     * Single production readiness rule used by both the setup UI service and the runtime.
     * Kept on the always-present configuration component so a partial overlay cannot leave
     * either service depending on an optional helper source file.
     */
    /** Descripción: Ejecuta la operación «oauthReady» correspondiente a la responsabilidad de Vault. Entradas: type: dato de entrada necesario para la operación; auth: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean oauthReady(RemoteStorageProviderType type,Provider auth){
        if(type==null||auth==null||!auth.isEnabled()||auth.getClientId().isBlank())return false;
        return switch(type){
            case GITHUB -> auth.isDeviceFlowPreferred()||!auth.getClientSecret().isBlank();
            case GOOGLE_DRIVE -> loopbackCallback()||!auth.getClientSecret().isBlank();
            case ONEDRIVE -> auth.isDeviceFlowPreferred()||loopbackCallback()||!auth.getClientSecret().isBlank();
            case GITLAB,DROPBOX -> true;
            case GENERIC_GIT -> false;
        };
    }

    /** Descripción: Ejecuta la operación «loopbackCallback» correspondiente a la responsabilidad de Vault. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean loopbackCallback(){
        try {
            URI uri=URI.create(callbackBaseUrl);String host=uri.getHost();
            return host!=null&&(host.equalsIgnoreCase("localhost")||host.equals("127.0.0.1")||host.equals("::1")||host.equals("[::1]"));
        } catch(IllegalArgumentException exception){return false;}
    }

    /** Descripción: Ejecuta la operación «callbackUrl» correspondiente a la responsabilidad de Vault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String callbackUrl(){
        String base=callbackBaseUrl;
        while(base.endsWith("/"))base=base.substring(0,base.length()-1);
        return base+"/api/remote-auth/callback";
    }

    public static class Vault {
        private boolean persistent=true;
        private Path root=Path.of("./data/remote-auth/vault");
        private String masterKeyEnvironmentVariable="HYDRA_REMOTE_VAULT_MASTER_KEY";
        private boolean allowProtectedLocalMasterKey=false;
        private String localMasterKeyFile=".hydra-vault-key";
        private int maximumConnections=32;
        /** Descripción: Evalúa la condición «isPersistent» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isPersistent(){return persistent;}
        /** Descripción: Actualiza el estado gestionado por Vault mediante la operación «setPersistent». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setPersistent(boolean value){persistent=value;}
        /** Descripción: Obtiene la información correspondiente a «getRoot» dentro de Vault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Path getRoot(){return root;}
        /** Descripción: Actualiza el estado gestionado por Vault mediante la operación «setRoot». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setRoot(Path value){root=value==null?Path.of("./data/remote-auth/vault"):value;}
        /** Descripción: Obtiene la información correspondiente a «getMasterKeyEnvironmentVariable» dentro de Vault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getMasterKeyEnvironmentVariable(){return masterKeyEnvironmentVariable;}
        /** Descripción: Actualiza el estado gestionado por Vault mediante la operación «setMasterKeyEnvironmentVariable». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMasterKeyEnvironmentVariable(String value){masterKeyEnvironmentVariable=normalize(value);}
        /** Descripción: Evalúa la condición «isAllowProtectedLocalMasterKey» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isAllowProtectedLocalMasterKey(){return allowProtectedLocalMasterKey;}
        /** Descripción: Actualiza el estado gestionado por Vault mediante la operación «setAllowProtectedLocalMasterKey». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setAllowProtectedLocalMasterKey(boolean value){allowProtectedLocalMasterKey=value;}
        /** Descripción: Obtiene la información correspondiente a «getLocalMasterKeyFile» dentro de Vault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getLocalMasterKeyFile(){return localMasterKeyFile;}
        /** Descripción: Actualiza el estado gestionado por Vault mediante la operación «setLocalMasterKeyFile». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setLocalMasterKeyFile(String value){localMasterKeyFile=normalize(value);}
        /** Descripción: Obtiene la información correspondiente a «getMaximumConnections» dentro de Vault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumConnections(){return maximumConnections;}
        /** Descripción: Actualiza el estado gestionado por Vault mediante la operación «setMaximumConnections». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumConnections(int value){maximumConnections=Math.max(1,value);}
    }

    public static class Provider {
        private boolean enabled=true;
        private String clientId="";
        private String clientSecret="";
        private String authorizationUrl="";
        private String deviceAuthorizationUrl="";
        private boolean deviceFlowPreferred=false;
        private String tokenUrl="";
        private String accountUrl="";
        private String revokeUrl="";
        private String tenant="common";
        private List<String> scopes=new ArrayList<>();
        /** Descripción: Evalúa la condición «isEnabled» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isEnabled(){return enabled;}
        /** Descripción: Actualiza el estado gestionado por Vault mediante la operación «setEnabled». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setEnabled(boolean value){enabled=value;}
        /** Descripción: Obtiene la información correspondiente a «getClientId» dentro de Vault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getClientId(){return clientId;}
        /** Descripción: Actualiza el estado gestionado por Vault mediante la operación «setClientId». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setClientId(String value){clientId=normalize(value);}
        /** Descripción: Obtiene la información correspondiente a «getClientSecret» dentro de Vault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getClientSecret(){return clientSecret;}
        /** Descripción: Actualiza el estado gestionado por Vault mediante la operación «setClientSecret». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setClientSecret(String value){clientSecret=value==null?"":value.trim();}
        /** Descripción: Obtiene la información correspondiente a «getAuthorizationUrl» dentro de Vault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getAuthorizationUrl(){return authorizationUrl;}
        /** Descripción: Actualiza el estado gestionado por Vault mediante la operación «setAuthorizationUrl». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setAuthorizationUrl(String value){authorizationUrl=normalize(value);}
        /** Descripción: Obtiene la información correspondiente a «getDeviceAuthorizationUrl» dentro de Vault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getDeviceAuthorizationUrl(){return deviceAuthorizationUrl;}
        /** Descripción: Actualiza el estado gestionado por Vault mediante la operación «setDeviceAuthorizationUrl». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setDeviceAuthorizationUrl(String value){deviceAuthorizationUrl=normalize(value);}
        /** Descripción: Evalúa la condición «isDeviceFlowPreferred» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isDeviceFlowPreferred(){return deviceFlowPreferred;}
        /** Descripción: Actualiza el estado gestionado por Vault mediante la operación «setDeviceFlowPreferred». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setDeviceFlowPreferred(boolean value){deviceFlowPreferred=value;}
        /** Descripción: Obtiene la información correspondiente a «getTokenUrl» dentro de Vault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getTokenUrl(){return tokenUrl;}
        /** Descripción: Actualiza el estado gestionado por Vault mediante la operación «setTokenUrl». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setTokenUrl(String value){tokenUrl=normalize(value);}
        /** Descripción: Obtiene la información correspondiente a «getAccountUrl» dentro de Vault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getAccountUrl(){return accountUrl;}
        /** Descripción: Actualiza el estado gestionado por Vault mediante la operación «setAccountUrl». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setAccountUrl(String value){accountUrl=normalize(value);}
        /** Descripción: Obtiene la información correspondiente a «getRevokeUrl» dentro de Vault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getRevokeUrl(){return revokeUrl;}
        /** Descripción: Actualiza el estado gestionado por Vault mediante la operación «setRevokeUrl». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setRevokeUrl(String value){revokeUrl=normalize(value);}
        /** Descripción: Obtiene la información correspondiente a «getTenant» dentro de Vault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getTenant(){return tenant;}
        /** Descripción: Actualiza el estado gestionado por Vault mediante la operación «setTenant». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setTenant(String value){tenant=normalize(value);}
        /** Descripción: Obtiene la información correspondiente a «getScopes» dentro de Vault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<String> getScopes(){return scopes;}
        /** Descripción: Actualiza el estado gestionado por Vault mediante la operación «setScopes». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setScopes(List<String> value){scopes=value==null?new ArrayList<>():new ArrayList<>(value);}
    }

    /** Descripción: Ejecuta la operación «defaults» correspondiente a la responsabilidad de Vault. Entradas: type: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static Provider defaults(RemoteStorageProviderType type){return switch(type){case GITHUB->githubDefaults();case GITLAB->gitlabDefaults();case ONEDRIVE->microsoftDefaults();case GOOGLE_DRIVE->googleDriveDefaults();case DROPBOX->dropboxDefaults();case GENERIC_GIT->genericGitDefaults();};}
    /** Descripción: Actualiza el estado gestionado por Vault mediante la operación «applyDefaults». Entradas: type: dato de entrada necesario para la operación; value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void applyDefaults(RemoteStorageProviderType type,Provider value){
        Provider defaults=defaults(type);
        if(value.authorizationUrl.isBlank())value.authorizationUrl=defaults.authorizationUrl;
        if(value.deviceAuthorizationUrl.isBlank())value.deviceAuthorizationUrl=defaults.deviceAuthorizationUrl;
        if(value.tokenUrl.isBlank())value.tokenUrl=defaults.tokenUrl;
        if(value.accountUrl.isBlank())value.accountUrl=defaults.accountUrl;
        if(value.revokeUrl.isBlank())value.revokeUrl=defaults.revokeUrl;
        if(value.tenant.isBlank())value.tenant=defaults.tenant;
        if(value.scopes==null||value.scopes.isEmpty())value.scopes=new ArrayList<>(defaults.scopes);
    }
    /** Descripción: Ejecuta la operación «githubDefaults» correspondiente a la responsabilidad de Vault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private static Provider githubDefaults(){
        Provider value=new Provider();
        value.authorizationUrl="https://github.com/login/oauth/authorize";
        value.deviceAuthorizationUrl="https://github.com/login/device/code";
        value.deviceFlowPreferred=false;
        value.tokenUrl="https://github.com/login/oauth/access_token";
        value.accountUrl="https://api.github.com/user";
        value.revokeUrl="https://api.github.com/applications/{clientId}/token";
        value.scopes=new ArrayList<>(List.of("repo"));
        return value;
    }
    /** Descripción: Ejecuta la operación «gitlabDefaults» correspondiente a la responsabilidad de Vault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private static Provider gitlabDefaults(){
        Provider value=new Provider();
        value.authorizationUrl="https://gitlab.com/oauth/authorize";
        value.deviceAuthorizationUrl="https://gitlab.com/oauth/authorize_device";
        value.deviceFlowPreferred=false;
        value.tokenUrl="https://gitlab.com/oauth/token";
        value.accountUrl="https://gitlab.com/api/v4/user";
        value.revokeUrl="https://gitlab.com/oauth/revoke";
        value.scopes=new ArrayList<>(List.of("api"));
        return value;
    }
    /** Descripción: Ejecuta la operación «microsoftDefaults» correspondiente a la responsabilidad de Vault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private static Provider microsoftDefaults(){
        Provider value=new Provider();
        value.authorizationUrl="https://login.microsoftonline.com/{tenant}/oauth2/v2.0/authorize";
        value.deviceAuthorizationUrl="https://login.microsoftonline.com/{tenant}/oauth2/v2.0/devicecode";
        value.deviceFlowPreferred=false;
        value.tokenUrl="https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token";
        value.accountUrl="https://graph.microsoft.com/v1.0/me?$select=id,displayName,userPrincipalName";
        value.scopes=new ArrayList<>(List.of("offline_access","Files.ReadWrite.AppFolder"));
        return value;
    }
    /** Descripción: Ejecuta la operación «googleDriveDefaults» correspondiente a la responsabilidad de Vault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private static Provider googleDriveDefaults(){
        Provider value=new Provider();
        value.authorizationUrl="https://accounts.google.com/o/oauth2/v2/auth";
        value.tokenUrl="https://oauth2.googleapis.com/token";
        value.accountUrl="https://www.googleapis.com/oauth2/v3/userinfo";
        value.revokeUrl="https://oauth2.googleapis.com/revoke";
        value.scopes=new ArrayList<>(List.of("openid","email","profile","https://www.googleapis.com/auth/drive.file"));
        return value;
    }
    /** Descripción: Ejecuta la operación «dropboxDefaults» correspondiente a la responsabilidad de Vault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private static Provider dropboxDefaults(){
        Provider value=new Provider();
        value.authorizationUrl="https://www.dropbox.com/oauth2/authorize";
        value.tokenUrl="https://api.dropboxapi.com/oauth2/token";
        value.accountUrl="https://api.dropboxapi.com/2/users/get_current_account";
        value.revokeUrl="https://api.dropboxapi.com/2/auth/token/revoke";
        value.scopes=new ArrayList<>(List.of("files.content.write","files.content.read","account_info.read"));
        return value;
    }
    /** Descripción: Ejecuta la operación «genericGitDefaults» correspondiente a la responsabilidad de Vault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private static Provider genericGitDefaults(){
        Provider value=new Provider();
        value.setEnabled(false);
        return value;
    }
    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de Vault. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalize(String value){return value==null?"":value.trim();}
}
