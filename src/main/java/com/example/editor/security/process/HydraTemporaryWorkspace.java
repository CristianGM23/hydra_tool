package com.example.editor.security.process;

import com.example.editor.observability.HydraBestEffortDiagnostics;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import org.springframework.stereotype.Component;

/** Owns isolated temporary workspaces and guarantees best-effort recursive cleanup. */
@Component
public class HydraTemporaryWorkspace {
    private final HydraExternalProcessPolicy policy;
    /** Descripción: Inicializa una instancia de HydraTemporaryWorkspace con sus dependencias y datos de entrada. Entradas: policy: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public HydraTemporaryWorkspace(HydraExternalProcessPolicy policy){this.policy=policy;}

    /** Descripción: Crea o prepara el resultado de «create» dentro de HydraTemporaryWorkspace. Entradas: prefix: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public Path create(String prefix){
        try{
            Path root=policy.temporaryRoot();
            Files.createDirectories(root);
            policy.rejectSymbolicPath(root);
            return Files.createTempDirectory(root,safePrefix(prefix));
        }catch(IOException exception){throw new IllegalStateException("Could not create isolated external-process workspace",exception);}
    }

    /** Descripción: Valida las condiciones necesarias para «verify» y rechaza entradas no válidas. Entradas: workspace: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void verify(Path workspace){policy.enforceWorkspaceQuota(workspace);}

    /** Descripción: Ejecuta la operación «cleanup» correspondiente a la responsabilidad de HydraTemporaryWorkspace sin materializar el árbol completo. Entradas: workspace: dato de entrada necesario para la operación. Salida: no devuelve valor; registra fallos de limpieza de mejor esfuerzo. */ public void cleanup(Path workspace){
        if(workspace==null||!Files.exists(workspace)) return;
        try{Files.walkFileTree(workspace,new SimpleFileVisitor<>(){
            /** Descripción: Elimina un fichero temporal visitado durante la limpieza. Entradas: file: fichero; attrs: metadatos. Salida: continúa el recorrido. */ @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attrs){try{Files.deleteIfExists(file);}catch(IOException exception){HydraBestEffortDiagnostics.record("HydraTemporaryWorkspace.cleanup",exception);}return FileVisitResult.CONTINUE;}
            /** Descripción: Elimina el directorio temporal después de sus hijos. Entradas: dir: directorio; exc: posible fallo de recorrido. Salida: continúa el recorrido. */ @Override public FileVisitResult postVisitDirectory(Path dir,IOException exc){if(exc!=null)HydraBestEffortDiagnostics.record("HydraTemporaryWorkspace.cleanup",exc);try{Files.deleteIfExists(dir);}catch(IOException exception){HydraBestEffortDiagnostics.record("HydraTemporaryWorkspace.cleanup",exception);}return FileVisitResult.CONTINUE;}
        });}catch(IOException exception){HydraBestEffortDiagnostics.record("HydraTemporaryWorkspace.cleanup",exception);}
    }

    /** Descripción: Ejecuta la operación «safePrefix» correspondiente a la responsabilidad de HydraTemporaryWorkspace. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String safePrefix(String value){
        String normalized=value==null?"hydra-":value.replaceAll("[^A-Za-z0-9._-]","-");
        return normalized.length()<3?"hydra-":normalized.substring(0,Math.min(40,normalized.length()));
    }
}
