package com.example.editor.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Operaciones locales de publicación atómica con fallback seguro cuando el proveedor NIO rechaza ATOMIC_MOVE. */
public final class AtomicFiles {
    /** Descripción: Impide instanciar la utilidad AtomicFiles; todas las operaciones se exponen mediante métodos estáticos. Entradas: no requiere parámetros. Salida: no devuelve valor. */ private AtomicFiles() {}

    /** Descripción: Escribe un documento completo mediante un temporal en el mismo directorio y lo publica sustituyendo el destino de forma atómica cuando el filesystem lo permite. Entradas: target: fichero de destino; content: bytes a persistir. Salida: no devuelve valor; garantiza la eliminación del temporal incluso si la publicación falla. */
    /** Descripción: Actualiza el estado gestionado por AtomicFiles mediante la operación «writeReplacing». Entradas: target: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public static void writeReplacing(Path target, byte[] content) throws IOException {
        if(target==null) throw new IllegalArgumentException("target is required");
        byte[] bytes=content==null?new byte[0]:content;
        Path normalized=target.toAbsolutePath().normalize();
        Path parent=normalized.getParent();
        if(parent==null) throw new IllegalArgumentException("target must have a parent directory");
        Files.createDirectories(parent);
        Path temporary=Files.createTempFile(parent,"."+normalized.getFileName()+"-", ".tmp");
        try {
            Files.write(temporary,bytes,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING);
            moveReplacing(temporary,normalized);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Descripción: Copia un fichero en streaming a un temporal del directorio destino y lo publica de forma atómica, abortando si supera el presupuesto indicado. Entradas: source: fichero origen; target: destino; maximumBytes: máximo de bytes admitidos. Salida: devuelve el número de bytes copiados y elimina siempre el temporal. */
    /** Descripción: Ejecuta la operación «copyReplacingBounded» correspondiente a la responsabilidad de AtomicFiles. Entradas: source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; maximumBytes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static long copyReplacingBounded(Path source,Path target,long maximumBytes) throws IOException {
        if(source==null||target==null)throw new IllegalArgumentException("source and target are required");
        if(maximumBytes<0)throw new IllegalArgumentException("maximumBytes must not be negative");
        Path normalized=target.toAbsolutePath().normalize();Path parent=normalized.getParent();
        if(parent==null)throw new IllegalArgumentException("target must have a parent directory");
        Files.createDirectories(parent);Path temporary=Files.createTempFile(parent,"."+normalized.getFileName()+"-", ".tmp");
        long total=0;
        try(InputStream input=Files.newInputStream(source,java.nio.file.LinkOption.NOFOLLOW_LINKS);OutputStream output=Files.newOutputStream(temporary,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)){
            byte[] buffer=new byte[64*1024];int read;
            while((read=input.read(buffer))>=0){if(read==0)continue;try{total=Math.addExact(total,read);}catch(ArithmeticException exception){throw new IOException("Source size overflow",exception);}if(total>maximumBytes)throw new IOException("Source exceeds maximum of "+maximumBytes+" bytes");output.write(buffer,0,read);}
        }catch(IOException exception){Files.deleteIfExists(temporary);throw exception;}
        try{moveReplacing(temporary,normalized);return total;}finally{Files.deleteIfExists(temporary);}
    }

    /**
     * Publica un fichero sustituyendo el destino. Primero intenta ATOMIC_MOVE; algunos
     * proveedores (especialmente Windows) comunican el rechazo de esa opción mediante
     * IOException/FileSystemException en vez de AtomicMoveNotSupportedException. En ese
     * caso se intenta una única publicación convencional con REPLACE_EXISTING. Si esta
     * también falla se propaga su excepción conservando como suppressed el fallo atómico.
     */
    /** Descripción: Ejecuta la operación «moveReplacing» correspondiente a la responsabilidad de AtomicFiles. Entradas: source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public static void moveReplacing(Path source, Path target) throws IOException {
        boolean sourceExisted=existsNoFollow(source);
        try {
            Files.move(source,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } catch(AtomicMoveNotSupportedException atomicFailure) {
            moveReplacingFallback(source,target,atomicFailure);
        } catch(IOException atomicFailure) {
            if(sourceExisted&&!existsNoFollow(source)&&existsNoFollow(target)) return;
            moveReplacingFallback(source,target,atomicFailure);
        }
    }

    /**
     * Publica un fichero o directorio nuevo sin sustituir un destino previo. Primero
     * intenta ATOMIC_MOVE y, si el proveedor rechaza la modalidad atómica mediante una
     * IOException genérica, repite sin ATOMIC_MOVE. El fallback nunca usa
     * REPLACE_EXISTING y por tanto conserva la semántica create-only.
     */
    /** Descripción: Ejecuta la operación «moveNew» correspondiente a la responsabilidad de AtomicFiles. Entradas: source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public static void moveNew(Path source, Path target) throws IOException {
        if(existsNoFollow(target)) throw new FileAlreadyExistsException(target.toString());
        boolean sourceExisted=existsNoFollow(source);
        try {
            Files.move(source,target,StandardCopyOption.ATOMIC_MOVE);
        } catch(AtomicMoveNotSupportedException atomicFailure) {
            moveNewFallback(source,target,atomicFailure);
        } catch(IOException atomicFailure) {
            if(sourceExisted&&!existsNoFollow(source)&&existsNoFollow(target)) return;
            moveNewFallback(source,target,atomicFailure);
        }
    }

    /** Realiza el reemplazo convencional sin ocultar el fallo que originó el fallback. */
    /** Descripción: Ejecuta la operación «moveReplacingFallback» correspondiente a la responsabilidad de AtomicFiles. Entradas: source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; atomicFailure: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void moveReplacingFallback(Path source,Path target,IOException atomicFailure)throws IOException{
        try{Files.move(source,target,StandardCopyOption.REPLACE_EXISTING);}
        catch(IOException replacementFailure){replacementFailure.addSuppressed(atomicFailure);throw replacementFailure;}
    }

    /** Realiza una publicación create-only convencional sin convertirla en overwrite. */
    /** Descripción: Ejecuta la operación «moveNewFallback» correspondiente a la responsabilidad de AtomicFiles. Entradas: source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; atomicFailure: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void moveNewFallback(Path source,Path target,IOException atomicFailure)throws IOException{
        try{Files.move(source,target);}
        catch(IOException replacementFailure){replacementFailure.addSuppressed(atomicFailure);throw replacementFailure;}
    }

    /** Consulta existencia sin seguir enlaces para razonar sobre el resultado de una publicación fallida. */
    /** Descripción: Evalúa la condición «existsNoFollow» sin modificar el estado externo. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean existsNoFollow(Path path){return path!=null&&Files.exists(path,LinkOption.NOFOLLOW_LINKS);}
}
