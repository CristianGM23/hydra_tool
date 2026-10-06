package com.example.editor.metamodel.importing.updatesite;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import com.example.editor.remote.git.GitRemoteUrlResolver;
import com.example.editor.remote.git.NativeGitRunner;
import com.example.editor.remote.storage.RemoteStorageCredentialResolver;
import com.example.editor.remote.storage.RemoteStorageCredentials;
import com.example.editor.remote.storage.RemoteStorageProperties;

/**
 * Imports a complete bounded repository snapshot through native Git without shell interpolation.
 * Browser tree URLs are normalized, HTTPS/SSRF/allow-list checks are reused, and repository
 * bytes remain staged on disk throughout capture and analysis.
 */
@Service
public class UpdateSiteRepositorySourceService {
    private static final Pattern REF=Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/-]{0,199}");
    private final UpdateSiteRemoteRepositoryService remoteBoundary;
    private final UpdateSiteImportProperties properties;
    private final UpdateSiteImportApplicationService imports;
    private final UpdateSiteRepositorySourceStore snapshots;
    private final NativeGitRunner git;
    private final RemoteStorageProperties storage;
    private final RemoteStorageCredentialResolver credentials;
    private final GitRemoteUrlResolver urls;
    private final UpdateSiteGitRepositoryLocator locator;
    private final UpdateSiteRepositoryRootDetector roots;

    /** Descripción: Inicializa una instancia de UpdateSiteRepositorySourceService con sus dependencias y datos de entrada. Entradas: remoteBoundary: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento; imports: dato de entrada necesario para la operación; snapshots: dato de entrada necesario para la operación; git: dato de entrada necesario para la operación; storage: dato de entrada necesario para la operación; credentials: dato de entrada necesario para la operación; urls: dato de entrada necesario para la operación; locator: dato de entrada necesario para la operación; roots: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteRepositorySourceService(UpdateSiteRemoteRepositoryService remoteBoundary,UpdateSiteImportProperties properties,
            UpdateSiteImportApplicationService imports,UpdateSiteRepositorySourceStore snapshots,NativeGitRunner git,
            RemoteStorageProperties storage,RemoteStorageCredentialResolver credentials,GitRemoteUrlResolver urls,
            UpdateSiteGitRepositoryLocator locator,UpdateSiteRepositoryRootDetector roots){
        this.remoteBoundary=remoteBoundary;this.properties=properties;this.imports=imports;this.snapshots=snapshots;this.git=git;
        this.storage=storage;this.credentials=credentials;this.urls=urls;this.locator=locator;this.roots=roots;
    }

    /** Descripción: Ejecuta la operación «analyze» correspondiente a la responsabilidad de Capture. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteRepositorySourceAnalysis analyze(UpdateSiteRepositorySourceRequest request){
        Capture capture=capture(request);
        try{
            String snapshotId=snapshots.save(capture.inputs());
            List<UpdateSiteInputFile> persisted=snapshots.load(snapshotId);
            UpdateSiteImportAnalysis analysis=imports.analyze(persisted,request.options());
            UpdateSiteRepositorySourceSummary summary=new UpdateSiteRepositorySourceSummary(snapshotId,capture.cloneUrl(),capture.provider(),
                    capture.reference(),capture.subdirectory(),persisted.size(),capture.totalBytes(),capture.skipped(),capture.diagnostics(),capture.detectedRoots());
            return new UpdateSiteRepositorySourceAnalysis(analysis,summary);
        } /** Descripción: Elimina o libera el recurso gestionado por «deleteTree» de forma controlada. Entradas: workspace: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ finally { deleteTree(capture.workspace()); }
    }

    /** Descripción: Ejecuta la operación «analyzeSnapshot» correspondiente a la responsabilidad de Capture. Entradas: snapshotId: identificador que delimita el recurso objetivo; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteImportAnalysis analyzeSnapshot(String snapshotId,UpdateSiteImportOptions options){return imports.analyze(snapshots.load(snapshotId),options==null?UpdateSiteImportOptions.defaults():options);}
    /** Descripción: Ejecuta la operación «importSnapshot» correspondiente a la responsabilidad de Capture. Entradas: snapshotId: identificador que delimita el recurso objetivo; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteImportResult importSnapshot(String snapshotId,UpdateSiteImportOptions options){return imports.importAndSave(snapshots.load(snapshotId),options==null?UpdateSiteImportOptions.defaults():options);}
    /** Descripción: Ejecuta la operación «reconcileSnapshot» correspondiente a la responsabilidad de Capture. Entradas: snapshotId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteReconciliationResult reconcileSnapshot(String snapshotId,UpdateSiteReconciliationRequest request){return imports.reconcile(snapshots.load(snapshotId),request);}
    /** Descripción: Ejecuta la operación «inputs» correspondiente a la responsabilidad de Capture. Entradas: snapshotId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public List<UpdateSiteInputFile> inputs(String snapshotId){return snapshots.load(snapshotId);}

    /** Descripción: Ejecuta la operación «capture» correspondiente a la responsabilidad de Capture. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Capture capture(UpdateSiteRepositorySourceRequest request){
        if(request==null||request.repositoryUrl().isBlank())throw new IllegalArgumentException("Repository URL is required");
        UpdateSiteGitRepositoryLocator.LocatedRepository located=locator.locate(request.repositoryUrl(),request.reference(),request.subdirectory());
        URI uri=remoteBoundary.validate(stripTrailingGit(located.cloneUrl()));
        String reference=validateReference(located.reference());Path parent=null;
        try{
            parent=Files.createTempDirectory("hydra-update-site-repository-");Path checkout=parent.resolve("checkout");
            List<String> command=new ArrayList<>(List.of("clone","--no-tags","--depth","1","--single-branch"));
            if(!reference.isBlank()){command.add("--branch");command.add(reference);}command.add(located.cloneUrl());command.add(checkout.toString());
            if(request.profileId().isBlank())git.requireSuccess(git.run(parent,command),"Repository clone");else authenticatedClone(parent,command,request.profileId(),uri);
            List<UpdateSiteDetectedRoot> detected=roots.detect(checkout);
            Collection collection=collect(checkout,located.subdirectory());
            List<String> diagnostics=new ArrayList<>(collection.diagnostics());
            if(detected.size()>1)diagnostics.add(detected.size()+" update-site root candidate(s) were detected; the complete repository snapshot remains available for cross-root recovery.");
            if(located.subdirectory().isBlank()&&detected.stream().anyMatch(root->!root.path().isBlank()&&root.recommended()))diagnostics.add("A nested update-site root was detected. You can re-run analysis with that subdirectory to reduce noise while preserving the original snapshot.");
            return new Capture(uri,located.cloneUrl(),located.provider(),reference,located.subdirectory(),collection.inputs(),collection.totalBytes(),collection.skipped(),List.copyOf(diagnostics),detected,parent);
        }catch(IOException exception){if(parent!=null)deleteTree(parent);throw new IllegalArgumentException("Could not create the repository import workspace",exception);}
        catch(RuntimeException exception){if(parent!=null)deleteTree(parent);throw exception;}
    }

    /** Descripción: Ejecuta la operación «authenticatedClone» correspondiente a la responsabilidad de Capture. Entradas: parent: dato de entrada necesario para la operación; command: dato de entrada necesario para la operación; profileId: identificador que delimita el recurso objetivo; requested: solicitud con los datos necesarios para ejecutar la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void authenticatedClone(Path parent,List<String> command,String profileId,URI requested){
        RemoteStorageProperties.Profile profile=storage.getProfiles().get(profileId);
        if(profile==null||!profile.isEnabled())throw new IllegalArgumentException("Repository profile is missing or disabled: "+profileId);
        URI configured=URI.create(stripTrailingGit(urls.resolve(profile))).normalize();
        if(!sameRepository(requested,configured))throw new IllegalArgumentException("The selected credential profile does not belong to the requested repository URL");
        RemoteStorageCredentials secret=credentials.resolve(profileId,profile);git.requireSuccess(git.runAuthenticated(parent,command,profile,secret),"Authenticated repository clone");
    }

    /** Descripción: Ejecuta la operación «collect» correspondiente a la responsabilidad de Capture. Entradas: checkout: dato de entrada necesario para la operación; requestedSubdirectory: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Collection collect(Path checkout,String requestedSubdirectory)throws IOException{
        Path source=resolveSubdirectory(checkout,requestedSubdirectory);List<UpdateSiteInputFile> inputs=new ArrayList<>();List<String> skipped=new ArrayList<>();List<String> diagnostics=new ArrayList<>();AtomicInteger files=new AtomicInteger();long[] bytes={0L};
        Files.walkFileTree(source,new SimpleFileVisitor<>(){
            /** Descripción: Ejecuta la operación «preVisitDirectory» correspondiente a la responsabilidad de Capture. Entradas: dir: dato de entrada necesario para la operación; attrs: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public FileVisitResult preVisitDirectory(Path dir,BasicFileAttributes attrs){if(!dir.equals(source)&&dir.getFileName()!=null&&".git".equals(dir.getFileName().toString()))return FileVisitResult.SKIP_SUBTREE;if(Files.isSymbolicLink(dir))return FileVisitResult.SKIP_SUBTREE;return FileVisitResult.CONTINUE;}
            /** Descripción: Ejecuta la operación «visitFile» correspondiente a la responsabilidad de Capture. Entradas: file: archivo o contenido que debe procesarse; attrs: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attrs){
                if(!attrs.isRegularFile()||Files.isSymbolicLink(file))return FileVisitResult.CONTINUE;
                int count=files.incrementAndGet();if(count>properties.getMaxEntries())throw new IllegalArgumentException("Repository contains more files than the configured update-site import limit");
                long size=attrs.size();String relative=source.relativize(file).toString().replace('\\','/');
                if(size>properties.getMaxEntryBytes()){skipped.add(relative);return FileVisitResult.CONTINUE;}
                try{bytes[0]=Math.addExact(bytes[0],size);}catch(ArithmeticException overflow){throw new IllegalArgumentException("Repository exceeds the configured update-site import byte budget",overflow);}
                if(bytes[0]>properties.getMaxUncompressedBytes())throw new IllegalArgumentException("Repository exceeds the configured update-site import byte budget");
                inputs.add(UpdateSiteInputFile.staged(relative,file,size,false));return FileVisitResult.CONTINUE;
            }
        });
        inputs.sort(Comparator.comparing(UpdateSiteInputFile::path));if(inputs.isEmpty())throw new IllegalArgumentException("Repository contains no importable files in the selected path");
        if(!skipped.isEmpty())diagnostics.add(skipped.size()+" oversized repository file(s) were skipped instead of exhausting the import budget.");
        diagnostics.add("Repository content was cloned into an isolated temporary workspace, staged without bulk heap copies and analyzed without executing repository code.");
        return new Collection(List.copyOf(inputs),bytes[0],List.copyOf(skipped),List.copyOf(diagnostics));
    }

    /** Descripción: Obtiene la información correspondiente a «resolveSubdirectory» dentro de Capture. Entradas: checkout: dato de entrada necesario para la operación; requested: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Path resolveSubdirectory(Path checkout,String requested){if(requested==null||requested.isBlank())return checkout;String normalized=requested.replace('\\','/').trim();if(normalized.startsWith("/")||normalized.contains("../")||normalized.equals(".."))throw new IllegalArgumentException("Repository subdirectory is unsafe");Path result=checkout.resolve(normalized).normalize();if(!result.startsWith(checkout)||!Files.isDirectory(result,LinkOption.NOFOLLOW_LINKS))throw new IllegalArgumentException("Repository subdirectory was not found");return result;}
    /** Descripción: Valida las condiciones necesarias para «validateReference» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String validateReference(String value){if(value==null||value.isBlank())return "";String result=value.trim();if(!REF.matcher(result).matches()||result.contains("..")||result.contains("@{")||result.startsWith("-")||result.endsWith(".lock"))throw new IllegalArgumentException("Repository reference contains unsafe characters");return result;}
    /** Descripción: Ejecuta la operación «stripTrailingGit» correspondiente a la responsabilidad de Capture. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String stripTrailingGit(String value){String trimmed=value==null?"":value.trim();return trimmed.endsWith(".git")?trimmed.substring(0,trimmed.length()-4):trimmed;}
    /** Descripción: Ejecuta la operación «sameRepository» correspondiente a la responsabilidad de Capture. Entradas: left: dato de entrada necesario para la operación; right: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean sameRepository(URI left,URI right){return left.getHost().equalsIgnoreCase(right.getHost())&&normalizePath(left.getPath()).equals(normalizePath(right.getPath()));}
    /** Descripción: Ejecuta la operación «normalizePath» correspondiente a la responsabilidad de Capture. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String normalizePath(String value){String result=value==null?"":value;while(result.endsWith("/"))result=result.substring(0,result.length()-1);if(result.endsWith(".git"))result=result.substring(0,result.length()-4);return result;}
    /** Descripción: Elimina o libera el recurso gestionado por «deleteTree» de forma incremental y de mejor esfuerzo. Entradas: root: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; registra cualquier fallo sin materializar el árbol completo. */ private void deleteTree(Path root){try{if(root==null||!Files.exists(root))return;Files.walkFileTree(root,new SimpleFileVisitor<>(){/** Descripción: Elimina un fichero temporal visitado durante la limpieza. Entradas: file: fichero; attrs: metadatos. Salida: continúa el recorrido. */ @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attrs){try{Files.deleteIfExists(file);}catch(IOException ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/metamodel/importing/updatesite/UpdateSiteRepositorySourceService.java",ignored);}return FileVisitResult.CONTINUE;}/** Descripción: Elimina un directorio temporal tras procesar sus hijos. Entradas: dir: directorio; exc: posible fallo del recorrido. Salida: continúa el recorrido. */ @Override public FileVisitResult postVisitDirectory(Path dir,IOException exc){if(exc!=null)com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/metamodel/importing/updatesite/UpdateSiteRepositorySourceService.java",exc);try{Files.deleteIfExists(dir);}catch(IOException ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/metamodel/importing/updatesite/UpdateSiteRepositorySourceService.java",ignored);}return FileVisitResult.CONTINUE;}});}catch(IOException ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/metamodel/importing/updatesite/UpdateSiteRepositorySourceService.java",ignored);}}
    private record Collection(List<UpdateSiteInputFile> inputs,long totalBytes,List<String> skipped,List<String> diagnostics){}
    private record Capture(URI uri,String cloneUrl,String provider,String reference,String subdirectory,List<UpdateSiteInputFile> inputs,long totalBytes,List<String> skipped,List<String> diagnostics,List<UpdateSiteDetectedRoot> detectedRoots,Path workspace){}
}
