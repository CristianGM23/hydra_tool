package com.example.editor.remote.storage;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Mantiene los perfiles de repositorios editables sin exponer ni persistir
 * credenciales. Los perfiles declarados en application.properties actúan como
 * base y el fichero gestionado por HYDRA conserva únicamente overrides seguros.
 */
@Service
public class RemoteStorageProfileManager {
    private static final Pattern PROFILE_ID=Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,79}");
    private final RemoteStorageProperties properties;
    private final ObjectMapper mapper;
    private final Object lock=new Object();
    private Map<String,RemoteStorageProperties.Profile> baseProfiles=Map.of();
    private ManagedState managed=new ManagedState(Map.of(),Set.of());

    /** Descripción: Inicializa una instancia de RemoteStorageProfileManager con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public RemoteStorageProfileManager(RemoteStorageProperties properties,ObjectMapper mapper){this.properties=properties;this.mapper=mapper;}

    /** Carga los overrides persistidos antes de que la interfaz atienda peticiones. */
    @PostConstruct
    /** Descripción: Crea o prepara el resultado de «initialize» dentro de ManagedProfile. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void initialize(){
        synchronized(lock){
            baseProfiles=copyProfiles(properties.getProfiles());
            managed=loadState();
            rebuildEffectiveProfiles();
        }
    }

    /** Devuelve configuraciones seguras y editables; nunca incluye token ni secreto OAuth. */
    /** Descripción: Ejecuta la operación «configurations» correspondiente a la responsabilidad de ManagedProfile. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<RemoteStorageProfileConfiguration> configurations(){
        synchronized(lock){
            List<RemoteStorageProfileConfiguration> result=new ArrayList<>();
            properties.getProfiles().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                    result.add(configuration(entry.getKey(),entry.getValue(),managed.profiles().containsKey(entry.getKey()))));
            return List.copyOf(result);
        }
    }

    /** Crea o sustituye un override seguro y lo activa inmediatamente para todos los exportadores. */
    /** Descripción: Ejecuta la operación «upsert» correspondiente a la responsabilidad de ManagedProfile. Entradas: profileId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public RemoteStorageProfileConfiguration upsert(String profileId,RemoteStorageProfileUpdateRequest request){
        synchronized(lock){
            String id=validatedId(profileId);
            if(request==null||request.provider()==null) throw invalid("Remote storage provider is required");
            if(!managed.profiles().containsKey(id)&&!baseProfiles.containsKey(id)&&effectiveProfileCount()>=properties.getMaximumManagedProfiles())
                throw new RemoteStorageException(RemoteStorageException.Reason.CAPACITY_EXCEEDED,"Maximum number of remote storage profiles reached");
            ManagedProfile next=validatedProfile(id,request);
            Map<String,ManagedProfile> profiles=new LinkedHashMap<>(managed.profiles());
            profiles.put(id,next);
            Set<String> deleted=new LinkedHashSet<>(managed.deletedProfileIds());
            deleted.remove(id);
            ManagedState previous=managed;
            managed=new ManagedState(Map.copyOf(profiles),Set.copyOf(deleted));
            try { persistState(); }
            catch(RuntimeException exception){ managed=previous; throw exception; }
            rebuildEffectiveProfiles();
            return configuration(id,properties.getProfiles().get(id),true);
        }
    }

    /** Elimina perfiles creados en HYDRA o aplica un tombstone sobre perfiles de configuración base. */
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: profileId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void delete(String profileId){
        synchronized(lock){
            String id=validatedId(profileId);
            if(!properties.getProfiles().containsKey(id)&&!managed.profiles().containsKey(id))
                throw new RemoteStorageException(RemoteStorageException.Reason.PROFILE_NOT_FOUND,"Remote storage profile was not found: "+id);
            Map<String,ManagedProfile> profiles=new LinkedHashMap<>(managed.profiles());
            profiles.remove(id);
            Set<String> deleted=new LinkedHashSet<>(managed.deletedProfileIds());
            if(baseProfiles.containsKey(id)) deleted.add(id); else deleted.remove(id);
            ManagedState previous=managed;
            managed=new ManagedState(Map.copyOf(profiles),Set.copyOf(deleted));
            try { persistState(); }
            catch(RuntimeException exception){ managed=previous; throw exception; }
            rebuildEffectiveProfiles();
        }
    }

    /** Descripción: Obtiene la información correspondiente a «loadState» dentro de ManagedProfile. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private ManagedState loadState(){
        Path profileRegistryFile=properties.getProfileRegistryFile();
        Path file=profileRegistryFile;
        if(file==null||!Files.isRegularFile(file)) return new ManagedState(Map.of(),Set.of());
        try {
            if(Files.size(file)>properties.getMaximumProfileRegistryBytes()) throw invalid("Remote storage profile registry exceeds its configured size limit");
            ManagedState state=mapper.readValue(com.example.editor.io.BoundedIo.readInternal(file),ManagedState.class);
            if(state==null) return new ManagedState(Map.of(),Set.of());
            Map<String,ManagedProfile> accepted=new LinkedHashMap<>();
            if(state.profiles()!=null) {
                if(state.profiles().size()>properties.getMaximumManagedProfiles()) throw invalid("Managed remote storage profile registry exceeds the configured profile count");
                for(Map.Entry<String,ManagedProfile> entry:state.profiles().entrySet()) {
                    String id=validatedId(entry.getKey());
                    ManagedProfile profile=entry.getValue();
                    if(profile==null) throw invalid("Managed remote storage profile is null: "+id);
                    accepted.put(id,validatedProfile(id,toUpdateRequest(profile)));
                }
            }
            Set<String> deleted=new LinkedHashSet<>();
            if(state.deletedProfileIds()!=null) for(String id:state.deletedProfileIds()) deleted.add(validatedId(id));
            return new ManagedState(Map.copyOf(accepted),Set.copyOf(deleted));
        } catch(IOException exception){
            throw new RemoteStorageException(RemoteStorageException.Reason.REMOTE_ERROR,"Could not load the managed remote storage profile registry",exception);
        }
    }

    /** Descripción: Ejecuta la operación «persistState» correspondiente a la responsabilidad de ManagedProfile. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void persistState(){
        Path profileRegistryFile=properties.getProfileRegistryFile();
        Path file=profileRegistryFile;
        if(file==null) throw invalid("Remote storage profile registry file is not configured");
        try {
            Path parent=file.toAbsolutePath().normalize().getParent();
            if(parent!=null) Files.createDirectories(parent);
            byte[] content=mapper.writeValueAsBytes(managed);
            if(content.length>properties.getMaximumProfileRegistryBytes()) throw invalid("Remote storage profile registry exceeds its configured size limit");
            Path temporary=file.resolveSibling(file.getFileName()+".tmp");
            Files.write(temporary,content);
            try { Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(java.nio.file.AtomicMoveNotSupportedException ignored){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
        } catch(IOException exception){
            throw new RemoteStorageException(RemoteStorageException.Reason.REMOTE_ERROR,"Could not persist the managed remote storage profile registry",exception);
        }
    }

    /** Descripción: Ejecuta la operación «rebuildEffectiveProfiles» correspondiente a la responsabilidad de ManagedProfile. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void rebuildEffectiveProfiles(){
        Map<String,RemoteStorageProperties.Profile> effective=copyProfiles(baseProfiles);
        managed.deletedProfileIds().forEach(effective::remove);
        managed.profiles().forEach((id,value)->effective.put(id,toRuntimeProfile(id,value,baseProfiles.get(id))));
        properties.setProfiles(effective);
    }

    /** Descripción: Valida las condiciones necesarias para «validatedProfile» y rechaza entradas no válidas. Entradas: id: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ManagedProfile validatedProfile(String id,RemoteStorageProfileUpdateRequest request){
        String display=clean(request.displayName());
        String baseUrl=clean(request.baseUrl());
        String owner=clean(request.owner());
        String repository=clean(request.repository());
        String projectId=clean(request.projectId());
        String branch=clean(request.branch());
        String driveId=clean(request.driveId());
        String repositoryUrl=clean(request.repositoryUrl());
        String rootItemId=clean(request.rootItemId());
        String gitUsername=clean(request.gitUsername());
        String root=normalizeRoot(request.rootPath());
        String apiVersion=clean(request.apiVersion());
        if(!repositoryUrl.isBlank()) {
            RemoteStorageLocationResolution resolved=RemoteStorageLocationResolver.resolve(repositoryUrl);
            if(request.provider()!=resolved.provider()) throw invalid("Remote URL resolves to "+resolved.provider()+" but the profile is configured as "+request.provider());
            repositoryUrl=resolved.url();
            if(!resolved.owner().isBlank()) owner=resolved.owner();
            if(!resolved.repository().isBlank()) repository=resolved.repository();
            if(!resolved.projectId().isBlank()) projectId=resolved.projectId();
            if(!resolved.branch().isBlank()) branch=resolved.branch();
            if(!resolved.rootPath().isBlank()) root=normalizeRoot(resolved.rootPath());
            if(!resolved.rootItemId().isBlank()) rootItemId=resolved.rootItemId();
        }
        if(request.provider()==RemoteStorageProviderType.GITHUB&&(owner.isBlank()||repository.isBlank())) throw invalid("GitHub repository URL or owner/repository is required");
        if(request.provider()==RemoteStorageProviderType.GITLAB&&projectId.isBlank()) throw invalid("GitLab repository URL or project id/path is required");
        if(request.provider()==RemoteStorageProviderType.GENERIC_GIT&&repositoryUrl.isBlank()) throw invalid("Generic Git repository URL is required");
        if((request.provider()==RemoteStorageProviderType.GITHUB||request.provider()==RemoteStorageProviderType.GITLAB||request.provider()==RemoteStorageProviderType.GENERIC_GIT)&&branch.isBlank()) branch="main";
        if(request.provider()==RemoteStorageProviderType.ONEDRIVE||request.provider()==RemoteStorageProviderType.GOOGLE_DRIVE||request.provider()==RemoteStorageProviderType.DROPBOX) branch="";
        long maximum=Math.max(0L,request.maximumArtifactBytes());
        return new ManagedProfile(display.isBlank()?id:display,request.provider(),request.enabled(),baseUrl,owner,repository,branch,projectId,driveId,request.oneDriveAppFolder(),repositoryUrl,rootItemId,gitUsername,root,apiVersion,maximum);
    }

    /** Descripción: Ejecuta la operación «toRuntimeProfile» correspondiente a la responsabilidad de ManagedProfile. Entradas: id: identificador que delimita el recurso objetivo; value: valor de entrada que debe procesarse; base: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageProperties.Profile toRuntimeProfile(String id,ManagedProfile value,RemoteStorageProperties.Profile base){
        RemoteStorageProperties.Profile profile=new RemoteStorageProperties.Profile();
        profile.setEnabled(value.enabled()); profile.setDisplayName(value.displayName()); profile.setProvider(value.provider()); profile.setBaseUrl(value.baseUrl());
        profile.setOwner(value.owner()); profile.setRepository(value.repository()); profile.setBranch(value.branch()); profile.setProjectId(value.projectId()); profile.setDriveId(value.driveId());
        profile.setOneDriveAppFolder(value.oneDriveAppFolder()); profile.setRepositoryUrl(value.repositoryUrl()); profile.setRootItemId(value.rootItemId()); profile.setGitUsername(value.gitUsername()); profile.setRootPath(value.rootPath()); profile.setApiVersion(value.apiVersion()); profile.setMaximumArtifactBytes(value.maximumArtifactBytes());
        // Preserve current server-side credential references only while the provider stays the same.
        // Reusing a token/connection id after switching provider would cross credential boundaries.
        boolean sameProviderBase=base!=null&&base.getProvider()==value.provider();
        profile.setToken(sameProviderBase?base.getToken():"");
        profile.setConnectionId(sameProviderBase&&!base.getConnectionId().isBlank()?base.getConnectionId():"oauth-"+id);
        return profile;
    }

    /** Descripción: Ejecuta la operación «configuration» correspondiente a la responsabilidad de ManagedProfile. Entradas: id: identificador que delimita el recurso objetivo; profile: archivo o contenido que debe procesarse; managedOverride: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageProfileConfiguration configuration(String id,RemoteStorageProperties.Profile profile,boolean managedOverride){
        return new RemoteStorageProfileConfiguration(id,profile.getDisplayName().isBlank()?id:profile.getDisplayName(),profile.getProvider(),profile.isEnabled(),profile.getBaseUrl(),profile.getOwner(),profile.getRepository(),profile.getBranch(),profile.getProjectId(),profile.getDriveId(),profile.isOneDriveAppFolder(),profile.getRepositoryUrl(),profile.getRootItemId(),profile.getGitUsername(),profile.getRootPath(),profile.getApiVersion(),profile.getMaximumArtifactBytes(),managedOverride);
    }


    /** Descripción: Ejecuta la operación «toUpdateRequest» correspondiente a la responsabilidad de ManagedProfile. Entradas: profile: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageProfileUpdateRequest toUpdateRequest(ManagedProfile profile){
        return new RemoteStorageProfileUpdateRequest(profile.displayName(),profile.provider(),profile.enabled(),profile.baseUrl(),profile.owner(),profile.repository(),profile.branch(),profile.projectId(),profile.driveId(),profile.oneDriveAppFolder(),profile.repositoryUrl(),profile.rootItemId(),profile.gitUsername(),profile.rootPath(),profile.apiVersion(),profile.maximumArtifactBytes());
    }

    /** Descripción: Ejecuta la operación «effectiveProfileCount» correspondiente a la responsabilidad de ManagedProfile. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private int effectiveProfileCount(){return properties.getProfiles().size();}
    /** Descripción: Valida las condiciones necesarias para «validatedId» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String validatedId(String value){String id=clean(value);if(!PROFILE_ID.matcher(id).matches())throw invalid("Remote storage profile id must use letters, numbers, dot, underscore or hyphen and be at most 80 characters");return id;}
    /** Descripción: Ejecuta la operación «normalizeRoot» correspondiente a la responsabilidad de ManagedProfile. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String normalizeRoot(String value){String path=clean(value).replace('\\','/');while(path.startsWith("/"))path=path.substring(1);while(path.endsWith("/"))path=path.substring(0,path.length()-1);for(String part:path.split("/+"))if(part.equals(".")||part.equals(".."))throw invalid("Remote root path cannot contain relative traversal segments");return path;}
    /** Descripción: Ejecuta la operación «clean» correspondiente a la responsabilidad de ManagedProfile. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String clean(String value){return value==null?"":value.trim();}
    /** Descripción: Ejecuta la operación «invalid» correspondiente a la responsabilidad de ManagedProfile. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageException invalid(String message){return new RemoteStorageException(RemoteStorageException.Reason.INVALID_REQUEST,message);}

    /** Descripción: Ejecuta la operación «copyProfiles» correspondiente a la responsabilidad de ManagedProfile. Entradas: source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String,RemoteStorageProperties.Profile> copyProfiles(Map<String,RemoteStorageProperties.Profile> source){Map<String,RemoteStorageProperties.Profile> result=new LinkedHashMap<>();if(source!=null)source.forEach((id,profile)->{if(id!=null&&profile!=null)result.put(id,copyProfile(profile));});return result;}
    /** Descripción: Ejecuta la operación «copyProfile» correspondiente a la responsabilidad de ManagedProfile. Entradas: source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageProperties.Profile copyProfile(RemoteStorageProperties.Profile source){RemoteStorageProperties.Profile p=new RemoteStorageProperties.Profile();p.setEnabled(source.isEnabled());p.setDisplayName(source.getDisplayName());p.setProvider(source.getProvider());p.setBaseUrl(source.getBaseUrl());p.setToken(source.getToken());p.setConnectionId(source.getConnectionId());p.setOwner(source.getOwner());p.setRepository(source.getRepository());p.setBranch(source.getBranch());p.setProjectId(source.getProjectId());p.setDriveId(source.getDriveId());p.setOneDriveAppFolder(source.isOneDriveAppFolder());p.setRepositoryUrl(source.getRepositoryUrl());p.setRootItemId(source.getRootItemId());p.setGitUsername(source.getGitUsername());p.setRootPath(source.getRootPath());p.setApiVersion(source.getApiVersion());p.setMaximumArtifactBytes(source.getMaximumArtifactBytes());return p;}

    public record ManagedState(Map<String,ManagedProfile> profiles,Set<String> deletedProfileIds){public ManagedState{profiles=profiles==null?Map.of():Map.copyOf(profiles);deletedProfileIds=deletedProfileIds==null?Set.of():Set.copyOf(deletedProfileIds);}}
    public record ManagedProfile(String displayName,RemoteStorageProviderType provider,boolean enabled,String baseUrl,String owner,String repository,String branch,String projectId,String driveId,boolean oneDriveAppFolder,String repositoryUrl,String rootItemId,String gitUsername,String rootPath,String apiVersion,long maximumArtifactBytes){ }
}
