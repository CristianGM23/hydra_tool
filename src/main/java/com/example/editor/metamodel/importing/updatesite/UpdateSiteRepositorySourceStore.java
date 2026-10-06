package com.example.editor.metamodel.importing.updatesite;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import com.example.editor.io.AtomicFiles;
import org.springframework.stereotype.Component;

/**
 * Disk-backed repository snapshot store.
 *
 * <p>Snapshots are directories rather than one giant in-memory ZIP. Each source
 * file is copied as a stream and later exposed as a staged {@link UpdateSiteInputFile},
 * so analysis can reopen files without materializing the complete repository in heap.</p>
 */
@Component
public class UpdateSiteRepositorySourceStore {
    private static final int BUFFER_SIZE=64*1024;
    private final UpdateSiteImportProperties properties;
    /** Descripción: Inicializa una instancia de UpdateSiteRepositorySourceStore con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteRepositorySourceStore(UpdateSiteImportProperties properties){this.properties=properties;}

    /** Descripción: Actualiza el estado gestionado por UpdateSiteRepositorySourceStore mediante la operación «save». Entradas: inputs: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public String save(List<UpdateSiteInputFile> inputs){
        if(inputs==null||inputs.isEmpty())throw new IllegalArgumentException("Repository snapshot requires at least one file");
        String id="repo-"+UUID.randomUUID();Path target=path(id);Path temporary=target.resolveSibling(target.getFileName()+".tmp-"+UUID.randomUUID());
        long total=0;
        try{
            Files.createDirectories(temporary);
            if(inputs.size()>properties.getMaxEntries())throw new IllegalArgumentException("Repository snapshot exceeds the configured file limit");
            List<UpdateSiteInputFile> ordered=new ArrayList<>(inputs);
            ordered.sort(Comparator.comparing(UpdateSiteInputFile::path));
            for(UpdateSiteInputFile input:ordered){
                String relative=normalize(input.path());Path destination=temporary.resolve(relative).normalize();
                if(!destination.startsWith(temporary))throw new IllegalArgumentException("Repository snapshot path escapes the snapshot root");
                if(destination.getParent()!=null)Files.createDirectories(destination.getParent());
                long copied=copy(input,destination,properties.getMaxEntryBytes());
                total=checkedAdd(total,copied,"Repository snapshot exceeds the configured import size limit");
                if(total>properties.getMaxUncompressedBytes())throw new IllegalArgumentException("Repository snapshot exceeds the configured import size limit");
            }
            AtomicFiles.moveNew(temporary,target);
            pruneSnapshots();
            return id;
        }catch(IOException|RuntimeException exception){deleteTree(temporary);if(exception instanceof IllegalArgumentException illegal)throw illegal;throw new IllegalArgumentException("Could not persist repository import snapshot",exception);}
    }

    /** Descripción: Obtiene la información correspondiente a «load» dentro de UpdateSiteRepositorySourceStore. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public List<UpdateSiteInputFile> load(String id){
        Path source=path(id);if(!Files.isDirectory(source))throw new IllegalArgumentException("Repository import snapshot was not found: "+id);
        List<UpdateSiteInputFile> result=new ArrayList<>();long total=0;
        try(var stream=Files.walk(source)){
            var iterator=stream.iterator();
            while(iterator.hasNext()){
                Path file=iterator.next();
                if(Files.isSymbolicLink(file))throw new IllegalArgumentException("Repository snapshot contains an unexpected symbolic link: "+source.relativize(file));
                if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))continue;
                if(result.size()>=properties.getMaxEntries())throw new IllegalArgumentException("Repository snapshot exceeds the configured file limit");
                String relative=source.relativize(file).toString().replace('\\','/');
                long size=Files.size(file);
                if(size>properties.getMaxEntryBytes())throw new IllegalArgumentException("Repository file exceeds the configured per-file limit: "+relative);
                total=checkedAdd(total,size,"Repository snapshot exceeds the configured import size limit");
                if(total>properties.getMaxUncompressedBytes())throw new IllegalArgumentException("Repository snapshot exceeds the configured import size limit");
                result.add(UpdateSiteInputFile.staged(relative,file,size,false));
            }
        }catch(IOException exception){throw new IllegalArgumentException("Could not read repository import snapshot",exception);}
        result.sort(Comparator.comparing(UpdateSiteInputFile::path));
        return List.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «copy» correspondiente a la responsabilidad de UpdateSiteRepositorySourceStore. Entradas: input: valor de entrada que debe procesarse; destination: dato de entrada necesario para la operación; maximum: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private long copy(UpdateSiteInputFile input,Path destination,long maximum)throws IOException{
        long total=0;byte[] buffer=new byte[BUFFER_SIZE];
        try(InputStream source=input.openStream();OutputStream output=Files.newOutputStream(destination,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){
            int read;while((read=source.read(buffer))>=0){if(read==0)continue;total=checkedAdd(total,read,"Repository file exceeds the configured per-file limit: "+input.path());if(total>maximum)throw new IllegalArgumentException("Repository file exceeds the configured per-file limit: "+input.path());output.write(buffer,0,read);}
        }
        return total;
    }

    /** Descripción: Suma dos contadores de bytes detectando overflow antes de aplicar los límites configurados. Entradas: left y right: cantidades que se acumulan; message: mensaje de error a conservar. Salida: devuelve la suma segura o rechaza una acumulación que exceda el rango de long. */ private long checkedAdd(long left,long right,String message){try{return Math.addExact(left,right);}catch(ArithmeticException overflow){throw new IllegalArgumentException(message,overflow);}}

    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de UpdateSiteRepositorySourceStore. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String normalize(String value){String path=value==null?"":value.replace('\\','/').trim();if(path.isBlank()||path.startsWith("/")||path.contains("../")||path.equals("..")||path.indexOf('\0')>=0)throw new IllegalArgumentException("Unsafe repository snapshot path: "+value);Path normalized=Path.of(path).normalize();if(normalized.isAbsolute()||normalized.startsWith(".."))throw new IllegalArgumentException("Unsafe repository snapshot path: "+value);return normalized.toString().replace('\\','/');}
    /** Descripción: Ejecuta la operación «path» correspondiente a la responsabilidad de UpdateSiteRepositorySourceStore. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Path path(String id){if(id==null||!id.matches("repo-[A-Za-z0-9-]{20,}"))throw new IllegalArgumentException("Invalid repository snapshot id");Path root=properties.storageRootPath().resolve("repository-sources").normalize();Path result=root.resolve(id).normalize();if(!result.startsWith(root))throw new IllegalArgumentException("Repository snapshot path escapes storage root");return result;}

    /** Descripción: Ejecuta la operación «pruneSnapshots» correspondiente a la responsabilidad de UpdateSiteRepositorySourceStore. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void pruneSnapshots(){
        Path root=properties.storageRootPath().resolve("repository-sources").normalize();
        if(!Files.isDirectory(root))return;
        long cutoff=System.currentTimeMillis()-java.time.Duration.ofHours(properties.getRepositorySnapshotRetentionHours()).toMillis();
        try(var stream=Files.list(root)){
            List<Path> snapshots=stream.filter(Files::isDirectory).filter(path->path.getFileName().toString().startsWith("repo-")).sorted((left,right)->Long.compare(lastModified(right),lastModified(left))).toList();
            for(int index=0;index<snapshots.size();index++){Path snapshot=snapshots.get(index);if(index>=properties.getRepositorySnapshotMaxCount()||lastModified(snapshot)<cutoff)deleteTree(snapshot);}
        }catch(IOException ignored){/* Retention cleanup must never make a completed snapshot write fail. */}
    }

    /** Descripción: Ejecuta la operación «lastModified» correspondiente a la responsabilidad de UpdateSiteRepositorySourceStore. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private long lastModified(Path path){try{return Files.getLastModifiedTime(path).toMillis();}catch(IOException ignored){return 0L;}}
    /** Descripción: Elimina o libera el recurso gestionado por «deleteTree» de forma controlada. Entradas: root: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void deleteTree(Path root){try{if(root==null||!Files.exists(root))return;Files.walkFileTree(root,new SimpleFileVisitor<>(){@Override public FileVisitResult visitFile(Path file,BasicFileAttributes attrs)throws IOException{Files.deleteIfExists(file);return FileVisitResult.CONTINUE;}/** Descripción: Ejecuta la operación «postVisitDirectory» correspondiente a la responsabilidad de UpdateSiteRepositorySourceStore. Entradas: dir: dato de entrada necesario para la operación; exc: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public FileVisitResult postVisitDirectory(Path dir,IOException exc)throws IOException{Files.deleteIfExists(dir);return FileVisitResult.CONTINUE;}});}catch(IOException ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/metamodel/importing/updatesite/UpdateSiteRepositorySourceStore.java",ignored);}}
}
