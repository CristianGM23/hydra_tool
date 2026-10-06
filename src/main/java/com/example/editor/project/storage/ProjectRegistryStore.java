package com.example.editor.project.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.example.editor.project.LocalProjectProperties;
import com.example.editor.project.ProjectException;
import com.example.editor.project.ProjectSummary;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Conserva proyectos recientes y la última sesión sin mezclarlo con el proyecto portable. */
@Component
public class ProjectRegistryStore {
    private final LocalProjectProperties properties;
    private final ObjectMapper mapper;
    private final ProjectFileOperations files;
    /** Descripción: Inicializa una instancia de ProjectRegistryStore con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; mapper: componente de conversión utilizado durante el procesamiento; files: archivo o contenido que debe procesarse. Salida: deja la instancia inicializada y lista para su uso. */ public ProjectRegistryStore(LocalProjectProperties properties,ObjectMapper mapper,ProjectFileOperations files) { this.properties=properties; this.mapper=mapper; this.files=files; }

    /** Descripción: Obtiene la información correspondiente a «load» dentro de ProjectRegistryStore. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public RegistryDocument load() {
        Path path=path();
        if(!Files.isRegularFile(path)) return new RegistryDocument("1.0","",List.of(),Map.of());
        try { return mapper.readValue(com.example.editor.io.BoundedIo.readInternal(path),RegistryDocument.class); }
        catch(JacksonException | IllegalArgumentException exception) { throw new ProjectException(ProjectException.Reason.STORAGE_ERROR,"Could not parse the local project registry",exception); }
        catch(IOException exception) { throw new ProjectException(ProjectException.Reason.STORAGE_ERROR,"Could not read the local project registry",exception); }
    }

    /** Descripción: Ejecuta la operación «remember» correspondiente a la responsabilidad de ProjectRegistryStore. Entradas: directory: ruta o ubicación utilizada por la operación; id: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación; active: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void remember(String directory,String id,String name,boolean active) {
        RegistryDocument current=load();
        List<String> recents=new ArrayList<>();
        recents.add(directory);
        for(String item:current.recentDirectories()) if(!item.equalsIgnoreCase(directory) && recents.size()<Math.max(1,properties.getRecentProjectLimit())) recents.add(item);
        Map<String,RegistryEntry> entries=new LinkedHashMap<>(current.entries());
        entries.put(directory,new RegistryEntry(id,name,Instant.now()));
        save(new RegistryDocument("1.0",active?directory:current.activeDirectory(),recents,entries));
    }

    /** Descripción: Elimina o libera el recurso gestionado por «closeActive» de forma controlada. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void closeActive() {
        RegistryDocument current=load();
        save(new RegistryDocument(current.schemaVersion(),"",current.recentDirectories(),current.entries()));
    }

    /** Elimina una raíz de recientes/entradas después de borrar físicamente el proyecto. */
    /** Descripción: Ejecuta la operación «forget» correspondiente a la responsabilidad de ProjectRegistryStore. Entradas: directory: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void forget(String directory) {
        if(directory==null || directory.isBlank()) return;
        RegistryDocument current=load();
        List<String> recents=current.recentDirectories().stream().filter(item->!item.equalsIgnoreCase(directory)).toList();
        Map<String,RegistryEntry> entries=new LinkedHashMap<>(current.entries());
        entries.keySet().removeIf(item->item.equalsIgnoreCase(directory));
        String active=current.activeDirectory().equalsIgnoreCase(directory)?"":current.activeDirectory();
        save(new RegistryDocument(current.schemaVersion(),active,recents,entries));
    }

    /** Descripción: Ejecuta la operación «summaries» correspondiente a la responsabilidad de ProjectRegistryStore. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<ProjectSummary> summaries() {
        RegistryDocument current=load();
        List<ProjectSummary> result=new ArrayList<>();
        for(String directory:current.recentDirectories()) {
            RegistryEntry entry=current.entries().get(directory);
            String id=entry==null?directory:entry.id();
            String name=entry==null?Path.of(directory).getFileName().toString():entry.name();
            Instant opened=entry==null?null:entry.lastOpenedAt();
            result.add(new ProjectSummary(id,name,directory,opened,directory.equals(current.activeDirectory()),Files.isDirectory(Path.of(directory))));
        }
        return List.copyOf(result);
    }

    /** Descripción: Actualiza el estado gestionado por ProjectRegistryStore mediante la operación «save». Entradas: document: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void save(RegistryDocument document) {
        try { files.writeAtomic(path(),mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(document)); }
        catch(JacksonException exception) { throw new ProjectException(ProjectException.Reason.STORAGE_ERROR,"Could not serialize the local project registry",exception); }
    }
    /** Descripción: Ejecuta la operación «path» correspondiente a la responsabilidad de ProjectRegistryStore. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Path path() { return properties.getRegistryDirectory().toAbsolutePath().normalize().resolve("project-registry.json"); }
    public record RegistryEntry(String id,String name,Instant lastOpenedAt) { }
    public record RegistryDocument(String schemaVersion,String activeDirectory,List<String> recentDirectories,Map<String,RegistryEntry> entries) {
        public RegistryDocument {
            schemaVersion=schemaVersion==null?"1.0":schemaVersion;
            activeDirectory=activeDirectory==null?"":activeDirectory;
            recentDirectories=recentDirectories==null?List.of():List.copyOf(recentDirectories);
            entries=entries==null?Map.of():Map.copyOf(entries);
        }
    }
}
