package com.example.editor.remote.auth;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;
import com.example.editor.remote.auth.provider.*;
import com.example.editor.remote.storage.RemoteStorageProperties;
import com.example.editor.remote.storage.RemoteStorageProviderType;
import com.example.editor.runtime.StripedKeyLockRegistry;

/** Orquesta OAuth independiente de usuarios, refresco y acceso al vault cifrado. */
@Service
public class RemoteAuthService {
    private final RemoteAuthProperties authProperties;
    private final RemoteStorageProperties storageProperties;
    private final RemoteOAuthProviderRegistry providers;
    private final RemoteCredentialVault vault;
    private final RemoteAuthFlowStore flows;
    private final RemoteAuthOutcomeStore outcomes;
    private final StripedKeyLockRegistry refreshLocks;
    private final StripedKeyLockRegistry devicePollLocks;

    /** Descripción: Inicializa una instancia de RemoteAuthService con sus dependencias y datos de entrada. Entradas: authProperties: configuración que gobierna el comportamiento; storageProperties: configuración que gobierna el comportamiento; providers: dato de entrada necesario para la operación; vault: dato de entrada necesario para la operación; flows: dato de entrada necesario para la operación; outcomes: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public RemoteAuthService(RemoteAuthProperties authProperties,RemoteStorageProperties storageProperties,RemoteOAuthProviderRegistry providers,
            RemoteCredentialVault vault,RemoteAuthFlowStore flows,RemoteAuthOutcomeStore outcomes){this.authProperties=authProperties;this.storageProperties=storageProperties;this.providers=providers;this.vault=vault;this.flows=flows;this.outcomes=outcomes;this.refreshLocks=new StripedKeyLockRegistry(authProperties.getRefreshLockStripes());this.devicePollLocks=new StripedKeyLockRegistry(authProperties.getDevicePollLockStripes());}

    /** Descripción: Ejecuta la operación «profiles» correspondiente a la responsabilidad de RemoteAuthService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<RemoteAuthProfileDescriptor> profiles(){
        requireEnabled();List<RemoteAuthProfileDescriptor> result=new ArrayList<>();
        storageProperties.getProfiles().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->result.add(profileDescriptor(entry.getKey(),entry.getValue())));
        return List.copyOf(result);
    }

    /** Descripción: Crea o prepara el resultado de «start» dentro de RemoteAuthService. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public RemoteAuthStartResponse start(RemoteAuthStartRequest request){
        requireEnabled();if(request==null||request.profileId()==null||request.profileId().isBlank())throw invalid("Remote storage profile is required");
        if(!vault.ready())throw new RemoteAuthException(RemoteAuthException.Reason.VAULT_UNAVAILABLE,"Persistent external connections require a configured credential-vault master key");
        RemoteStorageProperties.Profile storage=storageProfile(request.profileId());RemoteAuthProperties.Provider auth=authProfile(storage.getProvider());RemoteOAuthProvider provider=providers.require(storage.getProvider());
        String state=RemotePkce.randomState();List<String> scopes=provider.scopes(storage,auth);String connectionId=connectionId(request.profileId(),storage);
        if(provider.useDeviceFlow(storage,auth)){
            RemoteDeviceAuthorization device=provider.beginDeviceAuthorization(storage,auth,scopes);Instant expires=earliest(device.expiresAt(),Instant.now().plus(authProperties.getDeviceFlowTtl()));
            flows.put(PendingRemoteAuthFlow.device(state,request.profileId(),connectionId,storage.getProvider(),scopes,device.deviceCode(),device.pollIntervalSeconds(),expires,request.language()));
            return new RemoteAuthStartResponse(request.profileId(),connectionId,state,device.verificationUri().toString(),device.userCode(),true,device.pollIntervalSeconds(),expires);
        }
        String redirect=callbackUri().toString();String verifier=RemotePkce.verifier();Instant expires=Instant.now().plus(authProperties.getFlowTtl());
        URI authorization=RemoteNetworkPolicy.requireSecure(provider.authorizationUri(storage,auth,redirect,state,RemotePkce.challenge(verifier),scopes),authProperties.isAllowLoopbackHttp());
        flows.put(PendingRemoteAuthFlow.authorizationCode(state,request.profileId(),connectionId,storage.getProvider(),verifier,redirect,scopes,expires,request.language()));
        return new RemoteAuthStartResponse(request.profileId(),connectionId,state,authorization.toString(),"",false,0,expires);
    }

    /** Descripción: Ejecuta la operación «poll» correspondiente a la responsabilidad de RemoteAuthService. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public RemoteAuthPollResponse poll(RemoteAuthPollRequest request){
        requireEnabled();if(request==null||request.state()==null||request.state().isBlank())throw invalid("Pending external authorization state is required");
        String state=request.state().trim();RemoteAuthOutcomeStore.Outcome outcome=outcomes.find(state);if(outcome!=null)return terminalOutcome(outcome);
        PendingRemoteAuthFlow flow=flows.peek(state);if(!flow.deviceFlow())return pending(flow,1);
        Instant now=Instant.now();if(flow.nextPollAt().isAfter(now))return pending(flow,secondsUntil(now,flow.nextPollAt()));
        ReentrantLock lock=devicePollLocks.lockFor(state);boolean acquired;
        try{acquired=lock.tryLock(authProperties.getDevicePollLockWait().toMillis(),TimeUnit.MILLISECONDS);}catch(InterruptedException exception){Thread.currentThread().interrupt();throw new RemoteAuthException(RemoteAuthException.Reason.REMOTE_ERROR,"External device authorization polling was interrupted",exception);}
        if(!acquired)return pending(flow,1);
        try{
            outcome=outcomes.find(state);if(outcome!=null)return terminalOutcome(outcome);
            flow=flows.peek(state);now=Instant.now();if(flow.nextPollAt().isAfter(now))return pending(flow,secondsUntil(now,flow.nextPollAt()));
            RemoteStorageProperties.Profile storage=storageProfile(flow.profileId());if(storage.getProvider()!=flow.provider())throw terminalDeviceFailure(flow,new RemoteAuthException(RemoteAuthException.Reason.STATE_MISMATCH,"Remote storage provider changed while authorization was pending"));
            RemoteDevicePollResult result=providers.require(flow.provider()).pollDeviceAuthorization(storage,authProfile(flow.provider()),flow.deviceCode(),flow.scopes());
            if(result.status()==RemoteDevicePollStatus.PENDING||result.status()==RemoteDevicePollStatus.SLOW_DOWN){int interval=Math.max(flow.pollIntervalSeconds(),result.pollIntervalSeconds());PendingRemoteAuthFlow updated=flow.withNextPoll(interval);flows.update(updated);return pending(updated,interval);}
            if(result.status()==RemoteDevicePollStatus.DENIED)throw terminalDeviceFailure(flow,new RemoteAuthException(RemoteAuthException.Reason.TOKEN_EXCHANGE_FAILED,"External device authorization was denied"));
            if(result.status()==RemoteDevicePollStatus.EXPIRED)throw terminalDeviceFailure(flow,new RemoteAuthException(RemoteAuthException.Reason.FLOW_EXPIRED,"External device authorization expired"));
            RemoteCredentialMaterial material=result.credential();if(material==null||material.provider()!=flow.provider())throw terminalDeviceFailure(flow,new RemoteAuthException(RemoteAuthException.Reason.STATE_MISMATCH,"OAuth provider returned credentials for an unexpected provider"));
            consumeDeviceFlow(flow);
            try{vault.store(flow.connectionId(),material);RemoteAuthConnectionDescriptor connection=descriptor(flow.connectionId(),material);outcomes.success(flow.state(),flow.profileId(),connection,flow.expiresAt());return new RemoteAuthPollResponse(flow.state(),flow.profileId(),true,false,0,connection);}
            catch(RemoteAuthException exception){outcomes.failure(flow.state(),flow.profileId(),exception,flow.expiresAt());throw exception;}
            catch(RuntimeException exception){RemoteAuthException safe=new RemoteAuthException(RemoteAuthException.Reason.VAULT_UNAVAILABLE,"External credentials could not be stored after device authorization",exception);outcomes.failure(flow.state(),flow.profileId(),safe,flow.expiresAt());throw safe;}
        }finally{lock.unlock();}
    }

    /** Descripción: Traduce un outcome efímero ya terminal a la respuesta o excepción estable que deben observar todos los polls posteriores. Entradas: outcome. Salida: respuesta autorizada o error terminal coherente. */ private RemoteAuthPollResponse terminalOutcome(RemoteAuthOutcomeStore.Outcome outcome){if(outcome.success())return new RemoteAuthPollResponse(outcome.state(),outcome.profileId(),true,false,0,outcome.connection());throw new RemoteAuthException(outcome.reason()==null?RemoteAuthException.Reason.TOKEN_EXCHANGE_FAILED:outcome.reason(),outcome.message().isBlank()?"External authorization failed":outcome.message());}

    /** Descripción: Retira un device flow terminal y conserva temporalmente su error para que polls concurrentes o repetidos observen el mismo resultado. Entradas: flow y exception. Salida: la misma excepción lista para propagarse. */ private RemoteAuthException terminalDeviceFailure(PendingRemoteAuthFlow flow,RemoteAuthException exception){flows.remove(flow.state());outcomes.failure(flow.state(),flow.profileId(),exception,flow.expiresAt());return exception;}

    /** Descripción: Consume un device flow autorizado y convierte una expiración ocurrida durante el polling remoto en un outcome terminal reconsultable. Entradas: flow. Salida: no devuelve valor; consume el estado o propaga un error estable. */ private void consumeDeviceFlow(PendingRemoteAuthFlow flow){try{flows.consume(flow.state());}catch(RemoteAuthException exception){if(exception.getReason()==RemoteAuthException.Reason.FLOW_EXPIRED)outcomes.failure(flow.state(),flow.profileId(),exception,flow.expiresAt());throw exception;}}

    /** Descripción: Ejecuta la operación «pendingLanguage» correspondiente a la responsabilidad de RemoteAuthService. Entradas: state: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public String pendingLanguage(String state){requireEnabled();return flows.peek(state).language();}

    /** Descripción: Ejecuta la operación «complete» correspondiente a la responsabilidad de RemoteAuthService. Entradas: state: dato de entrada necesario para la operación; code: dato de entrada necesario para la operación; providerError: dato de entrada necesario para la operación; providerErrorDescription: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public RemoteAuthConnectionDescriptor complete(String state,String code,String providerError,String providerErrorDescription){
        requireEnabled();PendingRemoteAuthFlow pending=flows.peek(state);if(pending.deviceFlow())throw invalid("Device authorization must be completed through the protected polling endpoint");PendingRemoteAuthFlow flow=flows.consume(state);
        try {
            if(providerError!=null&&!providerError.isBlank())throw RemoteOAuthProviderErrorMapper.map(flow.provider(),providerError,providerErrorDescription);
            if(code==null||code.isBlank())throw invalid("Authorization callback did not include a code");
            RemoteStorageProperties.Profile storage=storageProfile(flow.profileId());if(storage.getProvider()!=flow.provider())throw new RemoteAuthException(RemoteAuthException.Reason.STATE_MISMATCH,"Remote storage provider changed while authorization was pending");
            RemoteOAuthProvider provider=providers.require(flow.provider());RemoteCredentialMaterial material=provider.exchange(storage,authProfile(flow.provider()),flow.redirectUri(),code.trim(),flow.verifier(),flow.scopes());
            if(material.provider()!=flow.provider())throw new RemoteAuthException(RemoteAuthException.Reason.STATE_MISMATCH,"OAuth provider returned credentials for an unexpected provider");
            vault.store(flow.connectionId(),material);RemoteAuthConnectionDescriptor connection=descriptor(flow.connectionId(),material);outcomes.success(flow.state(),flow.profileId(),connection,flow.expiresAt());return connection;
        } catch(RemoteAuthException exception){outcomes.failure(flow.state(),flow.profileId(),exception,flow.expiresAt());throw exception;}
        catch(RuntimeException exception){RemoteAuthException safe=new RemoteAuthException(RemoteAuthException.Reason.TOKEN_EXCHANGE_FAILED,"External authorization could not be completed",exception);outcomes.failure(flow.state(),flow.profileId(),safe,flow.expiresAt());throw safe;}
    }

    /** Descripción: Ejecuta la operación «credential» correspondiente a la responsabilidad de RemoteAuthService. Entradas: profileId: identificador que delimita el recurso objetivo; storage: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public RemoteCredentialMaterial credential(String profileId,RemoteStorageProperties.Profile storage){
        requireEnabled();String connectionId=connectionId(profileId,storage);RemoteCredentialMaterial current=vault.load(connectionId).orElseThrow(()->new RemoteAuthException(RemoteAuthException.Reason.CONNECTION_NOT_FOUND,"External connection is not authorized: "+connectionId));
        if(current.provider()!=storage.getProvider())throw new RemoteAuthException(RemoteAuthException.Reason.STATE_MISMATCH,"Stored external connection belongs to a different provider");
        if(!needsRefresh(current))return current;
        ReentrantLock lock=refreshLocks.lockFor(connectionId);boolean acquired;
        try{acquired=lock.tryLock(authProperties.getRefreshLockWait().toMillis(),TimeUnit.MILLISECONDS);}
        catch(InterruptedException exception){Thread.currentThread().interrupt();throw new RemoteAuthException(RemoteAuthException.Reason.REMOTE_ERROR,"External credential refresh wait was interrupted",exception);}
        if(!acquired){
            current=validatedCredential(connectionId,storage.getProvider());
            if(!needsRefresh(current)||current.expiresAt()!=null&&current.expiresAt().isAfter(Instant.now()))return current;
            throw new RemoteAuthException(RemoteAuthException.Reason.REMOTE_ERROR,"External credential refresh is already in progress; retry shortly");
        }
        try {
            current=validatedCredential(connectionId,storage.getProvider());
            if(!needsRefresh(current))return current;
            if(!current.refreshable()){
                if(current.expiresAt()!=null&&current.expiresAt().isAfter(Instant.now()))return current;
                throw new RemoteAuthException(RemoteAuthException.Reason.REFRESH_REQUIRED,"External connection expired and must be authorized again");
            }
            RemoteCredentialMaterial refreshed=providers.require(storage.getProvider()).refresh(storage,authProfile(storage.getProvider()),current);
            if(refreshed==null||refreshed.provider()!=storage.getProvider())throw new RemoteAuthException(RemoteAuthException.Reason.STATE_MISMATCH,"OAuth provider returned refreshed credentials for an unexpected provider");
            vault.store(connectionId,refreshed);return refreshed;
        } catch(RemoteAuthException exception){
            if(current.expiresAt()!=null&&current.expiresAt().isAfter(Instant.now()))return current;
            throw exception.getReason()==RemoteAuthException.Reason.REFRESH_REQUIRED?exception:new RemoteAuthException(RemoteAuthException.Reason.REFRESH_REQUIRED,"External connection could not be refreshed and must be authorized again",exception);
        } finally {lock.unlock();}
    }

    /** Descripción: Carga una credencial y valida que siga perteneciendo al proveedor esperado antes de reutilizarla durante un refresh concurrente. Entradas: connectionId y expectedProvider. Salida: credencial validada o error estable. */ private RemoteCredentialMaterial validatedCredential(String connectionId,RemoteStorageProviderType expectedProvider){RemoteCredentialMaterial value=vault.load(connectionId).orElseThrow(()->new RemoteAuthException(RemoteAuthException.Reason.CONNECTION_NOT_FOUND,"External connection is not authorized: "+connectionId));if(value.provider()!=expectedProvider)throw new RemoteAuthException(RemoteAuthException.Reason.STATE_MISMATCH,"Stored external connection belongs to a different provider");return value;}

    /** Descripción: Ejecuta la operación «connected» correspondiente a la responsabilidad de RemoteAuthService. Entradas: profileId: identificador que delimita el recurso objetivo; storage: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean connected(String profileId,RemoteStorageProperties.Profile storage){try{return vault.load(connectionId(profileId,storage)).filter(value->value.provider()==storage.getProvider()).isPresent();}catch(RuntimeException exception){return false;}}

    /** Descripción: Ejecuta la operación «disconnect» correspondiente a la responsabilidad de RemoteAuthService. Entradas: profileId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void disconnect(String profileId){
        requireEnabled();RemoteStorageProperties.Profile storage=storageProfile(profileId);String connectionId=connectionId(profileId,storage);RemoteCredentialMaterial current=vault.load(connectionId).orElse(null);
        try {if(current!=null)providers.require(storage.getProvider()).revoke(storage,authProfile(storage.getProvider()),current);}
        finally {vault.delete(connectionId);}
    }

    /** Descripción: Ejecuta la operación «profileDescriptor» correspondiente a la responsabilidad de RemoteAuthService. Entradas: profileId: identificador que delimita el recurso objetivo; storage: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public RemoteAuthProfileDescriptor profileDescriptor(String profileId,RemoteStorageProperties.Profile storage){
        String connectionId=connectionId(profileId,storage);boolean vaultReady=vault.ready();RemoteCredentialMaterial material=vaultReady?vault.load(connectionId).orElse(null):null;RemoteAuthProperties.Provider auth=storage.getProvider()==null?null:authProperties.provider(storage.getProvider());boolean oauthConfigured=oauthConfigured(storage.getProvider(),auth);
        return new RemoteAuthProfileDescriptor(profileId,storage.getDisplayName().isBlank()?profileId:storage.getDisplayName(),storage.getProvider(),connectionId,oauthConfigured,material!=null&&material.provider()==storage.getProvider(),material==null?"":material.accountLabel(),material==null?List.of():material.scopes(),material==null?null:material.expiresAt(),vault.persistent(),vaultReady,vault.protectionMode());
    }

    /** Descripción: Ejecuta la operación «pending» correspondiente a la responsabilidad de RemoteAuthService. Entradas: flow: dato de entrada necesario para la operación; seconds: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteAuthPollResponse pending(PendingRemoteAuthFlow flow,int seconds){return new RemoteAuthPollResponse(flow.state(),flow.profileId(),false,true,Math.max(1,seconds),null);}
    /** Descripción: Ejecuta la operación «secondsUntil» correspondiente a la responsabilidad de RemoteAuthService. Entradas: now: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int secondsUntil(Instant now,Instant target){return Math.max(1,(int)Math.ceil(Duration.between(now,target).toMillis()/1000.0));}
    /** Descripción: Ejecuta la operación «earliest» correspondiente a la responsabilidad de RemoteAuthService. Entradas: first: dato de entrada necesario para la operación; second: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Instant earliest(Instant first,Instant second){return first==null||second.isBefore(first)?second:first;}
    /** Descripción: Ejecuta la operación «oauthConfigured» correspondiente a la responsabilidad de RemoteAuthService. Entradas: type: dato de entrada necesario para la operación; auth: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean oauthConfigured(RemoteStorageProviderType type,RemoteAuthProperties.Provider auth){return authProperties.oauthReady(type,auth);}
    /** Descripción: Ejecuta la operación «descriptor» correspondiente a la responsabilidad de RemoteAuthService. Entradas: id: identificador que delimita el recurso objetivo; material: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteAuthConnectionDescriptor descriptor(String id,RemoteCredentialMaterial material){return new RemoteAuthConnectionDescriptor(id,material.provider(),material.accountId(),material.accountLabel(),material.scopes(),material.expiresAt(),material.refreshable(),vault.persistent(),vault.protectionMode());}
    /** Descripción: Ejecuta la operación «needsRefresh» correspondiente a la responsabilidad de RemoteAuthService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean needsRefresh(RemoteCredentialMaterial value){return value.expiresAt()!=null&&!value.expiresAt().isAfter(Instant.now().plus(authProperties.getTokenRefreshSkew()));}
    /** Descripción: Ejecuta la operación «storageProfile» correspondiente a la responsabilidad de RemoteAuthService. Entradas: profileId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageProperties.Profile storageProfile(String profileId){RemoteStorageProperties.Profile profile=storageProperties.getProfiles().get(profileId);if(profile==null||profile.getProvider()==null)throw invalid("Remote storage profile was not found or has no provider: "+profileId);if(!profile.isEnabled())throw invalid("Remote storage profile is disabled: "+profileId);return profile;}
    /** Descripción: Ejecuta la operación «authProfile» correspondiente a la responsabilidad de RemoteAuthService. Entradas: type: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteAuthProperties.Provider authProfile(RemoteStorageProviderType type){RemoteAuthProperties.Provider profile=authProperties.provider(type);if(profile==null||!profile.isEnabled())throw new RemoteAuthException(RemoteAuthException.Reason.PROVIDER_NOT_CONFIGURED,"External authorization is disabled for provider: "+type);return profile;}
    /** Descripción: Ejecuta la operación «callbackUri» correspondiente a la responsabilidad de RemoteAuthService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private URI callbackUri(){String base=authProperties.getCallbackBaseUrl();if(base.isBlank())throw invalid("OAuth callback base URL is required");while(base.endsWith("/"))base=base.substring(0,base.length()-1);return RemoteNetworkPolicy.requireSecure(URI.create(base+"/api/remote-auth/callback"),authProperties.isAllowLoopbackHttp());}
    /** Descripción: Ejecuta la operación «connectionId» correspondiente a la responsabilidad de RemoteAuthService. Entradas: profileId: identificador que delimita el recurso objetivo; profile: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String connectionId(String profileId,RemoteStorageProperties.Profile profile){if(profile!=null&&!profile.getConnectionId().isBlank())return profile.getConnectionId();String safe=("oauth-"+profileId).replaceAll("[^A-Za-z0-9._-]","-");return safe.length()>80?safe.substring(0,80):safe;}
    /** Descripción: Valida las condiciones necesarias para «requireEnabled» y rechaza entradas no válidas. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireEnabled(){if(!authProperties.isEnabled())throw new RemoteAuthException(RemoteAuthException.Reason.DISABLED,"External authorization is disabled");}
    /** Descripción: Ejecuta la operación «invalid» correspondiente a la responsabilidad de RemoteAuthService. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteAuthException invalid(String message){return new RemoteAuthException(RemoteAuthException.Reason.INVALID_REQUEST,message);}
}
