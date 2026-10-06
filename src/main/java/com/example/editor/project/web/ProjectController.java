package com.example.editor.project.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.example.editor.project.*;
import com.example.editor.project.storage.HeadlessProjectPathPicker;
import com.example.editor.project.storage.ProjectDeletionService;
import com.example.editor.project.storage.ProjectVisualAssetPolicy;
import com.example.editor.projection.drawio.DrawioProjectionService;

/** API completa de proyectos locales y Project Explorer. */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectApplicationService service;
    private final DrawioProjectionService drawio;
    private final ProjectPathPicker pathPicker;
    private final LocalProjectProperties properties;
    private final ProjectDeletionService deletionService;
    private final ProjectVisualAssetPolicy visualAssets;

    @Autowired
    /** Descripción: Inicializa el controlador desde Spring y garantiza un selector seguro aunque una configuración externa suprima los beans opcionales. Entradas: service: servicio de proyectos; drawio: proyección; pathPickerProvider: proveedor opcional; properties: configuración; deletionService: borrado; visualAssets: política visual. Salida: deja el controlador inicializado con un fallback headless cuando no existe selector. */ public ProjectController(ProjectApplicationService service,DrawioProjectionService drawio,ObjectProvider<ProjectPathPicker> pathPickerProvider,LocalProjectProperties properties,ProjectDeletionService deletionService,ProjectVisualAssetPolicy visualAssets) {
        this(service, drawio, pathPickerProvider.getIfAvailable(HeadlessProjectPathPicker::new), properties, deletionService, visualAssets);
    }

    /** Descripción: Inicializa una instancia de ProjectController con un selector resuelto explícitamente. Entradas: service: servicio; drawio: proyección; pathPicker: selector; properties: configuración; deletionService: borrado; visualAssets: política visual. Salida: deja la instancia lista. */ public ProjectController(ProjectApplicationService service,DrawioProjectionService drawio,ProjectPathPicker pathPicker,LocalProjectProperties properties,ProjectDeletionService deletionService,ProjectVisualAssetPolicy visualAssets) {
        this.service=service;
        this.drawio=drawio;
        this.pathPicker=pathPicker == null ? new HeadlessProjectPathPicker() : pathPicker;
        this.properties=properties;
        this.deletionService=deletionService;
        this.visualAssets=visualAssets;
    }

    /** Descripción: Mantiene compatibilidad con integraciones anteriores sin política visual inyectada. Entradas: service, drawio, pathPicker, properties y deletionService. Salida: inicializa el controlador con la política visual segura predeterminada. */ public ProjectController(ProjectApplicationService service,DrawioProjectionService drawio,ProjectPathPicker pathPicker,LocalProjectProperties properties,ProjectDeletionService deletionService) {
        this(service,drawio,pathPicker,properties,deletionService,new ProjectVisualAssetPolicy());
    }

    /** Descripción: Inicializa una instancia de ProjectController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación; drawio: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ProjectController(ProjectApplicationService service,DrawioProjectionService drawio) {
        this(service,drawio,request -> ProjectPathPickerResult.unavailable(request.mode(),"The native path picker is not configured."),new LocalProjectProperties(),null,new ProjectVisualAssetPolicy());
    }

    @GetMapping("/session") public ProjectSession session() { return service.session(); }
    @PostMapping("/path-picker") public ProjectPathPickerResult pickPath(@RequestBody ProjectPathPickerRequest request,HttpServletRequest servletRequest) {
        if(properties.isNativePathPickerLocalOnly() && !isSameComputerRequest(servletRequest)){
            return ProjectPathPickerResult.unavailable(request.mode(),"The directory selector can only be opened when this browser is running on the same computer as HYDRA. Open HYDRA locally on that computer, or enter a server-side absolute path manually.");
        }
        return pathPicker.pick(request);
    }
    @GetMapping("/recent") public List<ProjectSummary> recent() { return service.recentProjects(); }
    @GetMapping("/catalog") public ProjectCreationCatalog catalog() { return ProjectCreationCatalog.current(); }
    /** Descripción: Crea o prepara el resultado de «create» dentro de ProjectController. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ @PostMapping public ProjectSession create(@RequestBody CreateLocalProjectRequest request) { return service.create(request); }
    @PostMapping("/open") public ProjectSession open(@RequestBody OpenLocalProjectRequest request) { return service.open(request); }
    @PostMapping("/close") public ResponseEntity<Void> close() { service.close(); return ResponseEntity.noContent().build(); }
    @GetMapping("/active/tree") public ProjectTreeNode tree(@RequestParam(defaultValue="LOGICAL") ProjectTreeView view) { return service.tree(view); }
    @PutMapping("/active/configuration") public ProjectSession configure(@RequestBody ProjectConfigurationRequest request) { return service.configure(request); }
    @GetMapping("/active/file") public ResponseEntity<ProjectFileContent> read(@RequestParam String path) {
        ProjectFileContent content=service.read(path); return ResponseEntity.ok().header(HttpHeaders.ETAG,content.etag()).body(content);
    }
    @PutMapping("/active/file") public ResponseEntity<ProjectOperationResult> write(@RequestParam String path,@RequestBody ProjectFileWriteRequest request) {
        ProjectOperationResult result=service.write(path,request); return ResponseEntity.ok().header(HttpHeaders.ETAG,result.etag()).body(result);
    }
    @PostMapping("/active/entries") public ProjectOperationResult createEntry(@RequestBody ProjectEntryCreateRequest request) { return service.createEntry(request); }
    @PostMapping("/active/move") public ProjectOperationResult move(@RequestBody ProjectMoveRequest request) { return service.move(request); }
    @PostMapping("/active/duplicate") public ProjectOperationResult duplicate(@RequestBody ProjectDuplicateRequest request) { return service.duplicate(request); }
    @DeleteMapping("/active/entries") public ProjectOperationResult delete(@RequestParam String path,@RequestParam(required=false) String etag) { return service.delete(path,etag); }
    @GetMapping("/active/deletion-impact") public ProjectDeletionImpact deletionImpact() { return deletionService().analyzeActiveProject(); }
    @DeleteMapping("/active") public ProjectDeletionResult deleteActiveProject(@RequestParam(defaultValue="false") boolean deleteDependencies) { return deletionService().deleteActiveProject(deleteDependencies); }
    @PostMapping(path="/active/upload",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    /** Descripción: Ejecuta la operación «upload» correspondiente a la responsabilidad de ProjectController. Entradas: directory: ruta o ubicación utilizada por la operación; file: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public ProjectOperationResult upload(@RequestParam(defaultValue="") String directory,@RequestPart("file") MultipartFile file) throws java.io.IOException {
        return service.upload(directory,file.getOriginalFilename()==null?"upload.bin":file.getOriginalFilename(),file.getBytes());
    }
    @GetMapping("/active/download") public ResponseEntity<ByteArrayResource> download(@RequestParam String path) {
        byte[] content=service.download(path); String fileName=path.contains("/")?path.substring(path.lastIndexOf('/')+1):path;
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(content.length)
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(fileName,StandardCharsets.UTF_8).build().toString())
                .body(new ByteArrayResource(content));
    }
    @GetMapping("/active/visual")
    /** Descripción: Sirve una imagen validada del proyecto para su visualización segura en línea. Entradas: path: ruta portable de la imagen. Salida: contenido con su tipo multimedia real y cabeceras de seguridad. */ public ResponseEntity<ByteArrayResource> visual(@RequestParam String path) {
        byte[] content=service.download(path); ProjectVisualAssetPolicy.ProjectVisualAsset asset=visualAssets.validate(path,content);
        String fileName=path.contains("/")?path.substring(path.lastIndexOf('/')+1):path;
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(asset.mediaType())).contentLength(asset.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.inline().filename(fileName,StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","default-src 'none'; img-src data:; style-src 'unsafe-inline'; sandbox")
                .header(HttpHeaders.CACHE_CONTROL,"no-store").body(new ByteArrayResource(asset.content()));
    }
    @GetMapping("/active/export") public ResponseEntity<ByteArrayResource> exportProject() {
        ProjectSession session=service.session(); byte[] content=service.exportArchive(); String name=session.project()==null?"project":session.project().name();
        String fileName=URLEncoder.encode(name,StandardCharsets.UTF_8).replace("+","-")+".zip";
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip")).contentLength(content.length)
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(fileName,StandardCharsets.UTF_8).build().toString())
                .body(new ByteArrayResource(content));
    }
    @PostMapping(value="/active/export-selection",produces="application/zip")
    /** Descripción: Ejecuta la operación «exportSelection» correspondiente a la responsabilidad de ProjectController. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ByteArrayResource> exportSelection(@RequestBody ProjectSelectionExportRequest request) {
        ProjectSession session=service.session();
        byte[] content=selectionArchive(request.paths());
        String name=session.project()==null?"project":session.project().name();
        String fileName=URLEncoder.encode(name,StandardCharsets.UTF_8).replace("+","-")+"-selection.zip";
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip")).contentLength(content.length)
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(fileName,StandardCharsets.UTF_8).build().toString())
                .body(new ByteArrayResource(content));
    }
    @PostMapping(path="/import",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    /** Descripción: Ejecuta la operación «importProject» correspondiente a la responsabilidad de ProjectController. Entradas: parentDirectory: dato de entrada necesario para la operación; projectName: dato de entrada necesario para la operación; file: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public ProjectSession importProject(@RequestParam String parentDirectory,@RequestParam String projectName,@RequestPart("file") MultipartFile file) throws java.io.IOException {
        return service.importArchive(parentDirectory,projectName,file.getBytes());
    }
    @PostMapping(path="/import-browser-directory",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    /** Descripción: Ejecuta la operación «importBrowserDirectory» correspondiente a la responsabilidad de ProjectController. Entradas: parentDirectory: dato de entrada necesario para la operación; projectName: dato de entrada necesario para la operación; files: archivo o contenido que debe procesarse; paths: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ProjectSession importBrowserDirectory(@RequestParam String parentDirectory,@RequestParam String projectName,@RequestPart("files") List<MultipartFile> files,@RequestParam("paths") List<String> paths) {
        return service.importArchive(parentDirectory,projectName,browserDirectoryArchive(files,paths));
    }
    @PostMapping("/import-directory") public ProjectSession importProjectDirectory(@RequestBody ImportLocalProjectDirectoryRequest request) { return service.importDirectory(request); }
    @GetMapping("/active/changes") public ProjectChangeSet changes() { return service.pollChanges(); }
    @PostMapping("/active/sync/preview") public ProjectSynchronizationPlan preview(@RequestBody ProjectSyncRequest request) { return service.preview(request); }
    @PostMapping("/active/sync") public ProjectSynchronizationResult synchronize(@RequestBody ProjectSyncRequest request) { return service.synchronize(request); }
    @PutMapping("/active/tabs") public ProjectSession saveTabs(@RequestBody ProjectTabSessionRequest request) { return service.saveTabs(request); }
    @GetMapping("/active/search") public ProjectSearchResult search(@RequestParam String query) { return service.search(query); }
    @GetMapping("/active/local-path") public ProjectResolvedPath localPath(@RequestParam(defaultValue="") String path) { return service.resolvePath(path); }

    @GetMapping(value="/active/drawio/descriptor",produces="application/xml")
    /** Descripción: Ejecuta la operación «descriptorDrawio» correspondiente a la responsabilidad de ProjectController. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<String> descriptorDrawio() {
        ProjectSession session=service.session();
        if(!session.active() || session.project()==null) throw new ProjectException(ProjectException.Reason.NOT_FOUND,"No local project is open");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(drawio.export("project-descriptor",session.project()));
    }
    @GetMapping(value="/active/drawio/tree",produces="application/xml")
    /** Descripción: Ejecuta la operación «treeDrawio» correspondiente a la responsabilidad de ProjectController. Entradas: view: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<String> treeDrawio(@RequestParam(defaultValue="LOGICAL") ProjectTreeView view) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(drawio.export("project-tree",service.tree(view)));
    }
    @PostMapping(value="/active/drawio/sync-plan",produces="application/xml")
    /** Descripción: Actualiza el estado gestionado por ProjectController mediante la operación «syncPlanDrawio». Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<String> syncPlanDrawio(@RequestBody ProjectSyncRequest request) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(drawio.export("project-sync-plan",service.preview(request)));
    }


    /** Descripción: Ejecuta la operación «browserDirectoryArchive» correspondiente a la responsabilidad de ProjectController. Entradas: files: archivo o contenido que debe procesarse; paths: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] browserDirectoryArchive(List<MultipartFile> files,List<String> paths) {
        if(files==null||files.isEmpty())throw new ProjectException(ProjectException.Reason.INVALID_REQUEST,"Select a project folder before importing");
        if(paths==null||paths.size()!=files.size())throw new ProjectException(ProjectException.Reason.INVALID_REQUEST,"The selected folder paths do not match the uploaded files");
        if(files.size()>properties.getMaxArchiveEntries())throw new ProjectException(ProjectException.Reason.FILE_TOO_LARGE,"The selected project folder contains too many files");
        Set<String> entries=new LinkedHashSet<>();long total=0;
        try(ByteArrayOutputStream output=new ByteArrayOutputStream();ZipOutputStream zip=new ZipOutputStream(output,StandardCharsets.UTF_8)){
            for(int index=0;index<files.size();index++){
                MultipartFile file=files.get(index);String path=normalizeBrowserDirectoryPath(paths.get(index));
                if(!entries.add(path))throw new ProjectException(ProjectException.Reason.INVALID_REQUEST,"The selected project folder contains duplicate paths: "+path);
                long size=file.getSize();if(size>properties.getMaxUploadFileSizeBytes())throw new ProjectException(ProjectException.Reason.FILE_TOO_LARGE,"Project file exceeds the configured upload limit: "+path);
                total+=size;if(total>properties.getMaxArchiveSizeBytes())throw new ProjectException(ProjectException.Reason.FILE_TOO_LARGE,"The selected project folder exceeds the configured import-size limit");
                zip.putNextEntry(new ZipEntry(path));file.getInputStream().transferTo(zip);zip.closeEntry();
                if(output.size()>properties.getMaxArchiveSizeBytes())throw new ProjectException(ProjectException.Reason.FILE_TOO_LARGE,"The selected project folder exceeds the configured archive-size limit");
            }
            zip.finish();return output.toByteArray();
        }catch(ProjectException exception){throw exception;}catch(IOException exception){throw new ProjectException(ProjectException.Reason.STORAGE_ERROR,"Could not read the selected project folder",exception);}
    }

    /** Descripción: Ejecuta la operación «normalizeBrowserDirectoryPath» correspondiente a la responsabilidad de ProjectController. Entradas: rawPath: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String normalizeBrowserDirectoryPath(String rawPath) {
        String path=rawPath==null?"":rawPath.replace('\\','/').trim();
        while(path.startsWith("/"))path=path.substring(1);
        if(path.isBlank()||path.equals("..")||path.startsWith("../")||path.contains("/../")||path.contains("//")||path.indexOf('\0')>=0)throw new ProjectException(ProjectException.Reason.INVALID_REQUEST,"The selected project folder contains an invalid path");
        return path;
    }

    /** Descripción: Ejecuta la operación «selectionArchive» correspondiente a la responsabilidad de ProjectController. Entradas: requestedPaths: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] selectionArchive(List<String> requestedPaths) {
        if(requestedPaths==null || requestedPaths.isEmpty()) throw new ProjectException(ProjectException.Reason.INVALID_REQUEST,"At least one project entry must be selected");
        if(requestedPaths.size()>properties.getMaxArchiveEntries()) throw new ProjectException(ProjectException.Reason.INVALID_REQUEST,"Too many project entries were selected");
        ProjectTreeNode root=service.tree(ProjectTreeView.PHYSICAL);
        Set<String> directoryEntries=new LinkedHashSet<>();
        Set<String> fileEntries=new LinkedHashSet<>();
        for(String rawPath:requestedPaths) {
            String path=normalizeSelectionPath(rawPath);
            if(path.isEmpty()) throw new ProjectException(ProjectException.Reason.INVALID_REQUEST,"The project root cannot be exported as a selection; use full project export instead");
            ProjectTreeNode node=findTreeNode(root,path);
            if(node==null) throw new ProjectException(ProjectException.Reason.NOT_FOUND,"Selected project entry was not found: "+path);
            collectArchiveEntries(node,directoryEntries,fileEntries);
            if(directoryEntries.size()+fileEntries.size()>properties.getMaxArchiveEntries()) throw new ProjectException(ProjectException.Reason.FILE_TOO_LARGE,"The selected project entries exceed the configured archive-entry limit");
        }
        try(ByteArrayOutputStream output=new ByteArrayOutputStream(); ZipOutputStream zip=new ZipOutputStream(output,StandardCharsets.UTF_8)) {
            for(String directory:directoryEntries) {
                ZipEntry entry=new ZipEntry(directory.endsWith("/")?directory:directory+"/");
                zip.putNextEntry(entry); zip.closeEntry();
            }
            long uncompressed=0;
            for(String path:fileEntries) {
                byte[] bytes=service.download(path);
                uncompressed+=bytes.length;
                if(uncompressed>properties.getMaxArchiveSizeBytes()) throw new ProjectException(ProjectException.Reason.FILE_TOO_LARGE,"The selected project entries exceed the configured archive-size limit");
                zip.putNextEntry(new ZipEntry(path)); zip.write(bytes); zip.closeEntry();
                if(output.size()>properties.getMaxArchiveSizeBytes()) throw new ProjectException(ProjectException.Reason.FILE_TOO_LARGE,"The selected project archive exceeds the configured archive-size limit");
            }
            zip.finish();
            byte[] archive=output.toByteArray();
            if(archive.length>properties.getMaxArchiveSizeBytes()) throw new ProjectException(ProjectException.Reason.FILE_TOO_LARGE,"The selected project archive exceeds the configured archive-size limit");
            return archive;
        } catch(ProjectException exception) { throw exception; }
        catch(IOException exception) { throw new ProjectException(ProjectException.Reason.STORAGE_ERROR,"Could not create the selected project archive",exception); }
    }

    /** Descripción: Ejecuta la operación «collectArchiveEntries» correspondiente a la responsabilidad de ProjectController. Entradas: node: dato de entrada necesario para la operación; directories: dato de entrada necesario para la operación; files: archivo o contenido que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void collectArchiveEntries(ProjectTreeNode node,Set<String> directories,Set<String> files) {
        if(node.nodeType()==ProjectNodeType.FILE) { files.add(node.relativePath()); return; }
        if(node.nodeType()==ProjectNodeType.DIRECTORY || node.nodeType()==ProjectNodeType.GROUP) directories.add(node.relativePath());
        for(ProjectTreeNode child:node.children()) collectArchiveEntries(child,directories,files);
    }

    /** Descripción: Obtiene la información correspondiente a «findTreeNode» dentro de ProjectController. Entradas: node: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ProjectTreeNode findTreeNode(ProjectTreeNode node,String path) {
        if(node.relativePath().equals(path)) return node;
        for(ProjectTreeNode child:node.children()) {
            ProjectTreeNode found=findTreeNode(child,path);
            if(found!=null) return found;
        }
        return null;
    }

    /** Descripción: Ejecuta la operación «normalizeSelectionPath» correspondiente a la responsabilidad de ProjectController. Entradas: rawPath: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String normalizeSelectionPath(String rawPath) {
        if(rawPath==null) return "";
        String path=rawPath.replace('\\','/').trim();
        while(path.startsWith("/")) path=path.substring(1);
        while(path.endsWith("/") && !path.isEmpty()) path=path.substring(0,path.length()-1);
        if(path.equals("..") || path.startsWith("../") || path.contains("/../") || path.contains("//")) throw new ProjectException(ProjectException.Reason.INVALID_REQUEST,"Invalid selected project path");
        return path;
    }

    /** Descripción: Ejecuta la operación «deletionService» correspondiente a la responsabilidad de ProjectController. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private ProjectDeletionService deletionService() {
        if(deletionService==null) throw new IllegalStateException("Project deletion service is not available in this compatibility-only controller instance");
        return deletionService;
    }

    /** Descripción: Evalúa la condición «isSameComputerRequest» sin modificar el estado externo. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isSameComputerRequest(HttpServletRequest request) {
        try {
            InetAddress remote=InetAddress.getByName(request.getRemoteAddr());
            if(remote.isAnyLocalAddress() || remote.isLoopbackAddress()) return true;
            InetAddress local=InetAddress.getByName(request.getLocalAddr());
            if(remote.equals(local)) return true;
            Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();
            while(interfaces!=null && interfaces.hasMoreElements()){
                Enumeration<InetAddress> addresses=interfaces.nextElement().getInetAddresses();
                while(addresses.hasMoreElements()) if(remote.equals(addresses.nextElement())) return true;
            }
            return false;
        } catch(Exception exception){
            return false;
        }
    }

}
