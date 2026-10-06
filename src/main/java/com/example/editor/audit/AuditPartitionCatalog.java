package com.example.editor.audit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** Centraliza el descubrimiento y metadatos de particiones JSONL sin cargar su contenido. */
final class AuditPartitionCatalog {
    private static final String SUFFIX=".jsonl";
    private final Path root;
    /** Descripción: Inicializa una instancia de AuditPartitionCatalog con sus dependencias y datos de entrada. Entradas: root: ruta o ubicación utilizada por la operación. Salida: deja la instancia inicializada y lista para su uso. */ AuditPartitionCatalog(Path root){this.root=root;}

    /** Descripción: Ejecuta la operación «partition» correspondiente a la responsabilidad de AuditPartitionCatalog. Entradas: day: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ Path partition(LocalDate day){return root.resolve(day+SUFFIX).normalize();}
    /** Descripción: Ejecuta la operación «day» correspondiente a la responsabilidad de AuditPartitionCatalog. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ LocalDate day(Path path){
        String name=path.getFileName().toString();
        if(!name.endsWith(SUFFIX)) return null;
        try{return LocalDate.parse(name.substring(0,name.length()-SUFFIX.length()));}
        catch(RuntimeException ignored){return null;}
    }
    /** Descripción: Ejecuta la operación «all» correspondiente a la responsabilidad de AuditPartitionCatalog. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ List<Path> all(){
        if(!Files.isDirectory(root)) return List.of();
        try(var files=Files.list(root)){
            return files.filter(Files::isRegularFile).filter(path->day(path)!=null)
                    .sorted(Comparator.comparing(path->path.getFileName().toString())).toList();
        }catch(IOException exception){throw new AuditStorageException("Could not list audit partitions: "+root,exception);}
    }
    /** Descripción: Ejecuta la operación «storageBytes» correspondiente a la responsabilidad de AuditPartitionCatalog. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ long storageBytes(){
        long total=0L;
        for(Path path:all()){
            try{total=Math.addExact(total,Files.size(path));}
            catch(ArithmeticException exception){return Long.MAX_VALUE;}
            catch(IOException exception){throw new AuditStorageException("Could not inspect audit partition: "+path,exception);}
        }
        return total;
    }
    /** Descripción: Ejecuta la operación «count» correspondiente a la responsabilidad de AuditPartitionCatalog. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ int count(){return all().size();}
}
