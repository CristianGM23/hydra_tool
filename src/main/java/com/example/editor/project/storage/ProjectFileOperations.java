package com.example.editor.project.storage;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import org.springframework.stereotype.Component;
import com.example.editor.io.AtomicFiles;
import com.example.editor.io.FileSystemBoundaries;
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
            Path realRoot=FileSystemBoundaries.realRoot(path);
            Files.walkFileTree(path,new SimpleFileVisitor<>() {
                /** Descripción: SEC-WIN-JUNCTION: un directorio que resuelve fuera de lo que se borra (unión o punto de montaje de Windows) se elimina como enlace, sin entrar en él ni tocar lo que contiene su destino. Entradas: dir: directorio visitado; attrs: atributos básicos. Salida: salta su subárbol o continúa. */ @Override public FileVisitResult preVisitDirectory(Path dir,BasicFileAttributes attrs) throws IOException {
                    if(!dir.equals(path)&&FileSystemBoundaries.escapes(dir,realRoot)) {
                        Files.delete(dir);
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }
                /** Descripción: Elimina cada fichero visitado sin seguir enlaces simbólicos; si su ruta ya resuelve fuera de lo que se borra no lo toca (TOCTOU: Windows deja renombrar un directorio mientras se enumera y poner una unión en su lugar, y los nombres restantes se resolverían a través de ella). Un fichero regular se fija antes de comprobarlo para que ningún antecesor cambie entre la comprobación y el borrado. Entradas: file: fichero visitado; attrs: atributos básicos. Salida: continúa el recorrido. */ @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attrs) throws IOException {
                    if(!attrs.isRegularFile()) {
                        if(!FileSystemBoundaries.escapes(file,realRoot)) Files.deleteIfExists(file);
                        return FileVisitResult.CONTINUE;
                    }
                    try(Closeable pin=FileSystemBoundaries.pin(file)) {
                        if(!FileSystemBoundaries.escapes(file,realRoot)) Files.deleteIfExists(file);
                    } catch(NoSuchFileException gone) { /* ya no existe: nada que borrar */ }
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
        Path realSource=FileSystemBoundaries.realRoot(source);
        Files.walkFileTree(source,new SimpleFileVisitor<>() {
            /** Descripción: Crea el directorio equivalente antes de visitar sus hijos; rechaza un directorio que resuelve fuera del origen (SEC-WIN-JUNCTION). Entradas: dir: directorio origen; attrs: atributos básicos. Salida: continúa el recorrido. */ @Override public FileVisitResult preVisitDirectory(Path dir,BasicFileAttributes attrs) throws IOException {
                if(FileSystemBoundaries.escapes(dir,realSource)) throw new ProjectException(ProjectException.Reason.INVALID_REQUEST,"Links to directories outside the copied directory are not allowed: "+source.relativize(dir));
                Files.createDirectories(destination.resolve(source.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }
            /** Descripción: Copia un fichero o enlace visitado conservando el enlace como enlace en lugar de seguirlo; rechaza un fichero cuya ruta ya resuelve fuera del origen (TOCTOU: su directorio se sustituyó por una unión después de entrar en él). Un fichero regular se fija antes de comprobarlo para que la copia lea el mismo fichero comprobado. Entradas: file: entrada origen; attrs: atributos básicos. Salida: continúa el recorrido. */ @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attrs) throws IOException {
                Path target=destination.resolve(source.relativize(file).toString());
                if(target.getParent()!=null) Files.createDirectories(target.getParent());
                if(attrs.isSymbolicLink()) { Files.copy(file,target,LinkOption.NOFOLLOW_LINKS,StandardCopyOption.REPLACE_EXISTING); return FileVisitResult.CONTINUE; }
                try(Closeable pin=attrs.isRegularFile()?FileSystemBoundaries.pin(file):() -> {}) {
                    if(FileSystemBoundaries.escapes(file,realSource)) throw new ProjectException(ProjectException.Reason.INVALID_REQUEST,"Links to directories outside the copied directory are not allowed: "+source.relativize(file));
                    Files.copy(file,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.COPY_ATTRIBUTES);
                }
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
