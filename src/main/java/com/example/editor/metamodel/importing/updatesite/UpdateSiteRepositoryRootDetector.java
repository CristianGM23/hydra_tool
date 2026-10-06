package com.example.editor.metamodel.importing.updatesite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Detects likely p2/update-site roots inside ordinary Git repositories without
 * executing build logic. The scan is bounded and deterministic.
 */
@Component
public class UpdateSiteRepositoryRootDetector {
    private static final int MAX_DEPTH = 8;
    private static final int MAX_ROOTS = 24;
    private static final long MAX_DESCRIPTOR_BYTES = 512 * 1024L;
    private final UpdateSiteImportProperties properties;

    @Autowired
    /** Descripción: Inicializa el detector con los mismos límites de cardinalidad que gobiernan la importación de repositorios. Entradas: properties: configuración del importador. Salida: deja el detector preparado para un recorrido acotado. */
    /** Descripción: Inicializa una instancia de UpdateSiteRepositoryRootDetector con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteRepositoryRootDetector(UpdateSiteImportProperties properties) { this.properties = properties; }

    /** Descripción: Inicializa una instancia compatible para pruebas focales usando límites de importación por defecto. Entradas: no requiere parámetros. Salida: deja el detector listo con configuración conservadora. */
    /** Descripción: Inicializa una instancia de UpdateSiteRepositoryRootDetector con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteRepositoryRootDetector() { this(new UpdateSiteImportProperties()); }

    /** Descripción: Detecta raíces p2 recorriendo incrementalmente el repositorio hasta la profundidad y cardinalidad configuradas, sin ordenar/materializar todas las rutas visitadas. Entradas: repositoryRoot: raíz del repositorio. Salida: devuelve candidatos deterministas por puntuación o un diagnóstico explícito si se excede el presupuesto. */ public List<UpdateSiteDetectedRoot> detect(Path repositoryRoot) {
        Map<Path,RootScore> scores = new LinkedHashMap<>();
        int maximumFiles=Math.max(1,properties.getMaxEntries());int visited=0;
        try(var stream = Files.walk(repositoryRoot, MAX_DEPTH)) {
            var iterator=stream.iterator();
            while(iterator.hasNext()) {
                Path file=iterator.next();
                if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) continue;
                if(++visited>maximumFiles) return List.of(new UpdateSiteDetectedRoot("",0,List.of("Root discovery exceeded the configured file limit of "+maximumFiles+" entries."),true));
                inspect(repositoryRoot,file,scores);
            }
        } catch(IOException exception) {
            return List.of(new UpdateSiteDetectedRoot("",0,List.of("Root discovery could not complete: "+exception.getMessage()),true));
        }
        List<Map.Entry<Path,RootScore>> ordered = scores.entrySet().stream()
                .filter(entry -> entry.getValue().score > 0)
                .sorted(Comparator.<Map.Entry<Path,RootScore>>comparingInt(entry -> entry.getValue().score).reversed()
                        .thenComparing(entry -> relative(repositoryRoot,entry.getKey())))
                .limit(MAX_ROOTS).toList();
        if(ordered.isEmpty()) return List.of(new UpdateSiteDetectedRoot("",0,List.of("No explicit p2 root marker found; analyze the repository root."),true));
        int best = ordered.getFirst().getValue().score;
        return ordered.stream().map(entry -> new UpdateSiteDetectedRoot(relative(repositoryRoot,entry.getKey()),entry.getValue().score,
                entry.getValue().markers.stream().sorted().toList(),entry.getValue().score==best)).toList();
    }

    /** Descripción: Ejecuta la operación «inspect» correspondiente a la responsabilidad de UpdateSiteRepositoryRootDetector. Entradas: repositoryRoot: dato de entrada necesario para la operación; file: archivo o contenido que debe procesarse; scores: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void inspect(Path repositoryRoot,Path file,Map<Path,RootScore> scores) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        Path parent = file.getParent();
        if(parent == null) return;
        if(name.equals("content.xml") || name.equals("content.jar")) add(scores,parent,10,name);
        else if(name.equals("artifacts.xml") || name.equals("artifacts.jar")) add(scores,parent,10,name);
        else if(name.equals("category.xml")) add(scores,parent,9,name);
        else if(name.equals("site.xml")) add(scores,parent,8,name);
        else if(name.equals("feature.xml")) add(scores,featureRoot(repositoryRoot,parent),5,name);
        else if(name.endsWith(".target")) add(scores,parent,3,name);
        else if(name.endsWith(".product")) add(scores,parent,2,name);
        else if(name.equals("pom.xml") && looksLikeP2Build(file)) add(scores,parent,7,"Tycho/eclipse-repository pom.xml");
        else if(name.equals("build.gradle") || name.equals("build.gradle.kts")) {
            if(containsSmall(file,"eclipse") || containsSmall(file,"p2")) add(scores,parent,3,"Eclipse-oriented Gradle build");
        }
    }

    /** Descripción: Ejecuta la operación «featureRoot» correspondiente a la responsabilidad de UpdateSiteRepositoryRootDetector. Entradas: repositoryRoot: dato de entrada necesario para la operación; parent: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Path featureRoot(Path repositoryRoot,Path parent) {
        Path cursor = parent;
        while(cursor != null && cursor.startsWith(repositoryRoot)) {
            if(cursor.getFileName()!=null && "features".equalsIgnoreCase(cursor.getFileName().toString())) return cursor.getParent()==null?repositoryRoot:cursor.getParent();
            cursor = cursor.getParent();
        }
        return parent;
    }

    /** Descripción: Ejecuta la operación «looksLikeP2Build» correspondiente a la responsabilidad de UpdateSiteRepositoryRootDetector. Entradas: file: archivo o contenido que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean looksLikeP2Build(Path file) {
        return containsSmall(file,"eclipse-repository") || containsSmall(file,"tycho-p2") || containsSmall(file,"p2-repository");
    }

    /** Descripción: Evalúa la condición «containsSmall» sin modificar el estado externo. Entradas: file: archivo o contenido que debe procesarse; token: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean containsSmall(Path file,String token) {
        try {
            return com.example.editor.io.BoundedIo.readUtf8(file,MAX_DESCRIPTOR_BYTES).toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT));
        } catch(IOException ignored) { return false; }
    }

    /** Descripción: Ejecuta la operación «add» correspondiente a la responsabilidad de UpdateSiteRepositoryRootDetector. Entradas: scores: dato de entrada necesario para la operación; root: ruta o ubicación utilizada por la operación; score: dato de entrada necesario para la operación; marker: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void add(Map<Path,RootScore> scores,Path root,int score,String marker) {
        RootScore value=scores.computeIfAbsent(root.normalize(),ignored->new RootScore());
        value.score+=score;value.markers.add(marker);
    }

    /** Descripción: Ejecuta la operación «relative» correspondiente a la responsabilidad de UpdateSiteRepositoryRootDetector. Entradas: root: ruta o ubicación utilizada por la operación; candidate: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String relative(Path root,Path candidate) {
        String value=root.relativize(candidate).toString().replace('\\','/');
        return value.equals(".")?"":value;
    }

    private static final class RootScore {
        int score;
        final Set<String> markers=new LinkedHashSet<>();
    }
}
