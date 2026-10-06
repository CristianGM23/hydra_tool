package com.example.editor.project.storage;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.example.editor.project.*;

/** Detecta cambios por snapshots portables, sin depender de WatchService o del sistema operativo. */
@Component
public class ProjectChangeTracker {
    private final ProjectIndexService index;
    private final LocalProjectProperties properties;
    private Map<String,ProjectIndexedArtifact> snapshot=Map.of();
    /** Descripción: Inicializa una instancia de ProjectChangeTracker con sus dependencias y datos de entrada. Entradas: index: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public ProjectChangeTracker(ProjectIndexService index,LocalProjectProperties properties) { this.index=index; this.properties=properties; }
    public synchronized void reset(Path root,ProjectDescriptor descriptor) { snapshot=map(index.artifacts(root,descriptor)); }
    public synchronized ProjectChangeSet poll(Path root,ProjectDescriptor descriptor) {
        if(!properties.isChangeDetectionEnabled()) return new ProjectChangeSet(Instant.now(),List.of());
        Map<String,ProjectIndexedArtifact> current=map(index.artifacts(root,descriptor));
        List<ProjectFileChange> changes=new ArrayList<>();
        Instant now=Instant.now();
        for(Map.Entry<String,ProjectIndexedArtifact> entry:current.entrySet()) {
            ProjectIndexedArtifact previous=snapshot.get(entry.getKey());
            if(previous==null) changes.add(new ProjectFileChange(ProjectFileChangeType.CREATED,entry.getKey(),entry.getValue().artifactKind(),now,entry.getValue().etag()));
            else if(!previous.etag().equals(entry.getValue().etag())) changes.add(new ProjectFileChange(ProjectFileChangeType.MODIFIED,entry.getKey(),entry.getValue().artifactKind(),now,entry.getValue().etag()));
        }
        for(Map.Entry<String,ProjectIndexedArtifact> entry:snapshot.entrySet()) if(!current.containsKey(entry.getKey()))
            changes.add(new ProjectFileChange(ProjectFileChangeType.DELETED,entry.getKey(),entry.getValue().artifactKind(),now,""));
        snapshot=current;
        return new ProjectChangeSet(now,changes);
    }
    /** Descripción: Ejecuta la operación «map» correspondiente a la responsabilidad de ProjectChangeTracker. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String,ProjectIndexedArtifact> map(List<ProjectIndexedArtifact> values) {
        Map<String,ProjectIndexedArtifact> result=new LinkedHashMap<>();
        for(ProjectIndexedArtifact value:values) result.put(value.relativePath(),value);
        return Map.copyOf(result);
    }
}
