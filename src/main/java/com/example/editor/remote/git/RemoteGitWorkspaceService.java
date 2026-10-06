package com.example.editor.remote.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import com.example.editor.project.ProjectApplicationService;
import com.example.editor.io.BoundedIo;
import com.example.editor.project.ProjectSession;
import com.example.editor.remote.storage.RemoteStorageCredentialResolver;
import com.example.editor.remote.storage.RemoteStorageCredentials;
import com.example.editor.remote.storage.RemoteStorageException;
import com.example.editor.remote.storage.RemoteStorageProperties;
import com.example.editor.remote.storage.RemoteStorageProviderType;

/**
 * Operaciones Git reales sobre el proyecto local activo. Todos los comandos se
 * construyen en código y se ejecutan sin shell; no existe una entrada de comando
 * libre desde la interfaz.
 */
@Service
public class RemoteGitWorkspaceService {
    private static final Pattern BRANCH=Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/-]{0,199}");
    private static final char FIELD_SEPARATOR='\u001F';
    private static final char RECORD_SEPARATOR='\u001E';
    private static final Pattern TRACK_AHEAD=Pattern.compile("(?:^|[,\\s])ahead\\s+(\\d+)");
    private static final Pattern TRACK_BEHIND=Pattern.compile("(?:^|[,\\s])behind\\s+(\\d+)");
    private final ProjectApplicationService projects;
    private final RemoteStorageProperties storage;
    private final RemoteStorageCredentialResolver credentials;
    private final NativeGitRunner git;
    private final NativeGitProperties gitProperties;
    private final GitRemoteUrlResolver urls;
    private final ReentrantLock operationLock=new ReentrantLock(true);

    /** Descripción: Inicializa una instancia de RemoteGitWorkspaceService con sus dependencias y datos de entrada. Entradas: projects: servicio del proyecto activo; storage: perfiles de almacenamiento remoto; credentials: resolvedor de credenciales; git: ejecutor Git acotado; gitProperties: límites configurables del runtime Git; urls: resolvedor de URLs remotas. Salida: deja la instancia inicializada y lista para su uso. */ public RemoteGitWorkspaceService(ProjectApplicationService projects,RemoteStorageProperties storage,RemoteStorageCredentialResolver credentials,NativeGitRunner git,NativeGitProperties gitProperties,GitRemoteUrlResolver urls){this.projects=projects;this.storage=storage;this.credentials=credentials;this.git=git;this.gitProperties=gitProperties;this.urls=urls;}

    /** Descripción: Procesa la operación «execute» coordinando las responsabilidades de RemoteGitWorkspaceService. Entradas: operation: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public RemoteGitOperationResult execute(RemoteGitOperation operation,RemoteGitOperationRequest request){
        if(operation==null)throw invalid("Git operation is required");
        String profileId=required(request==null?null:request.profileId(),"Repository profile");
        RemoteStorageProperties.Profile profile=profile(profileId);
        Path root=projectRoot();String remoteName=remoteName(profileId);String remoteBranch=branch(request==null?null:request.branch(),profile);
        operationLock.lock();
        try{
            boolean initialized=isRepository(root);
            String operationMessage="";boolean success=true;
            switch(operation){
                case STATUS -> { }
                case INIT -> {if(!initialized){git.requireSuccess(git.run(root,List.of("init")),"Git initialization");git.requireSuccess(git.run(root,List.of("branch","-M",remoteBranch)),"Git default branch setup");initialized=true;}ensureRemote(root,profile,remoteName);operationMessage="Git repository is initialized and connected to the selected profile.";}
                case FETCH -> {requireRepository(initialized);ensureRemote(root,profile,remoteName);NativeGitRunner.Result result=git.runAuthenticated(root,List.of("fetch","--prune",remoteName),profile,credential(profileId,profile));git.requireSuccess(result,"Git fetch");operationMessage=result.output();}
                case PULL -> {requireRepository(initialized);ensureRemote(root,profile,remoteName);RemoteStorageCredentials secret=credential(profileId,profile);git.requireSuccess(git.runAuthenticated(root,List.of("fetch",remoteName,"refs/heads/"+remoteBranch+":refs/remotes/"+remoteName+"/"+remoteBranch),profile,secret),"Git fetch before pull");NativeGitRunner.Result result=pull(root,remoteName,remoteBranch,request==null?null:request.pullStrategy());success=result.successful();operationMessage=result.output();if(!success&&!hasConflicts(root))git.requireSuccess(result,"Git pull");}
                case PUSH -> {requireRepository(initialized);ensureRemote(root,profile,remoteName);NativeGitRunner.Result result=git.runAuthenticated(root,List.of("push","--set-upstream",remoteName,"HEAD:refs/heads/"+remoteBranch),profile,credential(profileId,profile));git.requireSuccess(result,"Git push");operationMessage=result.output();}
                case MERGE -> {requireRepository(initialized);ensureRemote(root,profile,remoteName);String source=validatedBranch(required(request==null?null:request.sourceBranch(),"Source branch"));String mergeTarget=localBranchExists(root,source)?source:remoteName+"/"+source;if(!localBranchExists(root,source)){git.requireSuccess(git.runAuthenticated(root,List.of("fetch",remoteName,"refs/heads/"+source+":refs/remotes/"+remoteName+"/"+source),profile,credential(profileId,profile)),"Git fetch before merge");}NativeGitRunner.Result result=git.run(root,List.of("merge","--no-edit",mergeTarget));success=result.successful();operationMessage=result.output();if(!success&&!hasConflicts(root))git.requireSuccess(result,"Git merge");}
                case ABORT_MERGE -> {requireRepository(initialized);NativeGitRunner.Result result=git.run(root,List.of("merge","--abort"));if(!result.successful()){NativeGitRunner.Result rebase=git.run(root,List.of("rebase","--abort"));operationMessage=rebase.successful()?rebase.output():result.output();success=rebase.successful();}else operationMessage=result.output();}
                case COMMIT_ALL -> {requireRepository(initialized);String message=required(request==null?null:request.commitMessage(),"Commit message");String pending=git.run(root,List.of("status","--porcelain")).output();if(pending.isBlank()){operationMessage="No local changes to commit.";}else{git.requireSuccess(git.run(root,List.of("add","-A")),"Git add");NativeGitRunner.Result result=git.run(root,List.of("commit","-m",message));git.requireSuccess(result,"Git commit");operationMessage=result.output();}}
                case CREATE_BRANCH -> {requireRepository(initialized);String target=validatedBranch(required(request==null?null:request.branch(),"Branch"));NativeGitRunner.Result result=git.run(root,List.of("checkout","-b",target));git.requireSuccess(result,"Git branch creation");remoteBranch=target;operationMessage=result.output();}
                case CHECKOUT -> {requireRepository(initialized);String target=validatedBranch(required(request==null?null:request.branch(),"Branch"));NativeGitRunner.Result result=git.run(root,List.of("checkout",target));git.requireSuccess(result,"Git checkout");remoteBranch=target;operationMessage=result.output();}
            }
            return status(operation,profileId,success,root,initialized,remoteName,remoteBranch,operationMessage);
        } finally {operationLock.unlock();}
    }

    /** Descripción: Ejecuta la operación «repositoryTree» correspondiente a la responsabilidad de RemoteGitWorkspaceService. Entradas: profileId: identificador que delimita el recurso objetivo; preferredBranch: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public RemoteGitRepositoryTree repositoryTree(String profileId,String preferredBranch){
        RemoteStorageProperties.Profile profile=profile(required(profileId,"Repository profile"));
        Path root=projectRoot();
        operationLock.lock();
        try{
            boolean initialized=isRepository(root);
            if(!initialized)return new RemoteGitRepositoryTree(profileId,root.toString(),false,"",false,List.of());
            String current=currentBranch(root);
            String remoteName=remoteName(profileId);
            return new RemoteGitRepositoryTree(profileId,root.toString(),true,current,mergeInProgress(root),List.of(
                    new RemoteGitBranchGroup("local",preferredBranchLabel(profile,current),"Branches available in the local working tree. The selected branch can be checked out, committed, pulled or pushed.",loadBranches(root,true,false,current,remoteName)),
                    new RemoteGitBranchGroup("remote","Remote branches","References fetched from the selected remote profile. Inspect them here before deciding what to merge or checkout locally.",loadBranches(root,false,true,current,remoteName))));
        } finally {operationLock.unlock(); }
    }

    /** Descripción: Ejecuta la operación «conflictView» correspondiente a la responsabilidad de RemoteGitWorkspaceService. Entradas: profileId: identificador que delimita el recurso objetivo; relativePath: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ public RemoteGitConflictFileView conflictView(String profileId,String relativePath){
        profile(required(profileId,"Repository profile"));
        Path root=projectRoot();
        operationLock.lock();
        try{
            requireRepository(isRepository(root));
            String safePath=validatedRelativePath(relativePath);
            return new RemoteGitConflictFileView(profileId,safePath,showBlob(root,":1:"+safePath),showBlob(root,":2:"+safePath),showBlob(root,":3:"+safePath),readWorkingTree(root.resolve(safePath)),mergeInProgress(root));
        } finally {operationLock.unlock();}
    }

    /** Descripción: Obtiene la información correspondiente a «resolveConflict» dentro de RemoteGitWorkspaceService. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public RemoteGitConflictResolutionResult resolveConflict(RemoteGitConflictResolutionRequest request){
        String profileId=required(request==null?null:request.profileId(),"Repository profile");
        profile(profileId);
        Path root=projectRoot();
        operationLock.lock();
        try{
            requireRepository(isRepository(root));
            String safePath=validatedRelativePath(request.relativePath());
            Path target=root.resolve(safePath).normalize();
            String resolvedContent=request.resolvedContent()==null?"":request.resolvedContent();
            int resolvedBytes=resolvedContent.getBytes(StandardCharsets.UTF_8).length;
            if(resolvedBytes>gitProperties.getMaximumOutputBytes()) throw invalid("Resolved conflict content exceeds the configured Git text limit of "+gitProperties.getMaximumOutputBytes()+" bytes");
            try{
                if(target.getParent()!=null) Files.createDirectories(target.getParent());
                Files.writeString(target,resolvedContent,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE);
            } catch(IOException exception){ throw invalid("Could not write the resolved file: "+exception.getMessage()); }
            if(request.stageResolved()) git.requireSuccess(git.run(root,List.of("add","--",safePath)),"Git add resolved file");
            return new RemoteGitConflictResolutionResult(safePath,request.stageResolved(),lines(git.run(root,List.of("diff","--name-only","--diff-filter=U")).output()),changedFiles(git.run(root,List.of("status","--porcelain=v1")).output()),request.stageResolved()?"The resolved file was saved and staged. You can continue the merge once every conflict is resolved.":"The resolved file was saved. Stage it explicitly when you are satisfied with the result.");
        } finally {operationLock.unlock();}
    }

    /** Descripción: Obtiene la información correspondiente a «loadBranches» dentro de RemoteGitWorkspaceService. Entradas: root: ruta o ubicación utilizada por la operación; local: dato de entrada necesario para la operación; remote: dato de entrada necesario para la operación; current: dato de entrada necesario para la operación; remoteName: dato de entrada necesario para la operación. Salida: devuelve una vista acotada de ramas y commits sin ejecutar una consulta adicional de ahead/behind por cada rama. */ private List<RemoteGitBranchNode> loadBranches(Path root,boolean local,boolean remote,String current,String remoteName){
        int limit=gitProperties.getMaximumBranchNodesPerGroup();
        List<String> command=branchListingCommand(limit);
        if(local) command.add("refs/heads");
        if(remote) command.add("refs/remotes/"+remoteName);
        List<RemoteGitBranchNode> branches=branchNodes(root,git.run(root,command),local,remote,current,remoteName);
        if(local&&current!=null&&!current.isBlank()&&branches.stream().noneMatch(item->item.fullName().equals(current))){
            List<String> currentCommand=branchListingCommand(1);currentCommand.add("refs/heads/"+current);
            List<RemoteGitBranchNode> currentNodes=branchNodes(root,git.run(root,currentCommand),true,false,current,remoteName);
            if(!currentNodes.isEmpty()){
                ArrayList<RemoteGitBranchNode> withCurrent=new ArrayList<>(branches);
                if(withCurrent.size()>=limit)withCurrent.remove(withCurrent.size()-1);
                withCurrent.add(0,currentNodes.get(0));
                branches=List.copyOf(withCurrent);
            }
        }
        return branches;
    }

    /** Descripción: Construye el comando acotado que recupera los metadatos de ramas y el estado ahead/behind en una sola consulta Git. Entradas: limit: máximo de ramas a recuperar. Salida: devuelve los argumentos de `for-each-ref` sin ejecutar todavía el proceso. */ private List<String> branchListingCommand(int limit){return new ArrayList<>(List.of("for-each-ref","--count="+limit,"--sort=-committerdate","--format=%(refname)%x1f%(refname:short)%x1f%(upstream:short)%x1f%(upstream:track)%x1f%(objectname:short)%x1f%(committerdate:iso-strict)%x1f%(subject)"));}

    /** Descripción: Convierte la salida acotada de `for-each-ref` en nodos de rama, descartando referencias simbólicas HEAD y cargando solo el historial reciente configurado. Entradas: root: raíz Git; result: salida de Git; local/remote: tipo de grupo; current: rama activa; remoteName: remoto seleccionado. Salida: devuelve nodos inmutables de ramas. */ private List<RemoteGitBranchNode> branchNodes(Path root,NativeGitRunner.Result result,boolean local,boolean remote,String current,String remoteName){
        if(!result.successful()||result.output().isBlank())return List.of();
        List<RemoteGitBranchNode> branches=new ArrayList<>();
        for(String line:lines(result.output())){
            String[] parts=line.split(String.valueOf(FIELD_SEPARATOR),-1);
            if(parts.length<7)continue;
            String shortName=parts[1];
            if(shortName.endsWith("/HEAD"))continue;
            String upstream=parts[2];
            int[] delta=trackingDelta(parts[3]);
            List<RemoteGitCommitNode> commits=commitSlice(root,shortName);
            String displayName=local?shortName:shortName.startsWith(remoteName+"/")?shortName.substring(remoteName.length()+1):shortName;
            branches.add(new RemoteGitBranchNode(displayName,shortName,local,remote,displayName.equals(current),upstream,delta[0],delta[1],parts[4],parts[5],parts[6],commits));
        }
        return List.copyOf(branches);
    }

    /** Descripción: Interpreta de forma determinista el estado `[ahead N, behind M]` producido por Git en locale C. Entradas: value: estado de seguimiento devuelto por `for-each-ref`. Salida: devuelve `[ahead, behind]`, usando cero cuando una parte no está presente o no es válida. */ private int[] trackingDelta(String value){return new int[]{trackingCount(TRACK_AHEAD,value),trackingCount(TRACK_BEHIND,value)};}

    /** Descripción: Extrae un contador no negativo desde el estado de seguimiento de Git. Entradas: pattern: patrón del contador; value: texto devuelto por Git. Salida: devuelve el contador o cero ante ausencia/formato inválido. */ private int trackingCount(Pattern pattern,String value){if(value==null||value.isBlank())return 0;var matcher=pattern.matcher(value);if(!matcher.find())return 0;try{return Integer.parseInt(matcher.group(1));}catch(NumberFormatException ignored){return 0;}}

    /** Descripción: Ejecuta la operación «commitSlice» correspondiente a la responsabilidad de RemoteGitWorkspaceService. Entradas: root: ruta o ubicación utilizada por la operación; ref: dato de entrada necesario para la operación. Salida: devuelve un historial reciente cuyo tamaño está limitado por configuración. */ private List<RemoteGitCommitNode> commitSlice(Path root,String ref){
        NativeGitRunner.Result result=git.run(root,List.of("log","--date=iso-strict","--pretty=format:%H%x1f%h%x1f%P%x1f%cd%x1f%s%x1e","-n",Integer.toString(gitProperties.getMaximumCommitsPerBranch()),ref));
        if(!result.successful()||result.output().isBlank()) return List.of();
        List<RemoteGitCommitNode> commits=new ArrayList<>();
        for(String record:result.output().split(String.valueOf(RECORD_SEPARATOR))){
            String trimmed=record.trim();
            if(trimmed.isEmpty()) continue;
            String[] parts=trimmed.split(String.valueOf(FIELD_SEPARATOR),-1);
            if(parts.length<5) continue;
            List<String> parents=parts[2].isBlank()?List.of():List.of(parts[2].trim().split("\\s+"));
            commits.add(new RemoteGitCommitNode(parts[0],parts[1],parents,parts[3],parts[4]));
        }
        return List.copyOf(commits);
    }

    /** Descripción: Ejecuta la operación «showBlob» correspondiente a la responsabilidad de RemoteGitWorkspaceService. Entradas: root: ruta o ubicación utilizada por la operación; spec: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String showBlob(Path root,String spec){
        NativeGitRunner.Result result=git.run(root,List.of("show",spec));
        return result.successful()?result.output():"";
    }

    /** Descripción: Obtiene el contenido textual del fichero en conflicto usando el mismo presupuesto máximo que las versiones recuperadas mediante Git. Entradas: path: ruta del fichero del working tree. Salida: devuelve el texto cuando cabe en el presupuesto o rechaza la vista si el fichero excede el límite configurado. */ private String readWorkingTree(Path path){
        if(!Files.exists(path))return "";
        try{return BoundedIo.readUtf8(path,gitProperties.getMaximumOutputBytes());}
        catch(IOException exception){throw invalid("Could not read the conflicted file within the configured Git output limit of "+gitProperties.getMaximumOutputBytes()+" bytes: "+exception.getMessage());}
    }

    /** Descripción: Actualiza el estado gestionado por RemoteGitWorkspaceService mediante la operación «mergeInProgress». Entradas: root: ruta o ubicación utilizada por la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean mergeInProgress(Path root){return Files.exists(root.resolve(".git/MERGE_HEAD"))||Files.isDirectory(root.resolve(".git/rebase-merge"))||Files.isDirectory(root.resolve(".git/rebase-apply"));}
    /** Descripción: Ejecuta la operación «pull» correspondiente a la responsabilidad de RemoteGitWorkspaceService. Entradas: root: ruta o ubicación utilizada por la operación; remoteName: dato de entrada necesario para la operación; branch: dato de entrada necesario para la operación; strategy: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private NativeGitRunner.Result pull(Path root,String remoteName,String branch,RemoteGitPullStrategy strategy){RemoteGitPullStrategy mode=strategy==null?RemoteGitPullStrategy.FF_ONLY:strategy;String remoteRef=remoteName+"/"+branch;return switch(mode){case FF_ONLY->git.run(root,List.of("merge","--ff-only",remoteRef));case MERGE->git.run(root,List.of("merge","--no-edit",remoteRef));case REBASE->git.run(root,List.of("rebase",remoteRef));};}
    /** Descripción: Ejecuta la operación «status» correspondiente a la responsabilidad de RemoteGitWorkspaceService. Entradas: operation: dato de entrada necesario para la operación; profileId: identificador que delimita el recurso objetivo; success: dato de entrada necesario para la operación; root: ruta o ubicación utilizada por la operación; initialized: dato de entrada necesario para la operación; remoteName: dato de entrada necesario para la operación; remoteBranch: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteGitOperationResult status(RemoteGitOperation operation,String profileId,boolean success,Path root,boolean initialized,String remoteName,String remoteBranch,String message){if(!initialized)return new RemoteGitOperationResult(operation,profileId,success,false,"",remoteName,remoteBranch,0,0,false,List.of(),List.of(),message,Instant.now());String branch=currentBranch(root);List<String> conflicts=lines(git.run(root,List.of("diff","--name-only","--diff-filter=U")).output());List<String> changed=changedFiles(git.run(root,List.of("status","--porcelain=v1")).output());int[] counts=aheadBehind(root,remoteName,remoteBranch);boolean merging=mergeInProgress(root);return new RemoteGitOperationResult(operation,profileId,success,initialized,branch,remoteName,remoteBranch,counts[0],counts[1],merging,conflicts,changed,message==null?"":message,Instant.now());}
    /** Descripción: Ejecuta la operación «aheadBehind» correspondiente a la responsabilidad de RemoteGitWorkspaceService. Entradas: root: ruta o ubicación utilizada por la operación; remoteName: dato de entrada necesario para la operación; branch: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int[] aheadBehind(Path root,String remoteName,String branch){if(branch==null||branch.isBlank())return new int[]{0,0};NativeGitRunner.Result verify=git.run(root,List.of("show-ref","--verify","--quiet","refs/remotes/"+remoteName+"/"+branch));if(!verify.successful())return new int[]{0,0};NativeGitRunner.Result result=git.run(root,List.of("rev-list","--left-right","--count","HEAD...refs/remotes/"+remoteName+"/"+branch));if(!result.successful())return new int[]{0,0};String[] parts=result.output().trim().split("\\s+");if(parts.length<2)return new int[]{0,0};try{return new int[]{Integer.parseInt(parts[0]),Integer.parseInt(parts[1])};}catch(NumberFormatException ignored){return new int[]{0,0};}}
    /** Descripción: Ejecuta la operación «localBranchExists» correspondiente a la responsabilidad de RemoteGitWorkspaceService. Entradas: root: ruta o ubicación utilizada por la operación; branch: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean localBranchExists(Path root,String branch){return git.run(root,List.of("show-ref","--verify","--quiet","refs/heads/"+branch)).successful();}
    /** Descripción: Evalúa la condición «hasConflicts» sin modificar el estado externo. Entradas: root: ruta o ubicación utilizada por la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean hasConflicts(Path root){return !git.run(root,List.of("diff","--name-only","--diff-filter=U")).output().isBlank();}
    /** Descripción: Valida las condiciones necesarias para «ensureRemote» y rechaza entradas no válidas. Entradas: root: ruta o ubicación utilizada por la operación; profile: archivo o contenido que debe procesarse; remoteName: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void ensureRemote(Path root,RemoteStorageProperties.Profile profile,String remoteName){String url=urls.resolve(profile);NativeGitRunner.Result current=git.run(root,List.of("remote","get-url",remoteName));if(!current.successful())git.requireSuccess(git.run(root,List.of("remote","add",remoteName,url)),"Git remote setup");else if(!current.output().trim().equals(url))git.requireSuccess(git.run(root,List.of("remote","set-url",remoteName,url)),"Git remote update");}
    /** Descripción: Evalúa la condición «isRepository» sin modificar el estado externo. Entradas: root: ruta o ubicación utilizada por la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isRepository(Path root){NativeGitRunner.Result result=git.run(root,List.of("rev-parse","--is-inside-work-tree"));return result.successful()&&"true".equalsIgnoreCase(result.output().trim());}
    /** Descripción: Ejecuta la operación «currentBranch» correspondiente a la responsabilidad de RemoteGitWorkspaceService. Entradas: root: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String currentBranch(Path root){NativeGitRunner.Result result=git.run(root,List.of("symbolic-ref","--short","HEAD"));return result.successful()?result.output().trim():"";}
    /** Descripción: Ejecuta la operación «changedFiles» correspondiente a la responsabilidad de RemoteGitWorkspaceService. Entradas: output: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> changedFiles(String output){List<String> result=new ArrayList<>();for(String line:lines(output)){if(line.length()>3)result.add(line.substring(3).trim());}return List.copyOf(result);}
    /** Descripción: Ejecuta la operación «lines» correspondiente a la responsabilidad de RemoteGitWorkspaceService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> lines(String value){if(value==null||value.isBlank())return List.of();return value.lines().map(String::trim).filter(item->!item.isBlank()).toList();}
    /** Descripción: Ejecuta la operación «projectRoot» correspondiente a la responsabilidad de RemoteGitWorkspaceService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Path projectRoot(){ProjectSession session=projects.session();if(!session.active()||session.directory()==null||session.directory().isBlank())throw invalid("No local project is open");Path root=Path.of(session.directory()).toAbsolutePath().normalize();if(!Files.isDirectory(root))throw invalid("Active project directory does not exist");return root;}
    /** Descripción: Ejecuta la operación «profile» correspondiente a la responsabilidad de RemoteGitWorkspaceService. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageProperties.Profile profile(String id){RemoteStorageProperties.Profile profile=storage.getProfiles().get(id);if(profile==null)throw new RemoteStorageException(RemoteStorageException.Reason.PROFILE_NOT_FOUND,"Remote storage profile was not found: "+id);if(!profile.isEnabled())throw new RemoteStorageException(RemoteStorageException.Reason.DISABLED,"Remote storage profile is disabled: "+id);if(profile.getProvider()!=RemoteStorageProviderType.GITHUB&&profile.getProvider()!=RemoteStorageProviderType.GITLAB&&profile.getProvider()!=RemoteStorageProviderType.GENERIC_GIT)throw invalid("Selected profile does not support Git operations");return profile;}
    /** Descripción: Ejecuta la operación «credential» correspondiente a la responsabilidad de RemoteGitWorkspaceService. Entradas: id: identificador que delimita el recurso objetivo; profile: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageCredentials credential(String id,RemoteStorageProperties.Profile profile){RemoteStorageCredentials value=credentials.resolve(id,profile);if(value==null||!value.configured())throw new RemoteStorageException(RemoteStorageException.Reason.CREDENTIALS_MISSING,"Remote Git credentials are not configured for profile: "+id);return value;}
    /** Descripción: Ejecuta la operación «remoteName» correspondiente a la responsabilidad de RemoteGitWorkspaceService. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private String remoteName(String id){String safe=("hydra-"+id).replaceAll("[^A-Za-z0-9._-]","-");return safe.length()>80?safe.substring(0,80):safe;}
    /** Descripción: Ejecuta la operación «branch» correspondiente a la responsabilidad de RemoteGitWorkspaceService. Entradas: requested: solicitud con los datos necesarios para ejecutar la operación; profile: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String branch(String requested,RemoteStorageProperties.Profile profile){String value=requested==null||requested.isBlank()?profile.getBranch():requested;return validatedBranch(value==null||value.isBlank()?"main":value);}
    /** Descripción: Ejecuta la operación «preferredBranchLabel» correspondiente a la responsabilidad de RemoteGitWorkspaceService. Entradas: profile: archivo o contenido que debe procesarse; current: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String preferredBranchLabel(RemoteStorageProperties.Profile profile,String current){String configured=profile.getBranch()==null||profile.getBranch().isBlank()?"main":profile.getBranch();return current==null||current.isBlank()?"Local branches":"Local branches";}
    /** Descripción: Valida las condiciones necesarias para «validatedBranch» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String validatedBranch(String value){String branch=value==null?"":value.trim();if(!BRANCH.matcher(branch).matches()||branch.contains("..")||branch.contains("@{")||branch.endsWith("/")||branch.endsWith(".")||branch.endsWith(".lock")||branch.startsWith("-")||branch.contains("//"))throw invalid("Git branch contains unsupported or unsafe characters");return branch;}
    /** Descripción: Valida las condiciones necesarias para «validatedRelativePath» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String validatedRelativePath(String value){String path=required(value,"Conflicted file").replace('\\','/');if(path.startsWith("/")||path.contains("../")||path.equals("..")||path.contains("://"))throw invalid("Conflicted file path contains unsupported or unsafe characters");Path normalized=Path.of(path).normalize();if(normalized.isAbsolute()||normalized.startsWith(".."))throw invalid("Conflicted file path escapes the active project");return normalized.toString().replace('\\','/');}
    /** Descripción: Valida las condiciones necesarias para «required» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String required(String value,String field){if(value==null||value.isBlank())throw invalid(field+" is required");return value.trim();}
    /** Descripción: Valida las condiciones necesarias para «requireRepository» y rechaza entradas no válidas. Entradas: initialized: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireRepository(boolean initialized){if(!initialized)throw invalid("Active project is not a Git repository. Initialize it first.");}
    /** Descripción: Ejecuta la operación «invalid» correspondiente a la responsabilidad de RemoteGitWorkspaceService. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageException invalid(String message){return new RemoteStorageException(RemoteStorageException.Reason.INVALID_REQUEST,message);}
}
