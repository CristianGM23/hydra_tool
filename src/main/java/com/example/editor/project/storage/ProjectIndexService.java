package com.example.editor.project.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.example.editor.project.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Construye los árboles lógico y físico y persiste un índice regenerable. */
@Component
public class ProjectIndexService {
    private final SafeProjectPathResolver paths;
    private final ProjectArtifactClassifier classifier;
    private final ProjectMediaTypeResolver mediaTypes;
    private final ProjectHashService hashes;
    private final LocalProjectProperties properties;
    private final ProjectFileOperations files;
    private final ObjectMapper mapper;
    private static final int MAX_HASH_CACHE_ENTRIES = 20_000;
    private final Map<Path, CachedHash> hashCache = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<Path, CachedHash>(256, .75f, true) {
                /** Descripción: Elimina o libera el recurso gestionado por «removeEldestEntry» de forma controlada. Entradas: eldest: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ @Override protected boolean removeEldestEntry(Map.Entry<Path, CachedHash> eldest) { return size() > MAX_HASH_CACHE_ENTRIES; }
            });
    private record CachedHash(long size, long modifiedMillis, String etag) {}
    /** Descripción: Inicializa una instancia de ProjectIndexService con sus dependencias y datos de entrada. Entradas: paths: ruta o ubicación utilizada por la operación; classifier: dato de entrada necesario para la operación; mediaTypes: dato de entrada necesario para la operación; hashes: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento; files: archivo o contenido que debe procesarse; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public ProjectIndexService(SafeProjectPathResolver paths,ProjectArtifactClassifier classifier,ProjectMediaTypeResolver mediaTypes,
            ProjectHashService hashes,LocalProjectProperties properties,ProjectFileOperations files,ObjectMapper mapper) {
        this.paths=paths; this.classifier=classifier; this.mediaTypes=mediaTypes; this.hashes=hashes; this.properties=properties; this.files=files; this.mapper=mapper;
    }

    /** Descripción: Crea o prepara el resultado de «build» dentro de ProjectIndexService. Entradas: root: ruta o ubicación utilizada por la operación; descriptor: dato de entrada necesario para la operación; view: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ProjectTreeNode build(Path root,ProjectDescriptor descriptor,ProjectTreeView view) {
        ProjectTreeNode tree = view == ProjectTreeView.PHYSICAL ? physicalRoot(root,descriptor) : logicalRoot(root,descriptor);
        return tree;
    }

    /** Descripción: Ejecuta la operación «artifacts» correspondiente a la responsabilidad de ProjectIndexService con profundidad y cardinalidad acotadas. Entradas: root: ruta del proyecto; descriptor: descriptor activo. Salida: devuelve artefactos ordenados de forma determinista o rechaza árboles que exceden el presupuesto configurado. */ public List<ProjectIndexedArtifact> artifacts(Path root,ProjectDescriptor descriptor) {
        List<ProjectIndexedArtifact> result=new ArrayList<>();int visited=0;int maximum=properties.getMaxTreeEntries();
        try(var stream=Files.walk(root,properties.getMaxTreeDepth())) {
            var iterator=stream.iterator();
            while(iterator.hasNext()) {
                Path path=iterator.next();
                if(!path.equals(root) && ++visited>maximum) throw treeLimit(maximum);
                if(!Files.isRegularFile(path)) continue;
                if(Files.isSymbolicLink(path)&&!properties.isAllowSymbolicLinks()) continue;
                String relative=paths.relative(root,path);
                if(relative.equals(descriptor.layout().internal()+"/project-index.json")) continue;
                Path safePath=paths.resolve(root,relative);
                result.add(indexed(safePath,relative,descriptor.layout()));
            }
        } catch(ProjectException exception){throw exception;}
        catch(IOException exception) { throw storage("Could not scan the project",exception); }
        result.sort(Comparator.comparing(ProjectIndexedArtifact::relativePath));
        return List.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «physicalRoot» correspondiente a la responsabilidad de ProjectIndexService. Entradas: root: ruta o ubicación utilizada por la operación; descriptor: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ProjectTreeNode physicalRoot(Path root,ProjectDescriptor descriptor) {
        TreeBudget budget=new TreeBudget(properties.getMaxTreeEntries());
        return new ProjectTreeNode("project:"+descriptor.id(),descriptor.name(),"",ProjectNodeType.PROJECT,ArtifactKind.PROJECT_DESCRIPTOR,
                FileOwnership.OWNED,ProjectNodeStatus.NORMAL,false,0,descriptor.updatedAt(),"",children(root,root,descriptor.layout(),Set.of(),0,budget));
    }

    /** Descripción: Ejecuta la operación «logicalRoot» correspondiente a la responsabilidad de ProjectIndexService. Entradas: root: ruta o ubicación utilizada por la operación; descriptor: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ProjectTreeNode logicalRoot(Path root,ProjectDescriptor descriptor) {
        List<ProjectTreeNode> groups=new ArrayList<>();
        TreeBudget budget=new TreeBudget(properties.getMaxTreeEntries());
        Map<String,String> logical=new LinkedHashMap<>(descriptor.layout().logicalRoots(descriptor.enabledCapabilities()));
        Map<String,String> allKnown=descriptor.layout().logicalRoots(EnumSet.allOf(ProjectCapability.class));
        for(Map.Entry<String,String> candidate:allKnown.entrySet()) {
            if(logical.containsValue(candidate.getValue())) continue;
            Path inactiveDirectory=paths.resolve(root,candidate.getValue());
            if(hasVisibleContent(inactiveDirectory)) logical.put(candidate.getKey()+" (inactive)",candidate.getValue());
        }
        Set<String> allRoots=new HashSet<>(logical.values());
        for(Map.Entry<String,String> entry:logical.entrySet()) {
            Path directory=paths.resolve(root,entry.getValue());
            Set<String> exclusions=new HashSet<>();
            for(String candidate:allRoots) if(!candidate.equals(entry.getValue()) && candidate.startsWith(entry.getValue()+"/")) exclusions.add(candidate);
            List<ProjectTreeNode> children=Files.isDirectory(directory)?children(root,directory,descriptor.layout(),exclusions,0,budget):List.of();
            groups.add(new ProjectTreeNode("group:"+entry.getValue(),entry.getKey(),entry.getValue(),ProjectNodeType.GROUP,
                    ArtifactKind.GENERIC_FILE,FileOwnership.USER_MANAGED,ProjectNodeStatus.NORMAL,false,0,null,"",children));
        }
        return new ProjectTreeNode("project:"+descriptor.id(),descriptor.name(),"",ProjectNodeType.PROJECT,ArtifactKind.PROJECT_DESCRIPTOR,
                FileOwnership.OWNED,ProjectNodeStatus.NORMAL,false,0,descriptor.updatedAt(),"",groups);
    }


    /** Mantiene visibles en la vista lógica los artefactos preexistentes de una capacidad desactivada sin volver a activarla. */
    /** Descripción: Evalúa la condición «hasVisibleContent» sin modificar el estado externo. Entradas: directory: ruta o ubicación utilizada por la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean hasVisibleContent(Path directory) {
        if(!Files.isDirectory(directory)) return false;
        try(var stream=Files.list(directory)) {
            return stream.anyMatch(path -> !Files.isSymbolicLink(path) || properties.isAllowSymbolicLinks());
        } catch(IOException exception) {
            throw storage("Could not inspect inactive project group " + directory,exception);
        }
    }

    /** Descripción: Ejecuta la operación «children» con un presupuesto global compartido para impedir que un directorio plano o un árbol amplio materialicen colecciones ilimitadas. Entradas: root: raíz del proyecto; directory: directorio actual; layout: disposición; excludedRoots: subárboles excluidos; depth: profundidad; budget: cardinalidad restante. Salida: devuelve hijos deterministas dentro de los límites configurados. */ private List<ProjectTreeNode> children(Path root,Path directory,ProjectLayout layout,Set<String> excludedRoots,int depth,TreeBudget budget) {
        if(depth>properties.getMaxTreeDepth()) return List.of();
        try(var stream=Files.list(directory)) {
            List<Path> entries=new ArrayList<>();var iterator=stream.iterator();
            while(iterator.hasNext()){budget.claim();entries.add(iterator.next());}
            entries.sort(Comparator.comparing((Path path)->path.getFileName().toString(),String.CASE_INSENSITIVE_ORDER).thenComparing(path->path.getFileName().toString()));
            List<ProjectTreeNode> result=new ArrayList<>(entries.size());
            for(Path child:entries) {
                String relative=paths.relative(root,child);
                if(excludedRoots.contains(relative)) continue;
                if(Files.isSymbolicLink(child)&&!properties.isAllowSymbolicLinks()) continue;
                Path safeChild=paths.resolve(root,relative);
                if(Files.isSymbolicLink(safeChild)&&Files.isDirectory(safeChild)) continue;
                if(Files.isDirectory(safeChild)) {
                    result.add(new ProjectTreeNode("dir:"+relative,child.getFileName().toString(),relative,ProjectNodeType.DIRECTORY,
                            ArtifactKind.GENERIC_FILE,classifier.ownership(relative,layout),ProjectNodeStatus.NORMAL,true,0,
                            Files.getLastModifiedTime(safeChild).toInstant(),"",children(root,safeChild,layout,excludedRoots,depth+1,budget)));
                } else if(Files.isRegularFile(safeChild)) result.add(fileNode(safeChild,relative,layout));
            }
            return List.copyOf(result);
        } catch(ProjectException exception){throw exception;}
        catch(IOException exception) { throw storage("Could not list " + directory,exception); }
    }

    /** Descripción: Ejecuta la operación «fileNode» correspondiente a la responsabilidad de ProjectIndexService. Entradas: path: ruta o ubicación utilizada por la operación; relative: dato de entrada necesario para la operación; layout: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ProjectTreeNode fileNode(Path path,String relative,ProjectLayout layout) throws IOException {
        BasicFileAttributes attrs=Files.readAttributes(path,BasicFileAttributes.class);
        ArtifactKind kind=classifier.classify(relative,layout);
        FileOwnership ownership=classifier.ownership(relative,layout);
        String mediaType=mediaTypes.resolve(path);
        boolean editable=classifier.editable(kind,mediaType,attrs.size(),properties.getMaxEditableFileSizeBytes());
        ProjectNodeStatus status=ownership==FileOwnership.GENERATED?ProjectNodeStatus.GENERATED:ProjectNodeStatus.NORMAL;
        return new ProjectTreeNode("file:"+relative,path.getFileName().toString(),relative,ProjectNodeType.FILE,kind,ownership,status,
                editable,attrs.size(),attrs.lastModifiedTime().toInstant(),etag(path, attrs),List.of());
    }

    /** Descripción: Ejecuta la operación «indexed» correspondiente a la responsabilidad de ProjectIndexService. Entradas: path: ruta o ubicación utilizada por la operación; relative: dato de entrada necesario para la operación; layout: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ProjectIndexedArtifact indexed(Path path,String relative,ProjectLayout layout) {
        try {
            BasicFileAttributes attrs=Files.readAttributes(path,BasicFileAttributes.class);
            ArtifactKind kind=classifier.classify(relative,layout);
            FileOwnership ownership=classifier.ownership(relative,layout);
            String mediaType=mediaTypes.resolve(path);
                return new ProjectIndexedArtifact(relative,kind,ownership,attrs.size(),attrs.lastModifiedTime().toInstant(),etag(path, attrs),
                    classifier.editable(kind,mediaType,attrs.size(),properties.getMaxEditableFileSizeBytes()));
        } catch(IOException exception) { throw storage("Could not index " + relative,exception); }
    }

    /** Descripción: Ejecuta la operación «persistIndex» correspondiente a la responsabilidad de ProjectIndexService. Entradas: root: ruta o ubicación utilizada por la operación; descriptor: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void persistIndex(Path root,ProjectDescriptor descriptor) {
        ProjectIndexDocument index=new ProjectIndexDocument("1.0",descriptor.id(),Instant.now(),artifacts(root,descriptor));
        Path destination=paths.resolve(root,descriptor.layout().internal()+"/project-index.json");
        try { files.writeAtomic(destination,mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(index)); }
        catch(JacksonException exception) { throw storage("Could not serialize the project index",exception); }
    }
    /** Persiste el índice solo cuando una operación lo solicita expresamente. */
    /** Descripción: Persiste el índice completo únicamente cuando una operación lo solicita expresamente. */
    /** Descripción: Ejecuta la operación «persist» correspondiente a la responsabilidad de CachedHash. Entradas: root: ruta o ubicación utilizada por la operación; descriptor: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void persist(Path root, ProjectDescriptor descriptor) { persistIndex(root, descriptor); }

    /** Descripción: Actualiza el índice persistido únicamente para las rutas mutadas y refresca siempre el descriptor. Entradas: root: raíz del proyecto; descriptor: descriptor activo; affectedRelativePaths: rutas modificadas. Salida: no devuelve valor; persiste un índice coherente sin recorrer innecesariamente todo el proyecto. */
    /** Descripción: Ejecuta la operación «persistAffected» correspondiente a la responsabilidad de ProjectIndexService. Entradas: root: ruta o ubicación utilizada por la operación; descriptor: dato de entrada necesario para la operación; affectedRelativePaths: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void persistAffected(Path root, ProjectDescriptor descriptor, String... affectedRelativePaths) {
        Path destination=paths.resolve(root,descriptor.layout().internal()+"/project-index.json");
        if(!Files.isRegularFile(destination)) { persistIndex(root,descriptor); return; }
        try {
            ProjectIndexDocument previous=mapper.readValue(com.example.editor.io.BoundedIo.readInternal(destination),ProjectIndexDocument.class);
            int maximum=properties.getMaxTreeEntries();
            if(previous.artifacts().size()>maximum)throw treeLimit(maximum);
            Map<String,ProjectIndexedArtifact> artifacts=new java.util.TreeMap<>();
            previous.artifacts().forEach(artifact -> artifacts.put(artifact.relativePath(),artifact));
            ensureIndexLimit(artifacts,maximum);
            refreshIndexedPath(root,descriptor,artifacts,ProjectDescriptorStore.FILE_NAME);
            if(affectedRelativePaths!=null) for(String relative:affectedRelativePaths) {
                if(relative==null||relative.isBlank()) continue;
                refreshIndexedPath(root,descriptor,artifacts,relative.replace('\\','/'));
            }
            ProjectIndexDocument next=new ProjectIndexDocument("1.0",descriptor.id(),Instant.now(),List.copyOf(artifacts.values()));
            files.writeAtomic(destination,mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(next));
        } catch(IOException | JacksonException exception) {
            throw storage("Could not update the project index",exception);
        }
    }

    /** Descripción: Quita del índice el estado previo de una ruta y vuelve a indexar solo su subárbol actual. Entradas: root: raíz del proyecto; descriptor: descriptor activo; artifacts: artefactos indexados; relative: ruta afectada. Salida: no devuelve valor; actualiza la colección incremental en memoria. */
    /** Descripción: Ejecuta la operación «refreshIndexedPath» correspondiente a la responsabilidad de ProjectIndexService. Entradas: root: ruta o ubicación utilizada por la operación; descriptor: dato de entrada necesario para la operación; artifacts: dato de entrada necesario para la operación; relative: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void refreshIndexedPath(Path root,ProjectDescriptor descriptor,Map<String,ProjectIndexedArtifact> artifacts,String relative) throws IOException {
        String normalized=relative.replace('\\','/');
        artifacts.keySet().removeIf(candidate -> candidate.equals(normalized)||candidate.startsWith(normalized+"/"));
        Path target=paths.resolve(root,normalized);
        if(Files.isRegularFile(target)) {
            if(!normalized.equals(descriptor.layout().internal()+"/project-index.json")) {
                artifacts.put(normalized,indexed(target,normalized,descriptor.layout()));
                ensureIndexLimit(artifacts,properties.getMaxTreeEntries());
            }
            return;
        }
        if(!Files.isDirectory(target)) return;
        int visited=0;int maximum=properties.getMaxTreeEntries();
        try(var stream=Files.walk(target,properties.getMaxTreeDepth())) {
            var iterator=stream.iterator();
            while(iterator.hasNext()) {
                Path path=iterator.next();
                if(!path.equals(target) && ++visited>maximum) throw treeLimit(maximum);
                if(!Files.isRegularFile(path)) continue;
                String child=paths.relative(root,path);
                if(child.equals(descriptor.layout().internal()+"/project-index.json")) continue;
                artifacts.put(child,indexed(paths.resolve(root,child),child,descriptor.layout()));
                ensureIndexLimit(artifacts,maximum);
            }
        }
    }

    /** Invalida únicamente los hashes de una ruta afectada y sus descendientes. */
    /** Descripción: Invalida del caché los hashes de la ruta afectada y de sus descendientes. */
    /** Descripción: Elimina o libera el recurso gestionado por «invalidate» de forma controlada. Entradas: path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void invalidate(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        synchronized(hashCache) { hashCache.keySet().removeIf(candidate -> candidate.equals(normalized) || candidate.startsWith(normalized)); }
    }

    /** Descripción: Reutiliza el hash si tamaño y fecha no cambiaron y lo recalcula solo cuando es necesario. */
    /** Descripción: Ejecuta la operación «etag» correspondiente a la responsabilidad de CachedHash. Entradas: path: ruta o ubicación utilizada por la operación; attrs: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String etag(Path path, BasicFileAttributes attrs) {
        Path key = path.toAbsolutePath().normalize();
        long modified = attrs.lastModifiedTime().toMillis();
        CachedHash cached = hashCache.get(key);
        if(cached != null && cached.size() == attrs.size() && cached.modifiedMillis() == modified) return cached.etag();
        String etag = hashes.etag(path);
        hashCache.put(key, new CachedHash(attrs.size(), modified, etag));
        return etag;
    }

    /** Descripción: Verifica que el índice incremental completo siga dentro del presupuesto global incluso cuando se actualizan varios subárboles en una sola operación. Entradas: artifacts: mapa actual del índice; maximum: máximo permitido. Salida: no devuelve valor; rechaza el índice cuando supera la cardinalidad configurada. */ private void ensureIndexLimit(Map<String,ProjectIndexedArtifact> artifacts,int maximum){if(artifacts.size()>maximum)throw treeLimit(maximum);}

    /** Descripción: Construye el error estable utilizado cuando un árbol local supera la cardinalidad configurable y evita continuar una exploración potencialmente destructiva. Entradas: maximum: máximo permitido. Salida: devuelve una excepción de almacenamiento con diagnóstico de configuración. */ private ProjectException treeLimit(int maximum){return new ProjectException(ProjectException.Reason.STORAGE_ERROR,"Project tree exceeds configured entry limit of "+maximum);}
    private final class TreeBudget { private final int maximum; private int visited; /** Descripción: Inicializa un presupuesto compartido de cardinalidad para un recorrido completo del árbol. Entradas: maximum: máximo de entradas que puede consumir el recorrido. Salida: deja el contador preparado con un mínimo seguro de una entrada. */ private TreeBudget(int maximum){this.maximum=Math.max(1,maximum);} /** Descripción: Consume una entrada del presupuesto compartido del árbol y rechaza el recorrido cuando se supera el límite. Entradas: no requiere parámetros. Salida: no devuelve valor; puede lanzar la excepción estable de límite. */ private void claim(){if(++visited>maximum)throw treeLimit(maximum);} }

    /** Descripción: Ejecuta la operación «storage» correspondiente a la responsabilidad de ProjectIndexService. Entradas: message: dato de entrada necesario para la operación; cause: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ProjectException storage(String message,Throwable cause) { return new ProjectException(ProjectException.Reason.STORAGE_ERROR,message,cause); }
}
