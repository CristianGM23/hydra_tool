package com.example.editor.project.storage;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import org.springframework.stereotype.Component;
import com.example.editor.io.AtomicFiles;
import com.example.editor.project.ProjectException;

/** Operaciones reutilizables de copia, borrado y escritura atómica. */
@Component
public class ProjectFileOperations {
    /** Descripción: Actualiza el estado gestionado por ProjectFileOperations mediante la operación «writeAtomic». Entradas: destination: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void writeAtomic(Path destination,byte[] content) {
        try { AtomicFiles.writeReplacing(destination,content); }
        catch(IOException exception) { throw storage("Could not write " + destination,exception); }
    }

    /** Descripción: Ejecuta la operación «copy» correspondiente a la responsabilidad de ProjectFileOperations. Entradas: source: dato de entrada necesario para la operación; destination: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void copy(Path source,Path destination) {
        try {
            if(Files.isDirectory(source,LinkOption.NOFOLLOW_LINKS)) copyDirectory(source,destination);
            else copyEntry(source,destination);
        } catch(IOException exception) { throw storage("Could not copy " + source,exception); }
    }

    /** Descripción: Copia un fichero regular en streaming, con límite explícito y publicación atómica; rechaza enlaces simbólicos. Entradas: source: fichero origen; destination: destino; maximumBytes: presupuesto máximo. Salida: devuelve los bytes copiados. */ public long copyRegularBounded(Path source,Path destination,long maximumBytes) {
        if(source==null||destination==null) throw new ProjectException(ProjectException.Reason.INVALID_REQUEST,"Source and destination are required");
        if(Files.isSymbolicLink(source)||!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS)) throw new ProjectException(ProjectException.Reason.INVALID_REQUEST,"Source must be a regular file without symbolic links");
        try { return AtomicFiles.copyReplacingBounded(source,destination,maximumBytes); }
        catch(IOException exception) { throw storage("Could not copy "+source,exception); }
    }

    /** Descripción: Elimina o libera el recurso gestionado por «deleteRecursively» de forma controlada. Entradas: path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void deleteRecursively(Path path) {
        if(path==null||!Files.exists(path,LinkOption.NOFOLLOW_LINKS)) return;
        try {
            Files.walkFileTree(path,new SimpleFileVisitor<>() {
                /** Descripción: Elimina cada fichero visitado sin seguir enlaces simbólicos. Entradas: file: fichero visitado; attrs: atributos básicos. Salida: continúa el recorrido tras eliminar el fichero. */ @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }
                /** Descripción: Elimina un directorio después de procesar sus hijos y propaga cualquier fallo previo. Entradas: dir: directorio visitado; exception: fallo del recorrido si existe. Salida: continúa el recorrido tras eliminar el directorio. */ @Override public FileVisitResult postVisitDirectory(Path dir,IOException exception) throws IOException {
                    if(exception!=null) throw exception;
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch(IOException exception) { throw storage("Could not delete " + path,exception); }
    }

    /** Descripción: Copia recursivamente un directorio sin materializar todo el árbol en memoria y sin seguir enlaces simbólicos. Entradas: source: raíz origen; destination: raíz destino. Salida: no devuelve valor; preserva la estructura visitada. */ private void copyDirectory(Path source,Path destination) throws IOException {
        Files.walkFileTree(source,new SimpleFileVisitor<>() {
            /** Descripción: Crea el directorio equivalente antes de visitar sus hijos. Entradas: dir: directorio origen; attrs: atributos básicos. Salida: continúa el recorrido. */ @Override public FileVisitResult preVisitDirectory(Path dir,BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(destination.resolve(source.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }
            /** Descripción: Copia un fichero o enlace visitado conservando el enlace como enlace en lugar de seguirlo. Entradas: file: entrada origen; attrs: atributos básicos. Salida: continúa el recorrido. */ @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attrs) throws IOException {
                Path target=destination.resolve(source.relativize(file).toString());
                if(target.getParent()!=null) Files.createDirectories(target.getParent());
                if(attrs.isSymbolicLink()) Files.copy(file,target,LinkOption.NOFOLLOW_LINKS,StandardCopyOption.REPLACE_EXISTING);
                else Files.copy(file,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** Descripción: Copia una entrada individual sin seguir enlaces simbólicos y crea previamente su directorio padre. Entradas: source: entrada origen; destination: destino. Salida: no devuelve valor. */ private void copyEntry(Path source,Path destination) throws IOException {
        if(destination.getParent()!=null) Files.createDirectories(destination.getParent());
        if(Files.isSymbolicLink(source)) Files.copy(source,destination,LinkOption.NOFOLLOW_LINKS,StandardCopyOption.REPLACE_EXISTING);
        else Files.copy(source,destination,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.COPY_ATTRIBUTES);
    }

    /** Descripción: Ejecuta la operación «storage» correspondiente a la responsabilidad de ProjectFileOperations. Entradas: message: dato de entrada necesario para la operación; cause: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ProjectException storage(String message,Throwable cause) { return new ProjectException(ProjectException.Reason.STORAGE_ERROR,message,cause); }
}
