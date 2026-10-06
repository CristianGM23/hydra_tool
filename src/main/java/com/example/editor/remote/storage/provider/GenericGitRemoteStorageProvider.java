package com.example.editor.remote.storage.provider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.springframework.stereotype.Component;
import com.example.editor.security.process.HydraTemporaryWorkspace;
import com.example.editor.remote.git.GitRemoteUrlResolver;
import com.example.editor.remote.git.NativeGitProperties;
import com.example.editor.remote.git.NativeGitRunner;
import com.example.editor.remote.storage.*;

/**
 * Adaptador de artefactos para Git genérico. Usa el ejecutable Git del servidor,
 * nunca una URL con credenciales ni una dependencia Java adicional.
 */
@Component
public class GenericGitRemoteStorageProvider implements RemoteArtifactStorageProvider {
    private final NativeGitRunner git;
    private final NativeGitProperties properties;
    private final GitRemoteUrlResolver urls;
    private final HydraTemporaryWorkspace temporaryWorkspace;
    /** Descripción: Inicializa una instancia de GenericGitRemoteStorageProvider con sus dependencias y datos de entrada. Entradas: git: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento; urls: dato de entrada necesario para la operación; temporaryWorkspace: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public GenericGitRemoteStorageProvider(NativeGitRunner git,NativeGitProperties properties,GitRemoteUrlResolver urls,HydraTemporaryWorkspace temporaryWorkspace){this.git=git;this.properties=properties;this.urls=urls;this.temporaryWorkspace=temporaryWorkspace;}
    /** Descripción: Ejecuta la operación «type» correspondiente a la responsabilidad de GenericGitRemoteStorageProvider. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public RemoteStorageProviderType type(){return RemoteStorageProviderType.GENERIC_GIT;}

    /** Descripción: Ejecuta la operación «testConnection» correspondiente a la responsabilidad de GenericGitRemoteStorageProvider. Entradas: profileId: identificador que delimita el recurso objetivo; profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ @Override public void testConnection(String profileId,RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials) {
        Path checkout=checkout(profile,credentials);
        delete(checkout);
    }

    /** Descripción: Obtiene la información correspondiente a «read» dentro de GenericGitRemoteStorageProvider. Entradas: profileId: identificador que delimita el recurso objetivo; profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; scope: dato de entrada necesario para la operación; maximumBytes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public RemoteArtifact read(String profileId,RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials,String path,RemoteArtifactScope scope,long maximumBytes) {
        Path checkout=checkout(profile,credentials);
        try {
            Path target=resolve(checkout,path);
            if(Files.isSymbolicLink(target)||!Files.isRegularFile(target,java.nio.file.LinkOption.NOFOLLOW_LINKS)) throw notFound(path);
            verifyNoSymlinkPath(checkout,target);
            long size=size(target);if(size>maximumBytes)throw tooLarge(maximumBytes);
            byte[] content=com.example.editor.io.BoundedIo.read(target,maximumBytes);
            String version=head(checkout);
            String contentType=probe(target);
            return new RemoteArtifact(new RemoteArtifactDescriptor(profileId,type(),scope,path,version,content.length,contentType,"",false),content);
        } catch(IOException exception){throw new RemoteStorageException(RemoteStorageException.Reason.REMOTE_ERROR,"Generic Git artifact could not be read",exception);}
        /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: checkout: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ finally{delete(checkout);}
    }

    /** Descripción: Actualiza el estado gestionado por GenericGitRemoteStorageProvider mediante la operación «write». Entradas: profileId: identificador que delimita el recurso objetivo; profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación; scope: dato de entrada necesario para la operación; command: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public RemoteArtifactDescriptor write(String profileId,RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials,RemoteArtifactScope scope,RemoteArtifactWriteCommand command) {
        Path checkout=checkout(profile,credentials);
        try {
            Path target=resolve(checkout,command.path());verifyNoSymlinkPath(checkout,target);boolean exists=Files.isRegularFile(target,java.nio.file.LinkOption.NOFOLLOW_LINKS);
            enforceMode(command.mode(),exists,command.path());String current=head(checkout);
            if(!command.expectedVersion().isBlank()&&!command.expectedVersion().equals(current))throw precondition(command.path());
            Files.createDirectories(target.getParent());Files.write(target,command.content());
            git.requireSuccess(git.run(checkout,java.util.List.of("config","user.name","HYDRA")),"Git author configuration");
            git.requireSuccess(git.run(checkout,java.util.List.of("config","user.email","hydra@localhost")),"Git author configuration");
            git.requireSuccess(git.run(checkout,java.util.List.of("add","--",command.path())),"Git add");
            String message=command.commitMessage()==null||command.commitMessage().isBlank()?"HYDRA remote artifact update":command.commitMessage();
            NativeGitRunner.Result commit=git.run(checkout,java.util.List.of("commit","-m",message,"--",command.path()));
            if(!commit.successful()&&!commit.output().toLowerCase(java.util.Locale.ROOT).contains("nothing to commit"))git.requireSuccess(commit,"Git commit");
            String branch=branch(profile);git.requireSuccess(git.runAuthenticated(checkout,java.util.List.of("push","origin","HEAD:refs/heads/"+branch),profile,credentials),"Git push");
            String version=head(checkout);
            return new RemoteArtifactDescriptor(profileId,type(),scope,command.path(),version,command.contentLength(),command.contentType(),"",!exists);
        } catch(IOException exception){throw new RemoteStorageException(RemoteStorageException.Reason.REMOTE_ERROR,"Generic Git artifact could not be written",exception);}
        /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: checkout: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ finally{delete(checkout);}
    }

    /** Descripción: Valida las condiciones necesarias para «checkout» y rechaza entradas no válidas. Entradas: profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Path checkout(RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials){
        Path directory;
        try{directory=temporaryWorkspace.create("git-artifact-");}catch(RuntimeException exception){throw new RemoteStorageException(RemoteStorageException.Reason.REMOTE_ERROR,"Could not create a temporary Git checkout",exception);}
        String remote=urls.resolve(profile);String branch=branch(profile);
        NativeGitRunner.Result first=git.runAuthenticated(directory,java.util.List.of("clone","--depth","1","--single-branch","--branch",branch,remote,"."),profile,credentials);
        if(!first.successful()){
            delete(directory);
            try{directory=temporaryWorkspace.create("git-artifact-");}catch(RuntimeException exception){throw new RemoteStorageException(RemoteStorageException.Reason.REMOTE_ERROR,"Could not recreate a temporary Git checkout",exception);}
            NativeGitRunner.Result fallback=git.runAuthenticated(directory,java.util.List.of("clone","--depth","1",remote,"."),profile,credentials);
            if(!fallback.successful()){delete(directory);git.requireSuccess(fallback,"Generic Git clone");}
        }
        temporaryWorkspace.verify(directory);verifyCheckoutSize(directory);return directory;
    }

    /** Descripción: Valida el checkout mediante recorrido sin seguimiento de enlaces, acotando bytes y evitando que un enlace del repositorio fuerce una lectura fuera del temporal. Entradas: root: raíz del checkout. Salida: no devuelve valor; rechaza el checkout si excede el límite configurado. */ private void verifyCheckoutSize(Path root){
        long maximum=properties.getMaximumTemporaryCheckoutBytes();long[] total={0L};
        try{
            Files.walkFileTree(root,new java.nio.file.SimpleFileVisitor<>(){
                /** Descripción: Acumula el tamaño de los ficheros regulares del checkout temporal sin seguir enlaces y aborta al superar el presupuesto. Entradas: fichero y atributos. Salida: devuelve la decisión de continuar el recorrido. */
                /** Descripción: Ejecuta la operación «visitFile» correspondiente a la responsabilidad de CheckoutLimitException. Entradas: file: archivo o contenido que debe procesarse; attrs: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public java.nio.file.FileVisitResult visitFile(Path file,java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                    if(attrs.isSymbolicLink())return java.nio.file.FileVisitResult.CONTINUE;
                    if(attrs.isRegularFile()){
                        try{total[0]=Math.addExact(total[0],attrs.size());}catch(ArithmeticException exception){throw new CheckoutLimitException();}
                        if(total[0]>maximum)throw new CheckoutLimitException();
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
        }catch(CheckoutLimitException exception){delete(root);throw tooLarge(maximum);}
        catch(IOException exception){delete(root);throw new RemoteStorageException(RemoteStorageException.Reason.REMOTE_ERROR,"Could not inspect the temporary Git checkout",exception);}
    }
    /** Descripción: Obtiene la información correspondiente a «resolve» dentro de GenericGitRemoteStorageProvider. Entradas: root: ruta o ubicación utilizada por la operación; relative: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Path resolve(Path root,String relative){Path target=root.resolve(relative.replace('\\','/')).normalize();if(!target.startsWith(root.normalize())||target.startsWith(root.resolve(".git").normalize()))throw invalid("Git artifact path escapes the checkout or targets .git metadata");return target;}
    /** Descripción: Rechaza enlaces simbólicos en cualquier componente existente de la ruta de un artefacto Git para impedir escapes indirectos del checkout. Entradas: root: raíz del checkout; target: destino resuelto. Salida: no devuelve valor. */ private void verifyNoSymlinkPath(Path root,Path target){Path normalizedRoot=root.toAbsolutePath().normalize();Path normalizedTarget=target.toAbsolutePath().normalize();Path current=normalizedRoot;for(Path segment:normalizedRoot.relativize(normalizedTarget)){current=current.resolve(segment);if(Files.exists(current,java.nio.file.LinkOption.NOFOLLOW_LINKS)&&Files.isSymbolicLink(current))throw invalid("Git artifact path traverses a symbolic link: "+current);}}
    /** Señal interna sin stack costoso para abortar inmediatamente un checkout sobredimensionado. */ private static final class CheckoutLimitException extends IOException { private static final long serialVersionUID=1L; }
    /** Descripción: Ejecuta la operación «head» correspondiente a la responsabilidad de GenericGitRemoteStorageProvider. Entradas: checkout: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String head(Path checkout){NativeGitRunner.Result result=git.run(checkout,java.util.List.of("rev-parse","HEAD"));return result.successful()?result.output().trim():"";}
    /** Descripción: Ejecuta la operación «branch» correspondiente a la responsabilidad de GenericGitRemoteStorageProvider. Entradas: profile: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String branch(RemoteStorageProperties.Profile profile){return profile.getBranch().isBlank()?"main":profile.getBranch();}
    /** Descripción: Ejecuta la operación «size» correspondiente a la responsabilidad de GenericGitRemoteStorageProvider. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private long size(Path path){try{return Files.size(path);}catch(IOException exception){throw new RemoteStorageException(RemoteStorageException.Reason.REMOTE_ERROR,"Could not inspect Git artifact size",exception);}}
    /** Descripción: Ejecuta la operación «probe» correspondiente a la responsabilidad de GenericGitRemoteStorageProvider. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String probe(Path path){try{String value=Files.probeContentType(path);return value==null?"application/octet-stream":value;}catch(IOException ignored){return "application/octet-stream";}}
    /** Descripción: Ejecuta la operación «enforceMode» correspondiente a la responsabilidad de GenericGitRemoteStorageProvider. Entradas: mode: dato de entrada necesario para la operación; exists: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void enforceMode(RemoteWriteMode mode,boolean exists,String path){if(mode==RemoteWriteMode.CREATE_ONLY&&exists)throw conflict(path);if(mode==RemoteWriteMode.UPDATE_ONLY&&!exists)throw notFound(path);}
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: root: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void delete(Path root){temporaryWorkspace.cleanup(root);}
    /** Descripción: Ejecuta la operación «invalid» correspondiente a la responsabilidad de GenericGitRemoteStorageProvider. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageException invalid(String message){return new RemoteStorageException(RemoteStorageException.Reason.INVALID_REQUEST,message);}
    /** Descripción: Ejecuta la operación «notFound» correspondiente a la responsabilidad de GenericGitRemoteStorageProvider. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageException notFound(String path){return new RemoteStorageException(RemoteStorageException.Reason.NOT_FOUND,"Generic Git artifact was not found: "+path);}
    /** Descripción: Ejecuta la operación «conflict» correspondiente a la responsabilidad de GenericGitRemoteStorageProvider. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageException conflict(String path){return new RemoteStorageException(RemoteStorageException.Reason.CONFLICT,"Generic Git artifact could not be written without conflict: "+path);}
    /** Descripción: Ejecuta la operación «precondition» correspondiente a la responsabilidad de GenericGitRemoteStorageProvider. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageException precondition(String path){return new RemoteStorageException(RemoteStorageException.Reason.PRECONDITION_FAILED,"Generic Git repository changed since the artifact was loaded: "+path);}
    /** Descripción: Ejecuta la operación «tooLarge» correspondiente a la responsabilidad de GenericGitRemoteStorageProvider. Entradas: max: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageException tooLarge(long max){return new RemoteStorageException(RemoteStorageException.Reason.FILE_TOO_LARGE,"Generic Git checkout or artifact exceeds the configured limit of "+max+" bytes");}
}
