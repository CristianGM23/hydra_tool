package com.example.editor.project.storage;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import com.example.editor.project.ArtifactKind;
import com.example.editor.project.LocalProjectProperties;
import com.example.editor.project.ProjectDescriptor;
import com.example.editor.project.ProjectException;
import com.example.editor.project.ProjectLayout;
import com.example.editor.project.ProjectSyncFileChange;
import com.example.editor.project.ProjectSyncRequest;
import com.example.editor.project.ProjectSynchronizationPlan;
import com.example.editor.project.ProjectSynchronizationResult;
import com.example.editor.project.ProjectWorkspaceContribution;
import com.example.editor.project.WorkspaceTransferDirection;
import com.example.editor.project.WorkspaceTransferMode;

/**
 * Puente explícito entre los repositorios actuales y el proyecto portable.
 * Los repositorios participan mediante ProjectWorkspaceContributor, por lo que
 * eventos, gamificación u otros módulos pueden añadirse sin modificar esta clase.
 */
@Component
public class ProjectWorkspaceBridge {
    private final ProjectWorkspaceContributionRegistry registry;
    private final ProjectArtifactClassifier classifier;
    private final ProjectFileOperations files;
    private final LocalProjectProperties properties;

    /** Descripción: Inicializa el puente con el registro de contribuciones, clasificador, operaciones de ficheros y límites portables compartidos. Entradas: registry: contribuciones disponibles; classifier: clasificador de artefactos; files: operaciones seguras de fichero; properties: presupuestos configurables del proyecto. Salida: deja la instancia lista para sincronizar. */
    /** Descripción: Inicializa una instancia de ProjectWorkspaceBridge con sus dependencias y datos de entrada. Entradas: registry: dato de entrada necesario para la operación; classifier: dato de entrada necesario para la operación; files: archivo o contenido que debe procesarse; properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public ProjectWorkspaceBridge(ProjectWorkspaceContributionRegistry registry,ProjectArtifactClassifier classifier,ProjectFileOperations files,LocalProjectProperties properties) {
        this.registry=registry;
        this.classifier=classifier;
        this.files=files;
        this.properties=properties;
    }

    /** Descripción: Calcula una sincronización sin escribir, aplicando los mismos límites y controles de seguridad que la ejecución real. Entradas: projectRoot: raíz del proyecto; descriptor: descriptor portable; request: dirección y modo. Salida: devuelve el plan determinista de cambios. */
    /** Descripción: Ejecuta la operación «preview» correspondiente a la responsabilidad de ProjectWorkspaceBridge. Entradas: projectRoot: dato de entrada necesario para la operación; descriptor: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ProjectSynchronizationPlan preview(Path projectRoot,ProjectDescriptor descriptor,ProjectSyncRequest request) {
        validate(request);
        List<ProjectSyncFileChange> changes=request.direction()==WorkspaceTransferDirection.WORKSPACE_TO_PROJECT
                ? workspaceToProject(projectRoot,descriptor,false,request.mode())
                : projectToWorkspace(projectRoot,descriptor,false,request.mode());
        List<String> warnings=new ArrayList<>();
        if(request.direction()==WorkspaceTransferDirection.PROJECT_TO_WORKSPACE) warnings.add("The current application repositories are reloaded after the transfer; unsaved in-memory changes must be saved first.");
        return new ProjectSynchronizationPlan(request.direction(),request.mode(),changes,warnings,true);
    }

    /** Descripción: Ejecuta la sincronización solicitada con inventario y bytes acotados, rechazo de enlaces simbólicos y rutas normalizadas. Entradas: projectRoot: raíz del proyecto; descriptor: descriptor portable; request: dirección y modo. Salida: devuelve los cambios aplicados. */
    /** Descripción: Actualiza el estado gestionado por ProjectWorkspaceBridge mediante la operación «apply». Entradas: projectRoot: dato de entrada necesario para la operación; descriptor: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ProjectSynchronizationResult apply(Path projectRoot,ProjectDescriptor descriptor,ProjectSyncRequest request) {
        validate(request);
        List<ProjectSyncFileChange> changes=request.direction()==WorkspaceTransferDirection.WORKSPACE_TO_PROJECT
                ? workspaceToProject(projectRoot,descriptor,true,request.mode())
                : projectToWorkspace(projectRoot,descriptor,true,request.mode());
        return new ProjectSynchronizationResult(request.direction(),request.mode(),Instant.now(),changes,List.of());
    }

    /** Descripción: Sincroniza repositorios configurados hacia el proyecto sin materializar previamente todos sus paths y respetando límites de cantidad, profundidad y bytes. Entradas: projectRoot: raíz destino; descriptor: layout; apply: indica si escribe; mode: estrategia de conflicto. Salida: devuelve cambios ordenados de forma determinista. */
    /** Descripción: Ejecuta la operación «workspaceToProject» correspondiente a la responsabilidad de ProjectWorkspaceBridge. Entradas: projectRoot: dato de entrada necesario para la operación; descriptor: dato de entrada necesario para la operación; apply: dato de entrada necesario para la operación; mode: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<ProjectSyncFileChange> workspaceToProject(Path projectRoot,ProjectDescriptor descriptor,boolean apply,WorkspaceTransferMode mode) {
        Path normalizedProject=requireRealRoot(projectRoot,"Project root");
        List<ProjectSyncFileChange> result=new ArrayList<>();
        SyncBudget budget=new SyncBudget();
        ProjectWorkspaceContributionRegistry.Snapshot snapshot=registry.snapshot(descriptor.layout());
        for(ProjectWorkspaceContribution contribution:snapshot.contributions()) {
            Path sourceRoot=contribution.workspaceRoot();
            if(!Files.exists(sourceRoot,LinkOption.NOFOLLOW_LINKS)) continue;
            requireRealRoot(sourceRoot,"Workspace contribution root");
            visitRegularFiles(sourceRoot,(file,attrs)->{
                budget.accept(sourceRoot,file,attrs);
                String local=portableRelative(sourceRoot,file);
                ArtifactKind kind=classifier.classify(local,descriptor.layout());
                if(!contribution.artifactKinds().contains(kind)) return;
                String projectDirectory=projectRootFor(kind,descriptor.layout(),contribution);
                Path destination=normalizedProject.resolve(projectDirectory).resolve(local).normalize();
                verifyDestination(normalizedProject,destination,"Synchronization destination escapes the project root");
                verifyExistingPathChain(normalizedProject,destination);
                String operation=operation(destination,mode);
                if(!operation.equals("SKIP")&&apply) copyPortableRegularFile(file,destination,attrs.size());
                result.add(change(file,destination,operation,kind,attrs.size()));
            },"Could not scan workspace contribution "+contribution.id());
        }
        return immutableSorted(result);
    }

    /** Descripción: Sincroniza el proyecto hacia los repositorios manteniendo la ruta relativa al root lógico de cada artefacto, evitando aplanamientos y colisiones. Entradas: projectRoot: raíz origen; descriptor: layout; apply: indica si escribe; mode: estrategia de conflicto. Salida: devuelve cambios ordenados de forma determinista. */
    /** Descripción: Ejecuta la operación «projectToWorkspace» correspondiente a la responsabilidad de ProjectWorkspaceBridge. Entradas: projectRoot: dato de entrada necesario para la operación; descriptor: dato de entrada necesario para la operación; apply: dato de entrada necesario para la operación; mode: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<ProjectSyncFileChange> projectToWorkspace(Path projectRoot,ProjectDescriptor descriptor,boolean apply,WorkspaceTransferMode mode) {
        Path normalizedProject=requireRealRoot(projectRoot,"Project root");
        List<ProjectSyncFileChange> result=new ArrayList<>();
        SyncBudget budget=new SyncBudget();
        ProjectWorkspaceContributionRegistry.Snapshot snapshot=registry.snapshot(descriptor.layout());
        visitRegularFiles(normalizedProject,(file,attrs)->{
            budget.accept(normalizedProject,file,attrs);
            String relative=portableRelative(normalizedProject,file);
            if(relative.equals("modeling-project.json")||relative.startsWith(descriptor.layout().internal()+"/")) return;
            ArtifactKind kind=classifier.classify(relative,descriptor.layout());
            Path destinationRoot=snapshot.targets().get(kind);
            if(destinationRoot==null) return;
            Path normalizedDestinationRoot=destinationRoot.toAbsolutePath().normalize();
            if(Files.exists(normalizedDestinationRoot,LinkOption.NOFOLLOW_LINKS)) requireRealRoot(normalizedDestinationRoot,"Workspace destination root");
            String relativeWithinArtifact=relativeWithinArtifactRoot(relative,kind,descriptor.layout(),snapshot);
            Path destination=normalizedDestinationRoot.resolve(relativeWithinArtifact).normalize();
            verifyDestination(normalizedDestinationRoot,destination,"Workspace destination escapes its repository");
            verifyExistingPathChain(normalizedDestinationRoot,destination);
            String operation=operation(destination,mode);
            if(!operation.equals("SKIP")&&apply) copyPortableRegularFile(file,destination,attrs.size());
            result.add(change(file,destination,operation,kind,attrs.size()));
        },"Could not scan the project");
        return immutableSorted(result);
    }

    /** Descripción: Recorre incrementalmente únicamente ficheros regulares reales sin seguir enlaces simbólicos. Entradas: root: raíz; consumer: operación por fichero; errorMessage: contexto del fallo. Salida: no devuelve valor. */
    /** Descripción: Ejecuta la operación «visitRegularFiles» correspondiente a la responsabilidad de ProjectWorkspaceBridge. Entradas: root: ruta o ubicación utilizada por la operación; consumer: dato de entrada necesario para la operación; errorMessage: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void visitRegularFiles(Path root,FileConsumer consumer,String errorMessage) {
        try {
            Files.walkFileTree(root,new SimpleFileVisitor<>() {
                /** Descripción: Rechaza directorios simbólicos antes de descender durante una sincronización de workspace. Entradas: directorio y atributos. Salida: devuelve la decisión de continuar el recorrido. */
                /** Descripción: Ejecuta la operación «preVisitDirectory» correspondiente a la responsabilidad de ProjectWorkspaceBridge. Entradas: dir: dato de entrada necesario para la operación; attrs: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public FileVisitResult preVisitDirectory(Path dir,BasicFileAttributes attrs) {
                    if(attrs.isSymbolicLink()) throw invalid("Symbolic links are not allowed during project synchronization: "+dir);
                    return FileVisitResult.CONTINUE;
                }
                /** Descripción: Entrega únicamente ficheros regulares reales al consumidor de sincronización y rechaza enlaces simbólicos. Entradas: fichero y atributos. Salida: devuelve la decisión de continuar el recorrido. */
                /** Descripción: Ejecuta la operación «visitFile» correspondiente a la responsabilidad de ProjectWorkspaceBridge. Entradas: file: archivo o contenido que debe procesarse; attrs: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attrs) {
                    if(attrs.isSymbolicLink()) throw invalid("Symbolic links are not allowed during project synchronization: "+file);
                    if(attrs.isRegularFile()) consumer.accept(file,attrs);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch(ProjectException exception) { throw exception; }
        catch(IOException exception) { throw storage(errorMessage,exception); }
    }

    /** Descripción: Copia un fichero regular mediante publicación atómica y presupuesto por fichero, rechazando cambios concurrentes de tipo o tamaño. Entradas: source: fichero origen; destination: destino; expectedSize: tamaño observado. Salida: no devuelve valor. */
    /** Descripción: Ejecuta la operación «copyPortableRegularFile» correspondiente a la responsabilidad de ProjectWorkspaceBridge. Entradas: source: dato de entrada necesario para la operación; destination: dato de entrada necesario para la operación; expectedSize: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void copyPortableRegularFile(Path source,Path destination,long expectedSize) {
        long maximum=Math.max(1L,Math.min(properties.getMaxUploadFileSizeBytes(),properties.getMaxArchiveSizeBytes()));
        if(expectedSize>maximum) throw tooLarge("Synchronization file exceeds the configured per-file limit: "+source);
        if(Files.isSymbolicLink(source)||!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS)) throw invalid("Synchronization source changed while being copied: "+source);
        try { files.copyRegularBounded(source,destination,maximum); }
        catch(ProjectException exception) { throw exception; }
    }

    /** Descripción: Obtiene el root lógico del tipo de artefacto usando primero el layout y después la contribución como fallback. Entradas: kind: tipo; layout: estructura portable; contribution: contribución origen. Salida: devuelve root relativo seguro. */
    /** Descripción: Ejecuta la operación «projectRootFor» correspondiente a la responsabilidad de ProjectWorkspaceBridge. Entradas: kind: dato de entrada necesario para la operación; layout: dato de entrada necesario para la operación; contribution: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String projectRootFor(ArtifactKind kind,ProjectLayout layout,ProjectWorkspaceContribution contribution) {
        String configured=layout.artifactRoots().get(kind);
        if(configured!=null) return configured;
        if(!contribution.fallbackProjectRoot().isBlank()) return contribution.fallbackProjectRoot();
        throw invalid("No project artifact root is configured for contribution "+contribution.id());
    }

    /** Descripción: Calcula la ruta del artefacto dentro de su repositorio manteniendo subdirectorios y evitando el aplanamiento por basename. Entradas: projectRelative: ruta dentro del proyecto; kind: tipo; layout: layout; snapshot: contribuciones. Salida: devuelve ruta relativa al repositorio destino. */
    /** Descripción: Ejecuta la operación «relativeWithinArtifactRoot» correspondiente a la responsabilidad de ProjectWorkspaceBridge. Entradas: projectRelative: dato de entrada necesario para la operación; kind: dato de entrada necesario para la operación; layout: dato de entrada necesario para la operación; snapshot: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String relativeWithinArtifactRoot(String projectRelative,ArtifactKind kind,ProjectLayout layout,ProjectWorkspaceContributionRegistry.Snapshot snapshot) {
        String root=layout.artifactRoots().get(kind);
        if(root==null) {
            for(ProjectWorkspaceContribution contribution:snapshot.contributions()) if(contribution.artifactKinds().contains(kind)&&!contribution.fallbackProjectRoot().isBlank()) { root=contribution.fallbackProjectRoot(); break; }
        }
        if(root==null||root.isBlank()) return Path.of(projectRelative).getFileName().toString();
        String normalizedRoot=root.replace('\\','/').replaceAll("/+$","");
        if(projectRelative.equals(normalizedRoot)) return Path.of(projectRelative).getFileName().toString();
        String prefix=normalizedRoot+"/";
        if(!projectRelative.startsWith(prefix)) return Path.of(projectRelative).getFileName().toString();
        return projectRelative.substring(prefix.length());
    }

    /** Descripción: Determina si el destino se creará, sustituirá o ignorará según existencia y modo. Entradas: destination: destino; mode: modo. Salida: devuelve CREATE, REPLACE o SKIP. */
    /** Descripción: Ejecuta la operación «operation» correspondiente a la responsabilidad de ProjectWorkspaceBridge. Entradas: destination: dato de entrada necesario para la operación; mode: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String operation(Path destination,WorkspaceTransferMode mode) {
        if(!Files.exists(destination,LinkOption.NOFOLLOW_LINKS)) return "CREATE";
        return mode==WorkspaceTransferMode.REPLACE?"REPLACE":"SKIP";
    }

    /** Descripción: Construye el cambio sin volver a consultar el filesystem para el tamaño ya observado. Entradas: source: origen; destination: destino; operation: operación; kind: tipo; size: bytes. Salida: devuelve el cambio. */
    /** Descripción: Ejecuta la operación «change» correspondiente a la responsabilidad de ProjectWorkspaceBridge. Entradas: source: dato de entrada necesario para la operación; destination: dato de entrada necesario para la operación; operation: dato de entrada necesario para la operación; kind: dato de entrada necesario para la operación; size: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ProjectSyncFileChange change(Path source,Path destination,String operation,ArtifactKind kind,long size) {
        return new ProjectSyncFileChange(source.toString(),destination.toString(),operation,kind,size);
    }

    /** Descripción: Ordena los cambios por origen y destino para producir planes reproducibles sin materializar listas intermedias de paths. Entradas: changes: cambios recopilados. Salida: devuelve copia inmutable ordenada. */
    /** Descripción: Ejecuta la operación «immutableSorted» correspondiente a la responsabilidad de ProjectWorkspaceBridge. Entradas: changes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<ProjectSyncFileChange> immutableSorted(List<ProjectSyncFileChange> changes) {
        changes.sort(Comparator.comparing(ProjectSyncFileChange::source).thenComparing(ProjectSyncFileChange::destination));
        return List.copyOf(changes);
    }

    /** Descripción: Valida dirección y modo obligatorios. Entradas: request: solicitud. Salida: no devuelve valor. */
    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validate(ProjectSyncRequest request) {
        if(request==null||request.direction()==null||request.mode()==null) throw invalid("Synchronization direction and mode are required");
    }

    /** Descripción: Exige una raíz real, normalizada y no simbólica. Entradas: root: ruta; label: nombre funcional. Salida: devuelve la raíz absoluta normalizada. */
    /** Descripción: Valida las condiciones necesarias para «requireRealRoot» y rechaza entradas no válidas. Entradas: root: ruta o ubicación utilizada por la operación; label: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Path requireRealRoot(Path root,String label) {
        if(root==null) throw invalid(label+" is required");
        Path normalized=root.toAbsolutePath().normalize();
        if(Files.isSymbolicLink(normalized)||!Files.isDirectory(normalized,LinkOption.NOFOLLOW_LINKS)) throw invalid(label+" must be a real directory");
        return normalized;
    }

    /** Descripción: Verifica que una ruta normalizada permanezca bajo su raíz. Entradas: root: raíz; destination: destino; message: error. Salida: no devuelve valor. */
    /** Descripción: Valida las condiciones necesarias para «verifyDestination» y rechaza entradas no válidas. Entradas: root: ruta o ubicación utilizada por la operación; destination: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void verifyDestination(Path root,Path destination,String message) {
        if(!destination.startsWith(root)) throw invalid(message);
    }

    /** Descripción: Rechaza enlaces simbólicos existentes en la cadena entre la raíz y el destino para evitar escapes indirectos. Entradas: root: raíz permitida; destination: destino. Salida: no devuelve valor. */
    /** Descripción: Valida las condiciones necesarias para «verifyExistingPathChain» y rechaza entradas no válidas. Entradas: root: ruta o ubicación utilizada por la operación; destination: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void verifyExistingPathChain(Path root,Path destination) {
        Path normalizedRoot=root.toAbsolutePath().normalize();
        Path current=normalizedRoot;
        if(Files.isSymbolicLink(current)) throw invalid("Symbolic links are not allowed in synchronization destinations: "+current);
        Path relative=normalizedRoot.relativize(destination.toAbsolutePath().normalize());
        for(Path segment:relative) {
            current=current.resolve(segment);
            if(Files.exists(current,LinkOption.NOFOLLOW_LINKS)&&Files.isSymbolicLink(current)) throw invalid("Symbolic links are not allowed in synchronization destinations: "+current);
        }
    }

    /** Descripción: Devuelve una ruta relativa portable con separadores normalizados. Entradas: root: raíz; path: fichero. Salida: devuelve ruta relativa. */
    /** Descripción: Ejecuta la operación «portableRelative» correspondiente a la responsabilidad de ProjectWorkspaceBridge. Entradas: root: ruta o ubicación utilizada por la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String portableRelative(Path root,Path path) { return root.relativize(path).toString().replace('\\','/'); }

    /** Descripción: Crea una excepción de solicitud inválida. Entradas: message: detalle. Salida: devuelve excepción. */
    /** Descripción: Ejecuta la operación «invalid» correspondiente a la responsabilidad de ProjectWorkspaceBridge. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ProjectException invalid(String message) { return new ProjectException(ProjectException.Reason.INVALID_REQUEST,message); }
    /** Descripción: Crea una excepción de exceso de presupuesto. Entradas: message: detalle. Salida: devuelve excepción. */
    /** Descripción: Ejecuta la operación «tooLarge» correspondiente a la responsabilidad de ProjectWorkspaceBridge. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ProjectException tooLarge(String message) { return new ProjectException(ProjectException.Reason.FILE_TOO_LARGE,message); }
    /** Descripción: Crea una excepción de almacenamiento preservando la causa. Entradas: message: detalle; cause: causa. Salida: devuelve excepción. */
    /** Descripción: Ejecuta la operación «storage» correspondiente a la responsabilidad de ProjectWorkspaceBridge. Entradas: message: dato de entrada necesario para la operación; cause: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ProjectException storage(String message,Throwable cause) { return new ProjectException(ProjectException.Reason.STORAGE_ERROR,message,cause); }

    /** Consumidor interno de fichero visitado que permite reutilizar el recorrido seguro en ambas direcciones. */
    @FunctionalInterface private interface FileConsumer { void accept(Path file,BasicFileAttributes attrs); }

    /** Presupuesto acumulado de una sincronización para evitar árboles no acotados. */
    private final class SyncBudget {
        private int entries;
        private long bytes;
        /** Descripción: Registra un fichero visitado y valida entradas, profundidad, tamaño individual y bytes acumulados. Entradas: root: raíz; file: fichero; attrs: atributos. Salida: no devuelve valor. */
        /** Descripción: Ejecuta la operación «accept» correspondiente a la responsabilidad de ProjectWorkspaceBridge. Entradas: root: ruta o ubicación utilizada por la operación; file: archivo o contenido que debe procesarse; attrs: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void accept(Path root,Path file,BasicFileAttributes attrs) {
            entries++;
            if(entries>Math.max(1,properties.getMaxArchiveEntries())) throw tooLarge("Project synchronization contains too many files");
            String relative=portableRelative(root,file);
            if(Path.of(relative).getNameCount()>Math.max(1,properties.getMaxTreeDepth())) throw tooLarge("Project synchronization contains entries deeper than the configured tree limit");
            long perFile=Math.max(1L,Math.min(properties.getMaxUploadFileSizeBytes(),properties.getMaxArchiveSizeBytes()));
            if(attrs.size()>perFile) throw tooLarge("Synchronization file exceeds the configured per-file limit: "+relative);
            try { bytes=Math.addExact(bytes,attrs.size()); }
            catch(ArithmeticException exception) { throw tooLarge("Project synchronization size overflow"); }
            if(bytes>properties.getMaxArchiveSizeBytes()) throw tooLarge("Project synchronization exceeds the configured total size limit");
        }
    }
}
