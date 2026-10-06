package com.example.editor.project.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Determina un media type estable incluso cuando el sistema operativo no lo conoce. */
@Component
public class ProjectMediaTypeResolver {
    /** Descripción: Obtiene la información correspondiente a «resolve» dentro de ProjectMediaTypeResolver. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ public String resolve(Path path) {
        try {
            String probed = Files.probeContentType(path);
            if(probed != null && !probed.isBlank()) return probed;
        } catch(IOException ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/project/storage/ProjectMediaTypeResolver.java",ignored);}
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if(name.endsWith(".json") || name.endsWith(".drawio")) return "application/json";
        if(name.endsWith(".xml") || name.endsWith(".ecore") || name.endsWith(".xmi") || name.endsWith(".drawio.xml")) return "application/xml";
        if(name.endsWith(".md")) return "text/markdown";
        if(name.endsWith(".java") || name.endsWith(".ts") || name.endsWith(".tsx") || name.endsWith(".js") || name.endsWith(".py") || name.endsWith(".puml")) return "text/plain";
        if(name.endsWith(".svg")) return "image/svg+xml";
        if(name.endsWith(".png")) return "image/png";
        if(name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if(name.endsWith(".gif")) return "image/gif";
        if(name.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }
}
