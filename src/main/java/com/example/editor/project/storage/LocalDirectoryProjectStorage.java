package com.example.editor.project.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.springframework.stereotype.Component;
import com.example.editor.io.AtomicFiles;
import com.example.editor.project.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Implementación completa del proyecto local basada en un directorio portable. */
@Component
public class LocalDirectoryProjectStorage implements ProjectStoragePort {
    private final LocalProjectProperties properties;
    private final SafeProjectPathResolver paths;
    private final ProjectDescriptorStore descriptors;
    private final ProjectIndexService index;
    private final ProjectArtifactClassifier classifier;
    private final ProjectMediaTypeResolver mediaTypes;
    private final ProjectHashService hashes;
    private final ProjectFileOperations files;
    private final ProjectRegistryStore registry;
    private final ProjectZipService archives;
    private final ProjectChangeTracker changes;
    private final ProjectWorkspaceBridge workspace;
    private final ObjectMapper mapper;
    private final ReentrantReadWriteLock lock=new ReentrantReadWriteLock();
    private Path activeRoot;
    private ProjectDescriptor activeDescriptor;
    private Instant openedAt;
    private List<String> restoredTabs=List.of();

    /** Descripción: Inicializa una instancia de LocalDirectoryProjectStorage con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; paths: ruta o ubicación utilizada por la operación; descriptors: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación; classifier: dato de entrada necesario para la operación; mediaTypes: dato de entrada necesario para la operación; hashes: dato de entrada necesario para la operación; files: archivo o contenido que debe procesarse; registry: dato de entrada necesario para la operación; archives: archivo o contenido que debe procesarse; changes: dato de entrada necesario para la operación; workspace: dato de entrada necesario para la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public LocalDirectoryProjectStorage(LocalProjectProperties properties,SafeProjectPathResolver paths,ProjectDescriptorStore descriptors,
            ProjectIndexService index,ProjectArtifactClassifier classifier,ProjectMediaTypeResolver mediaTypes,ProjectHashService hashes,
            ProjectFileOperations files,ProjectRegistryStore registry,ProjectZipService archives,ProjectChangeTracker changes,
            ProjectWorkspaceBridge workspace,ObjectMapper mapper) {
        this.properties=properties; this.paths=paths; this.descriptors=descriptors; this.index=index; this.classifier=classifier;
        this.mediaTypes=mediaTypes; this.hashes=hashes; this.files=files; this.registry=registry; this.archives=archives;
        this.changes=changes; this.workspace=workspace; this.mapper=mapper;
        restoreLastSession();
    }

    /** Descripción: Crea o prepara el resultado de «create» dentro de LocalDirectoryProjectStorage. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public ProjectSession create(CreateLocalProjectRequest request) {
        requireEnabled();
        if(request==null) throw invalid("Project creation request is required");
        String name=required(request.name(),"Project name");
        String id=required(request.projectId(),"Project id");
        if(!id.matches("[A-Za-z0-9._:-]+")) throw invalid("Project id contains unsupported characters");
        Path parent=paths.projectRoot(required(request.parentDirectory(),"Parent directory"));
        String directoryName=safeDirectoryName(name);
        Path root=parent.resolve(directoryName).normalize();
        if(Files.exists(root)) throw new ProjectException(ProjectException.Reason.CONFLICT,"Project directory already exists: " + root);
        lock.writeLock().lock();
        try {
            ProjectLayout layout=ProjectLayout.standard();
            Instant now=Instant.now();
            ProjectPreset preset=request.resolvedPreset();
            ProjectDescriptor descriptor=new ProjectDescriptor("1.1",id,name,request.description(),now,now,ProjectStorageMode.LOCAL_DIRECTORY,
                    layout,request.resolvedCapabilities(),java.util.Map.of("createdBy","local-user"),preset,request.resolvedExperienceMode(),new ProjectPrimaryArtifact("","",""));
            createStructure(root,descriptor);
            setActive(root,descriptor,List.of());
            if(request.initializeFromCurrentWorkspace()) workspace.apply(root,descriptor,new ProjectSyncRequest(WorkspaceTransferDirection.WORKSPACE_TO_PROJECT,WorkspaceTransferMode.REPLACE));
            refreshDescriptor();
            index.build(root,activeDescriptor,ProjectTreeView.LOGICAL);
            index.persist(root, activeDescriptor);
            changes.reset(root,activeDescriptor);
            return session();
        } finally { lock.writeLock().unlock(); }
    }

    /** Descripción: Crea o prepara el resultado de «open» dentro de LocalDirectoryProjectStorage. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public ProjectSession open(OpenLocalProjectRequest request) {
        requireEnabled();
        if(request==null) throw invalid("Project open request is required");
        Path root=paths.projectRoot(required(request.projectDirectory(),"Project directory"));
        lock.writeLock().lock();
        try {
            ProjectDescriptor descriptor=descriptors.read(root);
            List<String> tabs=loadTabs(root,descriptor);
            setActive(root,descriptor,tabs);
            if(request.loadIntoCurrentWorkspace()) workspace.apply(root,descriptor,new ProjectSyncRequest(WorkspaceTransferDirection.PROJECT_TO_WORKSPACE,request.transferMode()));
            index.build(root,descriptor,ProjectTreeView.LOGICAL);
            index.persist(root, descriptor);
            changes.reset(root,descriptor);
            return session();
        } finally { lock.writeLock().unlock(); }
    }

    /** Descripción: Ejecuta la operación «session» correspondiente a la responsabilidad de LocalDirectoryProjectStorage. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public ProjectSession session() {
        lock.readLock().lock();
        try { return activeRoot==null?ProjectSession.closed():new ProjectSession(true,activeDescriptor,activeRoot.toString(),openedAt,restoredTabs); }
        finally { lock.readLock().unlock(); }
    }
    /** Descripción: Ejecuta la operación «recentProjects» correspondiente a la responsabilidad de LocalDirectoryProjectStorage. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public List<ProjectSummary> recentProjects() { requireEnabled(); return registry.summaries(); }
    /** Descripción: Elimina o libera el recurso gestionado por «close» de forma controlada. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ @Override public void close() {
        lock.writeLock().lock();
        try { activeRoot=null; activeDescriptor=null; openedAt=null; restoredTabs=List.of(); registry.closeActive(); }
        finally { lock.writeLock().unlock(); }
    }
    /** Descripción: Ejecuta la operación «tree» correspondiente a la responsabilidad de LocalDirectoryProjectStorage. Entradas: view: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public ProjectTreeNode tree(ProjectTreeView view) {
        lock.readLock().lock();
        try { requireActive(); return index.build(activeRoot,activeDescriptor,view==null?ProjectTreeView.LOGICAL:view); }
        finally { lock.readLock().unlock(); }
    }

    /** Descripción: Obtiene la información correspondiente a «read» dentro de LocalDirectoryProjectStorage. Entradas: relativePath: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public ProjectFileContent read(String relativePath) {
        lock.readLock().lock();
        try {
            requireActive(); Path path=paths.resolve(activeRoot,required(relativePath,"File path"));
            if(!Files.isRegularFile(path)) throw notFound("Project file was not found: " + relativePath);
            byte[] content=readLimited(path,properties.getMaxUploadFileSizeBytes());
            String mediaType=mediaTypes.resolve(path); ArtifactKind kind=classifier.classify(relativePath,activeDescriptor.layout());
            boolean editable=classifier.editable(kind,mediaType,content.length,properties.getMaxEditableFileSizeBytes());
            boolean binary=!isText(mediaType,path);
            String value=binary?Base64.getEncoder().encodeToString(content):new String(content,StandardCharsets.UTF_8);
            return new ProjectFileContent(relativePath,path.getFileName().toString(),kind,classifier.ownership(relativePath,activeDescriptor.layout()),
                    mediaType,"UTF-8",binary,value,hashes.etag(content),content.length,lastModified(path),editable);
        } finally { lock.readLock().unlock(); }
    }

    /** Descripción: Actualiza el estado gestionado por LocalDirectoryProjectStorage mediante la operación «write». Entradas: relativePath: ruta o ubicación utilizada por la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public ProjectOperationResult write(String relativePath,ProjectFileWriteRequest request) {
        lock.writeLock().lock();
        try {
            requireActive(); if(request==null) throw invalid("File content is required");
            Path path=paths.resolve(activeRoot,required(relativePath,"File path"));
            if(!Files.isRegularFile(path)) throw notFound("Project file was not found: " + relativePath);
            verifyEtag(path,request.expectedEtag());
            byte[] content=request.binary()?decode(request.content()):safeText(request.content()).getBytes(StandardCharsets.UTF_8);
            verifyUploadSize(content.length); files.writeAtomic(path,content);
            ProjectTreeNode tree=afterMutation(relativePath);
            return result(relativePath,hashes.etag(content),tree);
        } finally { lock.writeLock().unlock(); }
    }

    /** Descripción: Crea o prepara el resultado de «createEntry» dentro de LocalDirectoryProjectStorage. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public ProjectOperationResult createEntry(ProjectEntryCreateRequest request) {
        lock.writeLock().lock();
        try {
            requireActive(); if(request==null) throw invalid("Entry request is required");
            String relative=required(request.relativePath(),"Entry path"); Path target=paths.resolve(activeRoot,relative);
            if(Files.exists(target)) throw conflict("Project entry already exists: " + relative);
            try {
                if(request.directory()) Files.createDirectories(target);
                else files.writeAtomic(target,safeText(request.content()).getBytes(StandardCharsets.UTF_8));
            } catch(IOException exception) { throw storage("Could not create " + relative,exception); }
            ProjectTreeNode tree=afterMutation(relative);
            return result(relative,request.directory()?"":hashes.etag(readLimited(target,properties.getMaxUploadFileSizeBytes())),tree);
        } finally { lock.writeLock().unlock(); }
    }

    /** Descripción: Ejecuta la operación «move» correspondiente a la responsabilidad de LocalDirectoryProjectStorage. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public ProjectOperationResult move(ProjectMoveRequest request) {
        lock.writeLock().lock();
        try {
            requireActive(); if(request==null) throw invalid("Move request is required");
            Path source=paths.resolve(activeRoot,required(request.sourcePath(),"Source path"));
            Path target=paths.resolve(activeRoot,required(request.targetPath(),"Target path"));
            protectReserved(request.sourcePath()); if(!Files.exists(source)) throw notFound("Source entry was not found");
            if(Files.exists(target)) throw conflict("Target entry already exists");
            if(Files.isRegularFile(source)) verifyEtag(source,request.expectedEtag());
            try { Files.createDirectories(target.getParent()); AtomicFiles.moveNew(source,target); }
            catch(IOException exception) { throw storage("Could not move project entry",exception); }
            ProjectTreeNode tree=afterMutation(request.sourcePath(),request.targetPath());
            return result(request.targetPath(),Files.isRegularFile(target)?hashes.etag(readLimited(target,properties.getMaxUploadFileSizeBytes())):"",tree);
        } finally { lock.writeLock().unlock(); }
    }

    /** Descripción: Ejecuta la operación «duplicate» correspondiente a la responsabilidad de LocalDirectoryProjectStorage. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public ProjectOperationResult duplicate(ProjectDuplicateRequest request) {
        lock.writeLock().lock();
        try {
            requireActive(); if(request==null) throw invalid("Duplicate request is required");
            Path source=paths.resolve(activeRoot,required(request.sourcePath(),"Source path"));
            Path target=paths.resolve(activeRoot,required(request.targetPath(),"Target path"));
            if(!Files.exists(source)) throw notFound("Source entry was not found");
            if(Files.exists(target)) throw conflict("Target entry already exists");
            files.copy(source,target);
            ProjectTreeNode tree=afterMutation(request.targetPath());
            return result(request.targetPath(),Files.isRegularFile(target)?hashes.etag(readLimited(target,properties.getMaxUploadFileSizeBytes())):"",tree);
        } finally { lock.writeLock().unlock(); }
    }

    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: relativePath: ruta o ubicación utilizada por la operación; expectedEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public ProjectOperationResult delete(String relativePath,String expectedEtag) {
        lock.writeLock().lock();
        try {
            requireActive(); String relative=required(relativePath,"Entry path"); protectReserved(relative);
            Path path=paths.resolve(activeRoot,relative); if(!Files.exists(path)) throw notFound("Project entry was not found");
            if(Files.isRegularFile(path)) verifyEtag(path,expectedEtag);
            files.deleteRecursively(path);
            ProjectTreeNode tree=afterMutation(relative);
            return result("","",tree);
        } finally { lock.writeLock().unlock(); }
    }

    /** Descripción: Ejecuta la operación «upload» correspondiente a la responsabilidad de LocalDirectoryProjectStorage. Entradas: directory: ruta o ubicación utilizada por la operación; fileName: archivo o contenido que debe procesarse; content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ @Override public ProjectOperationResult upload(String directory,String fileName,byte[] content) {
        lock.writeLock().lock();
        try {
            requireActive(); verifyUploadSize(content==null?0:content.length);
            String safeName=Path.of(required(fileName,"File name")).getFileName().toString();
            String relative=(directory==null||directory.isBlank()?"":directory.replace('\\','/')+"/")+safeName;
            Path target=paths.resolve(activeRoot,relative); if(Files.exists(target)) throw conflict("File already exists: " + relative);
            byte[] safeContent=content==null?new byte[0]:content;
            files.writeAtomic(target,safeContent);
            ProjectTreeNode tree=afterMutation(relative);
            return result(relative,hashes.etag(safeContent),tree);
        } finally { lock.writeLock().unlock(); }
    }

    /** Descripción: Ejecuta la operación «download» correspondiente a la responsabilidad de LocalDirectoryProjectStorage. Entradas: relativePath: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public byte[] download(String relativePath) {
        lock.readLock().lock();
        try { requireActive(); Path path=paths.resolve(activeRoot,required(relativePath,"File path")); if(!Files.isRegularFile(path)) throw notFound("Project file was not found"); return readLimited(path,properties.getMaxArchiveSizeBytes()); }
        finally { lock.readLock().unlock(); }
    }
    /** Descripción: Ejecuta la operación «exportArchive» correspondiente a la responsabilidad de LocalDirectoryProjectStorage. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public byte[] exportArchive() { lock.readLock().lock(); try { requireActive(); return archives.exportProject(activeRoot); } finally { lock.readLock().unlock(); } }

    /** Descripción: Ejecuta la operación «importArchive» correspondiente a la responsabilidad de LocalDirectoryProjectStorage. Entradas: parentDirectory: dato de entrada necesario para la operación; projectName: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ @Override public ProjectSession importArchive(String parentDirectory,String projectName,byte[] content) {
        requireEnabled(); String name=required(projectName,"Project name"); Path parent=paths.projectRoot(required(parentDirectory,"Parent directory"));
        Path destination=parent.resolve(safeDirectoryName(name)).normalize(); if(Files.exists(destination)) throw conflict("Project directory already exists");
        lock.writeLock().lock();
        try {
            archives.importProject(content,destination);
            Path descriptorPath=destination.resolve(ProjectDescriptorStore.FILE_NAME);
            if(!Files.isRegularFile(descriptorPath,java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                Path nested=singleDirectoryChild(destination);
                if(nested!=null&&Files.isRegularFile(nested.resolve(ProjectDescriptorStore.FILE_NAME),java.nio.file.LinkOption.NOFOLLOW_LINKS)) normalizeNestedImportedProject(destination,nested);
            }
            ProjectDescriptor descriptor=descriptors.read(destination); setActive(destination,descriptor,loadTabs(destination,descriptor));
            index.build(destination,descriptor,ProjectTreeView.LOGICAL); index.persist(destination, descriptor); changes.reset(destination,descriptor); return session();
        } catch(RuntimeException exception) {
            if(Files.exists(destination,java.nio.file.LinkOption.NOFOLLOW_LINKS)) files.deleteRecursively(destination);
            throw exception;
        } finally { lock.writeLock().unlock(); }
    }

    /** Descripción: Ejecuta la operación «importDirectory» correspondiente a la responsabilidad de LocalDirectoryProjectStorage. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public ProjectSession importDirectory(ImportLocalProjectDirectoryRequest request) {
        requireEnabled();
        if(request==null) throw invalid("Import request is required");
        Path source=paths.projectRoot(required(request.sourceDirectory(),"Source project directory"));
        if(!Files.isDirectory(source) || !Files.isRegularFile(source.resolve(ProjectDescriptorStore.FILE_NAME))) throw invalid("The selected directory is not a valid modeling project");
        validateDirectoryImportSource(source);
        Path parent=paths.projectRoot(required(request.parentDirectory(),"Parent directory"));
        Path destination=parent.resolve(safeDirectoryName(required(request.projectName(),"Project name"))).normalize();
        if(Files.exists(destination)) throw conflict("Project directory already exists");
        if(destination.startsWith(source)) throw invalid("The imported project cannot be copied inside its own source directory");
        lock.writeLock().lock();
        try {
            files.copy(source,destination);
            ProjectDescriptor descriptor=descriptors.read(destination);
            setActive(destination,descriptor,loadTabs(destination,descriptor));
            index.build(destination,descriptor,ProjectTreeView.LOGICAL);
            index.persist(destination, descriptor);
            changes.reset(destination,descriptor);
            return session();
        } catch(RuntimeException exception){
            files.deleteRecursively(destination);
            throw exception;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Descripción: Ejecuta la operación «pollChanges» correspondiente a la responsabilidad de LocalDirectoryProjectStorage. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public ProjectChangeSet pollChanges() { lock.writeLock().lock(); try { requireActive(); return changes.poll(activeRoot,activeDescriptor); } finally { lock.writeLock().unlock(); } }
    /** Descripción: Ejecuta la operación «previewSynchronization» correspondiente a la responsabilidad de LocalDirectoryProjectStorage. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public ProjectSynchronizationPlan previewSynchronization(ProjectSyncRequest request) { lock.readLock().lock(); try { requireActive(); return workspace.preview(activeRoot,activeDescriptor,request); } finally { lock.readLock().unlock(); } }
    /** Descripción: Actualiza el estado gestionado por LocalDirectoryProjectStorage mediante la operación «synchronize». Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public ProjectSynchronizationResult synchronize(ProjectSyncRequest request) {
        lock.writeLock().lock();
        try { requireActive(); ProjectSynchronizationResult result=workspace.apply(activeRoot,activeDescriptor,request); afterMutation(); return result; }
        finally { lock.writeLock().unlock(); }
    }
    /** Descripción: Actualiza el estado gestionado por LocalDirectoryProjectStorage mediante la operación «saveTabs». Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public ProjectSession saveTabs(ProjectTabSessionRequest request) {
        lock.writeLock().lock();
        try {
            requireActive(); restoredTabs=request==null?List.of():request.paths();
            try { files.writeAtomic(paths.resolve(activeRoot,activeDescriptor.layout().internal()+"/session.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(request==null?new ProjectTabSessionRequest(List.of()):request)); }
            catch(JacksonException exception) { throw storage("Could not serialize the project tab session",exception); }
            return session();
        } finally { lock.writeLock().unlock(); }
    }

    /** Descripción: Obtiene la información correspondiente a «search» dentro de LocalDirectoryProjectStorage. Entradas: query: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public ProjectSearchResult search(String query) {
        lock.readLock().lock();
        try {
            requireActive(); String needle=required(query,"Search query").toLowerCase(Locale.ROOT);
            List<ProjectSearchMatch> matches=new ArrayList<>(); boolean truncated=false;
            for(ProjectIndexedArtifact artifact:index.artifacts(activeRoot,activeDescriptor)) {
                if(matches.size()>=Math.max(1,properties.getMaxSearchResults())) { truncated=true; break; }
                Path path=paths.resolve(activeRoot,artifact.relativePath());
                String lowerName=path.getFileName().toString().toLowerCase(Locale.ROOT);
                if(lowerName.contains(needle)) matches.add(new ProjectSearchMatch(artifact.relativePath(),artifact.artifactKind(),0,lowerName.indexOf(needle)+1,path.getFileName().toString()));
                if(!artifact.editable() || artifact.size()>properties.getMaxEditableFileSizeBytes()) continue;
                String mediaType=mediaTypes.resolve(path); if(!isText(mediaType,path)) continue;
                try (var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)) {
                    String value; int line=0; int maximum=Math.max(1,properties.getMaxSearchResults());
                    while(matches.size()<maximum && (value=reader.readLine())!=null) {
                        line++; int column=value.toLowerCase(Locale.ROOT).indexOf(needle);
                        if(column>=0) matches.add(new ProjectSearchMatch(artifact.relativePath(),artifact.artifactKind(),line,column+1,value.strip()));
                    }
                } catch(IOException ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/project/storage/LocalDirectoryProjectStorage.java",ignored);}
            }
            return new ProjectSearchResult(query,matches,truncated);
        } finally { lock.readLock().unlock(); }
    }

    /** Actualiza capacidades y modo del proyecto sin cambiar su identidad ni eliminar artefactos existentes. */
    /** Descripción: Ejecuta la operación «configure» correspondiente a la responsabilidad de LocalDirectoryProjectStorage. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public ProjectSession configure(ProjectConfigurationRequest request) {
        lock.writeLock().lock();
        try {
            requireActive();
            if(request==null) throw invalid("Project configuration request is required");
            Set<ProjectCapability> requested=request.enabledCapabilities()==null?activeDescriptor.enabledCapabilities():request.enabledCapabilities();
            Set<ProjectCapability> resolved=ProjectCapabilityDependencies.resolve(requested);
            ProjectDescriptor next=activeDescriptor.configure(resolved,request.experienceMode(),request.primaryArtifact());
            try {
                for(String directory:next.layout().primaryRoots(next.enabledCapabilities())) Files.createDirectories(paths.resolve(activeRoot,directory));
            } catch(IOException exception) { throw storage("Could not materialize folders for the selected project capabilities",exception); }
            activeDescriptor=next;
            descriptors.write(activeRoot,activeDescriptor);
            index.invalidate(activeRoot);
            index.persist(activeRoot,activeDescriptor);
            changes.reset(activeRoot,activeDescriptor);
            return session();
        } finally { lock.writeLock().unlock(); }
    }

    /** Descripción: Obtiene la información correspondiente a «resolvePath» dentro de LocalDirectoryProjectStorage. Entradas: relativePath: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public ProjectResolvedPath resolvePath(String relativePath) {
        lock.readLock().lock();
        try {
            requireActive(); String relative=relativePath==null?"":relativePath;
            Path path=paths.resolve(activeRoot,relative);
            return new ProjectResolvedPath(relative,path.toString(),Files.isDirectory(path),Files.exists(path));
        } finally { lock.readLock().unlock(); }
    }

    /** Descripción: Valida un directorio importado mediante recorrido incremental, sin materializar el árbol, aplicando límites de entradas, profundidad, bytes y política de enlaces. Entradas: source: raíz importada. Salida: no devuelve valor. */ private void validateDirectoryImportSource(Path source) {
        int maximumEntries=Math.max(1,properties.getMaxArchiveEntries());int maximumDepth=Math.max(1,properties.getMaxTreeDepth());long maximumBytes=properties.getMaxArchiveSizeBytes();int[] entries={0};long[] total={0L};
        try {
            Files.walkFileTree(source,new java.nio.file.SimpleFileVisitor<>(){
                /** Descripción: Acumula de forma acotada una entrada del árbol importado y valida profundidad, enlaces y bytes antes de aceptar el proyecto. Entradas: path y atributos de la entrada. Salida: no devuelve valor; actualiza los contadores o rechaza la entrada. */
                /** Descripción: Ejecuta la operación «accept» correspondiente a la responsabilidad de LocalDirectoryProjectStorage. Entradas: path: ruta o ubicación utilizada por la operación; attrs: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void accept(Path path,java.nio.file.attribute.BasicFileAttributes attrs){
                    if(path.equals(source))return;
                    entries[0]++;if(entries[0]>maximumEntries)throw new ProjectException(ProjectException.Reason.FILE_TOO_LARGE,"The project directory contains too many entries");
                    if(source.relativize(path).getNameCount()>maximumDepth)throw new ProjectException(ProjectException.Reason.FILE_TOO_LARGE,"The project directory contains entries deeper than the configured tree limit");
                    if(!properties.isAllowSymbolicLinks()&&attrs.isSymbolicLink())throw invalid("Symbolic links are disabled for imported project directories");
                    if(attrs.isRegularFile()){
                        try{total[0]=Math.addExact(total[0],attrs.size());}catch(ArithmeticException exception){throw new ProjectException(ProjectException.Reason.FILE_TOO_LARGE,"The project directory is too large",exception);}
                        if(total[0]>maximumBytes)throw new ProjectException(ProjectException.Reason.FILE_TOO_LARGE,"The project directory is larger than the configured import limit");
                    }
                }
                /** Descripción: Valida cada directorio antes de recorrerlo para aplicar los límites del proyecto importado sin materializar el árbol completo. Entradas: directorio y atributos. Salida: devuelve la decisión de continuar el recorrido. */
                /** Descripción: Ejecuta la operación «preVisitDirectory» correspondiente a la responsabilidad de LocalDirectoryProjectStorage. Entradas: dir: dato de entrada necesario para la operación; attrs: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public java.nio.file.FileVisitResult preVisitDirectory(Path dir,java.nio.file.attribute.BasicFileAttributes attrs){accept(dir,attrs);return java.nio.file.FileVisitResult.CONTINUE;}
                /** Descripción: Valida cada fichero importado y acumula su coste dentro del presupuesto configurado. Entradas: fichero y atributos. Salida: devuelve la decisión de continuar el recorrido. */
                /** Descripción: Ejecuta la operación «visitFile» correspondiente a la responsabilidad de LocalDirectoryProjectStorage. Entradas: file: archivo o contenido que debe procesarse; attrs: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public java.nio.file.FileVisitResult visitFile(Path file,java.nio.file.attribute.BasicFileAttributes attrs){accept(file,attrs);return java.nio.file.FileVisitResult.CONTINUE;}
            });
        } catch(ProjectException exception) { throw exception; }
        catch(IOException exception) { throw storage("Could not inspect the project directory",exception); }
    }

    /** Descripción: Devuelve el único hijo directo cuando el directorio contiene exactamente una carpeta real; evita desempaquetar wrappers ambiguos. Entradas: directory: raíz importada. Salida: devuelve la carpeta única o null. */ private Path singleDirectoryChild(Path directory){
        try(var stream=Files.newDirectoryStream(directory)){Path only=null;for(Path entry:stream){if(only!=null)return null;only=entry;}return only!=null&&!Files.isSymbolicLink(only)&&Files.isDirectory(only,java.nio.file.LinkOption.NOFOLLOW_LINKS)?only:null;}
        catch(IOException exception){throw storage("Could not inspect imported project",exception);}
    }

    /** Descripción: Normaliza de forma transaccional un ZIP que contiene una única carpeta contenedora, sin mezclar entradas ni sobrescribir silenciosamente. Entradas: destination: destino publicado inicialmente; nested: carpeta contenedora. Salida: no devuelve valor. */ private void normalizeNestedImportedProject(Path destination,Path nested){
        Path parent=destination.toAbsolutePath().normalize().getParent();Path staging=null;
        try{
            staging=Files.createTempDirectory(parent,"."+destination.getFileName()+"-normalize-");
            try(var stream=Files.newDirectoryStream(nested)){for(Path item:stream)AtomicFiles.moveNew(item,staging.resolve(item.getFileName()));}
            files.deleteRecursively(destination);AtomicFiles.moveNew(staging,destination);staging=null;
        }catch(IOException|ProjectException exception){if(staging!=null)files.deleteRecursively(staging);files.deleteRecursively(destination);if(exception instanceof ProjectException projectException)throw projectException;throw storage("Could not normalize imported project",exception);}
    }

    /** Descripción: Crea o prepara el resultado de «createStructure» dentro de LocalDirectoryProjectStorage. Entradas: root: ruta o ubicación utilizada por la operación; descriptor: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void createStructure(Path root,ProjectDescriptor descriptor) {
        try {
            Files.createDirectories(root);
            for(String directory:descriptor.layout().primaryRoots(descriptor.enabledCapabilities())) Files.createDirectories(paths.resolve(root,directory));
            Files.createDirectories(paths.resolve(root,descriptor.layout().internal()+"/cache"));
            Files.createDirectories(paths.resolve(root,descriptor.layout().internal()+"/locks"));
            descriptors.write(root,descriptor);
            files.writeAtomic(root.resolve("README.md"),projectReadme(descriptor).getBytes(StandardCharsets.UTF_8));
            files.writeAtomic(root.resolve(".gitignore"),projectGitignore().getBytes(StandardCharsets.UTF_8));
            files.writeAtomic(paths.resolve(root,descriptor.layout().internal()+"/settings.json"),"{\n  \"schemaVersion\": \"1.0\"\n}\n".getBytes(StandardCharsets.UTF_8));
            files.writeAtomic(paths.resolve(root,descriptor.layout().internal()+"/session.json"),"{\n  \"paths\": []\n}\n".getBytes(StandardCharsets.UTF_8));
        } catch(IOException exception) { files.deleteRecursively(root); throw storage("Could not create the project structure",exception); }
    }
    /** Descripción: Actualiza el estado gestionado por LocalDirectoryProjectStorage mediante la operación «setActive». Entradas: root: ruta o ubicación utilizada por la operación; descriptor: dato de entrada necesario para la operación; tabs: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void setActive(Path root,ProjectDescriptor descriptor,List<String> tabs) {
        activeRoot=root; activeDescriptor=descriptor; openedAt=Instant.now(); restoredTabs=tabs==null?List.of():List.copyOf(tabs);
        registry.remember(root.toString(),descriptor.id(),descriptor.name(),true);
    }
    /** Descripción: Actualiza el árbol y el seguimiento tras una mutación invalidando solo las rutas afectadas; sin rutas explícitas invalida todo el proyecto. Entradas: affectedRelativePaths: colección de rutas modificadas. Salida: devuelve el árbol lógico actualizado. */ private ProjectTreeNode afterMutation(String... affectedRelativePaths) {
        refreshDescriptor();
        if(affectedRelativePaths == null || affectedRelativePaths.length == 0) {
            index.invalidate(activeRoot);
            index.persist(activeRoot,activeDescriptor);
        } else {
            for(String relativePath : affectedRelativePaths) if(relativePath != null && !relativePath.isBlank()) index.invalidate(paths.resolve(activeRoot,relativePath));
            index.invalidate(activeRoot.resolve(ProjectDescriptorStore.FILE_NAME));
            index.persistAffected(activeRoot,activeDescriptor,affectedRelativePaths);
        }
        ProjectTreeNode tree=index.build(activeRoot,activeDescriptor,ProjectTreeView.LOGICAL);
        changes.reset(activeRoot,activeDescriptor);
        return tree;
    }
    /** Descripción: Ejecuta la operación «refreshDescriptor» correspondiente a la responsabilidad de LocalDirectoryProjectStorage. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void refreshDescriptor() { activeDescriptor=activeDescriptor.touch(Instant.now()); descriptors.write(activeRoot,activeDescriptor); }
    /** Descripción: Ejecuta la operación «result» correspondiente a la responsabilidad de LocalDirectoryProjectStorage. Entradas: relative: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación; tree: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ProjectOperationResult result(String relative,String etag,ProjectTreeNode tree) { return new ProjectOperationResult(session(),relative,etag,tree); }
    /** Descripción: Ejecuta la operación «restoreLastSession» correspondiente a la responsabilidad de LocalDirectoryProjectStorage. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void restoreLastSession() {
        try {
            String directory=registry.load().activeDirectory();
            if(directory!=null&&!directory.isBlank()&&Files.isDirectory(Path.of(directory))) {
                Path root=paths.projectRoot(directory); ProjectDescriptor descriptor=descriptors.read(root); setActive(root,descriptor,loadTabs(root,descriptor)); changes.reset(root,descriptor);
            }
        } catch(RuntimeException ignored) { activeRoot=null; activeDescriptor=null; openedAt=null; restoredTabs=List.of(); }
    }
    /** Descripción: Obtiene la información correspondiente a «loadTabs» dentro de LocalDirectoryProjectStorage. Entradas: root: ruta o ubicación utilizada por la operación; descriptor: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> loadTabs(Path root,ProjectDescriptor descriptor) {
        Path path=paths.resolve(root,descriptor.layout().internal()+"/session.json"); if(!Files.isRegularFile(path)) return List.of();
        try { return mapper.readValue(com.example.editor.io.BoundedIo.readInternal(path),ProjectTabSessionRequest.class).paths(); }
        catch(IOException | JacksonException | IllegalArgumentException exception) { return List.of(); }
    }
    /** Descripción: Obtiene la información correspondiente a «readLimited» dentro de LocalDirectoryProjectStorage. Entradas: path: ruta o ubicación utilizada por la operación; limit: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] readLimited(Path path,long limit) {
        try { long size=Files.size(path); if(size>limit) throw new ProjectException(ProjectException.Reason.FILE_TOO_LARGE,"Project file exceeds the configured limit"); return com.example.editor.io.BoundedIo.readInternal(path); }
        catch(ProjectException exception) { throw exception; } catch(IOException exception) { throw storage("Could not read " + path,exception); }
    }
    /** Descripción: Valida las condiciones necesarias para «verifyEtag» y rechaza entradas no válidas. Entradas: path: ruta o ubicación utilizada por la operación; expected: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void verifyEtag(Path path,String expected) {
        if(expected==null||expected.isBlank()) return;
        String current=hashes.etag(readLimited(path,properties.getMaxUploadFileSizeBytes()));
        if(!current.equals(expected)) throw new ProjectException(ProjectException.Reason.PRECONDITION_FAILED,"The project file changed since it was opened");
    }
    /** Descripción: Valida las condiciones necesarias para «verifyUploadSize» y rechaza entradas no válidas. Entradas: size: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void verifyUploadSize(long size) { if(size>properties.getMaxUploadFileSizeBytes()) throw new ProjectException(ProjectException.Reason.FILE_TOO_LARGE,"File exceeds the configured upload limit"); }
    /** Descripción: Evalúa la condición «isText» sin modificar el estado externo. Entradas: mediaType: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isText(String mediaType,Path path) {
        String name=path.getFileName().toString().toLowerCase(Locale.ROOT);
        return mediaType.startsWith("text/")||mediaType.contains("json")||mediaType.contains("xml")||mediaType.contains("javascript")||name.endsWith(".drawio")||name.endsWith(".puml");
    }
    /** Descripción: Ejecuta la operación «decode» correspondiente a la responsabilidad de LocalDirectoryProjectStorage. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] decode(String content) { try { return Base64.getDecoder().decode(safeText(content)); } catch(IllegalArgumentException exception) { throw invalid("Binary content must use Base64"); } }
    /** README inicial orientado a capacidades, evitando documentar módulos que el proyecto no utiliza. */
    /** Descripción: Ejecuta la operación «projectReadme» correspondiente a la responsabilidad de LocalDirectoryProjectStorage. Entradas: descriptor: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String projectReadme(ProjectDescriptor descriptor) {
        StringBuilder text=new StringBuilder("# ").append(descriptor.name()).append("\n\n");
        if(descriptor.description()!=null&&!descriptor.description().isBlank()) text.append(descriptor.description().trim()).append("\n\n");
        text.append("Proyecto HYDRA portable creado desde el preset `").append(descriptor.createdFromPreset().name()).append("`.\n\n")
            .append("## Capacidades activas\n\n");
        if(descriptor.enabledCapabilities().isEmpty()) text.append("- Ninguna todavía. Puedes añadir capacidades desde el explorador del proyecto.\n");
        else descriptor.enabledCapabilities().stream().sorted(java.util.Comparator.comparing(Enum::name)).forEach(capability->text.append("- `").append(capability.name()).append("`\n"));
        text.append("\nLas capacidades son ampliables: el preset solo define el punto de partida y no limita la evolución del proyecto.\n\n")
            .append("## Estructura\n\n")
            .append("- `modeling-project.json` — descriptor canónico del proyecto.\n")
            .append("- `.modeling/` — estado interno de HYDRA; no necesita edición manual.\n");
        descriptor.layout().primaryRoots(descriptor.enabledCapabilities()).stream()
            .sorted()
            .forEach(root -> text.append("- `").append(root).append("/`\n"));
        text.append("\nLas raíces anteriores corresponden a las capacidades activas. Las subcarpetas especializadas se crean bajo demanda cuando se añade el primer artefacto que las necesita.\n");
        return text.toString();
    }
    /** Descripción: Ejecuta la operación «projectGitignore» correspondiente a la responsabilidad de LocalDirectoryProjectStorage. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private String projectGitignore() {
        return ".modeling/cache/\n.modeling/locks/\n.DS_Store\nThumbs.db\n";
    }
    /** Descripción: Ejecuta la operación «safeDirectoryName» correspondiente a la responsabilidad de LocalDirectoryProjectStorage. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String safeDirectoryName(String value) { String safe=value.trim().replaceAll("[^A-Za-z0-9._-]+","-").replaceAll("^-+|-+$",""); if(safe.isBlank()) throw invalid("Project name cannot produce an empty directory name"); return safe; }
    /** Descripción: Ejecuta la operación «protectReserved» correspondiente a la responsabilidad de LocalDirectoryProjectStorage. Entradas: relative: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void protectReserved(String relative) {
        String normalized=relative.replace('\\','/');
        boolean reserved=normalized.equals("modeling-project.json")
                || normalized.equals(activeDescriptor.layout().internal())
                || normalized.startsWith(activeDescriptor.layout().internal()+"/")
                || activeDescriptor.layout().logicalRoots().containsValue(normalized)
                || activeDescriptor.layout().primaryRoots().contains(normalized);
        if(reserved) throw invalid("Reserved project structure cannot be moved or deleted");
    }
    /** Descripción: Ejecuta la operación «lastModified» correspondiente a la responsabilidad de LocalDirectoryProjectStorage. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Instant lastModified(Path path) { try { return Files.getLastModifiedTime(path).toInstant(); } catch(IOException exception) { throw storage("Could not read file metadata",exception); } }
    /** Descripción: Ejecuta la operación «safeText» correspondiente a la responsabilidad de LocalDirectoryProjectStorage. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String safeText(String value) { return value==null?"":value; }
    /** Descripción: Valida las condiciones necesarias para «required» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String required(String value,String field) { if(value==null||value.isBlank()) throw invalid(field+" is required"); return value.trim(); }
    /** Descripción: Valida las condiciones necesarias para «requireEnabled» y rechaza entradas no válidas. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireEnabled() { if(!properties.isEnabled()) throw new ProjectException(ProjectException.Reason.DISABLED,"Local projects are disabled"); }
    /** Descripción: Valida las condiciones necesarias para «requireActive» y rechaza entradas no válidas. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireActive() { requireEnabled(); if(activeRoot==null||activeDescriptor==null) throw new ProjectException(ProjectException.Reason.NOT_FOUND,"No local project is open"); }
    /** Descripción: Ejecuta la operación «invalid» correspondiente a la responsabilidad de LocalDirectoryProjectStorage. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ProjectException invalid(String message) { return new ProjectException(ProjectException.Reason.INVALID_REQUEST,message); }
    /** Descripción: Ejecuta la operación «notFound» correspondiente a la responsabilidad de LocalDirectoryProjectStorage. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ProjectException notFound(String message) { return new ProjectException(ProjectException.Reason.NOT_FOUND,message); }
    /** Descripción: Ejecuta la operación «conflict» correspondiente a la responsabilidad de LocalDirectoryProjectStorage. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ProjectException conflict(String message) { return new ProjectException(ProjectException.Reason.CONFLICT,message); }
    /** Descripción: Ejecuta la operación «storage» correspondiente a la responsabilidad de LocalDirectoryProjectStorage. Entradas: message: dato de entrada necesario para la operación; cause: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ProjectException storage(String message,Throwable cause) { return new ProjectException(ProjectException.Reason.STORAGE_ERROR,message,cause); }
}
