package com.example.editor.project.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;
import com.example.editor.project.LocalProjectProperties;
import com.example.editor.project.ProjectException;

/** Resuelve rutas relativas sin permitir escapar de la raíz del proyecto. */
@Component
public class SafeProjectPathResolver {
    private final LocalProjectProperties properties;

    /** Descripción: Inicializa una instancia de SafeProjectPathResolver con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public SafeProjectPathResolver(LocalProjectProperties properties) {
        this.properties=properties;
    }

    /** Descripción: Ejecuta la operación «projectRoot» correspondiente a la responsabilidad de SafeProjectPathResolver. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public Path projectRoot(String value) {
        if(value==null||value.isBlank()) throw invalid("Project directory is required");
        Path root=Path.of(value).toAbsolutePath().normalize();
        verifyNoSymbolicLinks(root,true);
        return root;
    }

    /** Descripción: Obtiene la información correspondiente a «resolve» dentro de SafeProjectPathResolver. Entradas: root: ruta o ubicación utilizada por la operación; relativePath: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ public Path resolve(Path root,String relativePath) {
        if(root==null) throw invalid("Project root is required");
        Path normalizedRoot=root.toAbsolutePath().normalize();
        String safe=relativePath==null?"":relativePath.trim().replace('\\','/');
        if(safe.startsWith("/")||safe.matches("^[A-Za-z]:.*")) throw invalid("Project paths must be relative");
        Path candidate=normalizedRoot.resolve(safe).normalize();
        if(!candidate.startsWith(normalizedRoot)) throw invalid("Path escapes the project root");
        verifyNoSymbolicLinks(candidate,false);
        verifyRealContainment(normalizedRoot,candidate);
        return candidate;
    }

    /** Descripción: Ejecuta la operación «relative» correspondiente a la responsabilidad de SafeProjectPathResolver. Entradas: root: ruta o ubicación utilizada por la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ public String relative(Path root,Path path) {
        Path normalizedRoot=root.toAbsolutePath().normalize();
        Path normalizedPath=path.toAbsolutePath().normalize();
        if(!normalizedPath.startsWith(normalizedRoot)) throw invalid("Path is outside the project root");
        return normalizedRoot.relativize(normalizedPath).toString().replace('\\','/');
    }

    /** Descripción: Valida las condiciones necesarias para «verifyNoSymbolicLinks» y rechaza entradas no válidas. Entradas: path: ruta o ubicación utilizada por la operación; includeMissingTail: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void verifyNoSymbolicLinks(Path path,boolean includeMissingTail) {
        if(properties.isAllowSymbolicLinks()) return;
        Path current=path.getRoot();
        for(Path part:path) {
            current=current==null?part:current.resolve(part);
            boolean exists=Files.exists(current,java.nio.file.LinkOption.NOFOLLOW_LINKS);
            if(exists&&Files.isSymbolicLink(current)) throw invalid("Symbolic links are disabled for local projects");
            if(!exists&&includeMissingTail) break;
        }
    }

    /** Descripción: Valida las condiciones necesarias para «verifyRealContainment» y rechaza entradas no válidas. Entradas: root: ruta o ubicación utilizada por la operación; candidate: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void verifyRealContainment(Path root,Path candidate) {
        if(!properties.isAllowSymbolicLinks()||!Files.exists(root)) return;
        try {
            Path realRoot=root.toRealPath();
            Path existing=candidate;
            while(existing!=null&&!Files.exists(existing)) existing=existing.getParent();
            if(existing!=null&&!existing.toRealPath().startsWith(realRoot)) throw invalid("Symbolic link escapes the project root");
        } catch(IOException exception) {
            throw new ProjectException(ProjectException.Reason.STORAGE_ERROR,"Could not resolve the real project path",exception);
        }
    }

    /** Descripción: Ejecuta la operación «invalid» correspondiente a la responsabilidad de SafeProjectPathResolver. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ProjectException invalid(String message) {
        return new ProjectException(ProjectException.Reason.INVALID_REQUEST,message);
    }
}
