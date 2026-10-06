package com.example.editor.project.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Component;
import com.example.editor.io.AtomicFiles;
import com.example.editor.io.BoundedIo;
import com.example.editor.project.LocalProjectProperties;
import com.example.editor.project.ProjectException;

/** Importa y exporta proyectos ZIP con protección frente a Zip Slip y límites de tamaño. */
@Component
public class ProjectZipService {
    private static final int STREAM_BUFFER_BYTES=64*1024;
    private final LocalProjectProperties properties;
    private final ProjectFileOperations files;

    /** Descripción: Inicializa una instancia de ProjectZipService con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; files: archivo o contenido que debe procesarse. Salida: deja la instancia inicializada y lista para su uso. */ public ProjectZipService(LocalProjectProperties properties,ProjectFileOperations files) {
        this.properties=properties;
        this.files=files;
    }

    /** Descripción: Exporta un proyecto a ZIP con inventario acotado, orden determinista, rechazo de enlaces simbólicos y sin mantener simultáneamente dos copias del archivo completo en memoria. Entradas: root: raíz del proyecto. Salida: devuelve el ZIP si respeta los presupuestos configurados. */ public byte[] exportProject(Path root) {
        Path temporary=null;
        try {
            List<ExportEntry> entries=collectExportEntries(root);
            temporary=Files.createTempFile("hydra-project-export-", ".zip");
            long expanded=0;
            try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(temporary))) {
                for(ExportEntry source:entries) {
                    verifyPortableSource(source.path());
                    ZipEntry entry=new ZipEntry(source.directory()?source.relative()+"/":source.relative());
                    entry.setTime(source.lastModifiedMillis());
                    zip.putNextEntry(entry);
                    if(!source.directory()) expanded=copyExportFile(source.path(),zip,expanded);
                    zip.closeEntry();
                }
                zip.finish();
            }
            long archiveSize=Files.size(temporary);
            if(archiveSize>properties.getMaxArchiveSizeBytes()) throw tooLarge("The project archive exceeds the configured limit");
            return BoundedIo.read(temporary,properties.getMaxArchiveSizeBytes());
        } catch(ProjectException exception) {
            throw exception;
        } catch(IOException exception) {
            throw new ProjectException(ProjectException.Reason.STORAGE_ERROR,"Could not export the project archive",exception);
        } finally {
            if(temporary!=null) try { Files.deleteIfExists(temporary); } catch(IOException ignored) { /* best-effort cleanup of a system temporary file */ }
        }
    }

    /** Descripción: Importa un ZIP en un directorio temporal hermano y publica el proyecto completo solo cuando todas las entradas han superado las validaciones. Entradas: archive: ZIP recibido; destination: destino final nuevo. Salida: no devuelve valor; evita dejar un proyecto parcial ante cualquier error. */ public void importProject(byte[] archive,Path destination) {
        if(archive==null||archive.length==0) throw invalid("The project archive is empty");
        if(archive.length>properties.getMaxArchiveSizeBytes()) throw tooLarge("The project archive exceeds the configured limit");
        Path normalized=destination==null?null:destination.toAbsolutePath().normalize();
        if(normalized==null||normalized.getParent()==null) throw invalid("Project import destination is invalid");
        Path staging=null;
        boolean published=false;
        try {
            Files.createDirectories(normalized.getParent());
            if(Files.exists(normalized,LinkOption.NOFOLLOW_LINKS)) throw new ProjectException(ProjectException.Reason.CONFLICT,"Project import destination already exists");
            staging=Files.createTempDirectory(normalized.getParent(),"."+normalized.getFileName()+"-import-");
            extractArchive(archive,staging);
            AtomicFiles.moveNew(staging,normalized);
            published=true;
        } catch(ProjectException exception) {
            throw exception;
        } catch(IOException exception) {
            throw new ProjectException(ProjectException.Reason.STORAGE_ERROR,"Could not import the project archive",exception);
        } finally {
            if(!published&&staging!=null&&Files.exists(staging,LinkOption.NOFOLLOW_LINKS)) {
                try { files.deleteRecursively(staging); } catch(ProjectException ignored) { /* preserve the original import failure */ }
            }
        }
    }

    /** Descripción: Recorre el proyecto sin seguir enlaces, omite la caché interna y limita número de entradas, profundidad y bytes portables antes de comprimir. Entradas: root: raíz del proyecto. Salida: devuelve un inventario acotado y ordenado de entradas exportables. */ private List<ExportEntry> collectExportEntries(Path root) throws IOException {
        if(root==null||Files.isSymbolicLink(root)||!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS)) throw invalid("Project root must be a real directory");
        int maximumEntries=Math.max(1,properties.getMaxArchiveEntries());
        int maximumDepth=Math.max(1,properties.getMaxTreeDepth());
        long maximumBytes=properties.getMaxArchiveSizeBytes();
        ArrayList<ExportEntry> entries=new ArrayList<>(Math.min(maximumEntries,1024));
        long[] estimatedBytes={0L};
        Files.walkFileTree(root,new SimpleFileVisitor<>() {
            /** Descripción: Valida profundidad y número de directorios y omite por completo la caché interna no portable. Entradas: dir: directorio visitado; attrs: atributos básicos. Salida: continúa o salta el subárbol de caché. */ @Override public FileVisitResult preVisitDirectory(Path dir,BasicFileAttributes attrs) throws IOException {
                if(dir.equals(root)) return FileVisitResult.CONTINUE;
                String relative=portableRelative(root,dir);
                if(relative.equals(".modeling/cache")||relative.startsWith(".modeling/cache/")) return FileVisitResult.SKIP_SUBTREE;
                verifyDepth(relative,maximumDepth);
                addEntry(entries,new ExportEntry(dir,relative,true,0L,attrs.lastModifiedTime().toMillis()),maximumEntries);
                return FileVisitResult.CONTINUE;
            }
            /** Descripción: Añade únicamente ficheros regulares reales, rechazando enlaces y recursos especiales para impedir que el ZIP lea fuera del proyecto. Entradas: file: fichero visitado; attrs: atributos básicos. Salida: continúa el recorrido. */ @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attrs) throws IOException {
                String relative=portableRelative(root,file);
                if(relative.startsWith(".modeling/cache/")) return FileVisitResult.CONTINUE;
                if(attrs.isSymbolicLink()) throw invalid("Symbolic links cannot be exported in a portable project archive: "+relative);
                if(!attrs.isRegularFile()) throw invalid("Unsupported project entry type: "+relative);
                verifyDepth(relative,maximumDepth);
                estimatedBytes[0]=addBytes(estimatedBytes[0],attrs.size(),maximumBytes,"The portable project content exceeds the configured limit");
                addEntry(entries,new ExportEntry(file,relative,false,attrs.size(),attrs.lastModifiedTime().toMillis()),maximumEntries);
                return FileVisitResult.CONTINUE;
            }
        });
        entries.sort(Comparator.comparing(ExportEntry::relative));
        return List.copyOf(entries);
    }

    /** Descripción: Extrae todas las entradas del archivo dentro del staging aplicando límites de entradas, profundidad, bytes y tiempo de lectura. Entradas: archive: ZIP recibido; staging: directorio temporal seguro. Salida: no devuelve valor; completa únicamente si el archivo entero es válido. */ private void extractArchive(byte[] archive,Path staging) throws IOException {
        long total=0;
        int entries=0;
        Set<String> names=new HashSet<>();
        long started=System.nanoTime();
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null) {
                verifyReadTime(started);
                entries++;
                if(entries>Math.max(1,properties.getMaxArchiveEntries())) throw tooLarge("The project archive contains too many entries");
                String entryName=normalizeEntryName(entry.getName());
                verifyDepth(entryName,Math.max(1,properties.getMaxTreeDepth()));
                if(!names.add(entryName)) throw invalid("Duplicated entry in project archive: "+entryName);
                Path target=staging.resolve(entryName).normalize();
                if(!target.startsWith(staging)) throw invalid("Unsafe entry in project archive: "+entryName);
                if(entry.isDirectory()) Files.createDirectories(target);
                else {
                    Files.createDirectories(target.getParent());
                    byte[] buffer=new byte[STREAM_BUFFER_BYTES];
                    int read;
                    try(var output=Files.newOutputStream(target)) {
                        while((read=zip.read(buffer))!=-1) {
                            if(read==0) continue;
                            verifyReadTime(started);
                            total=addBytes(total,read,properties.getMaxArchiveSizeBytes(),"Expanded project archive exceeds the configured limit");
                            verifyCompressionRatio(total,archive.length);
                            output.write(buffer,0,read);
                        }
                    }
                }
                zip.closeEntry();
            }
        }
    }

    /** Descripción: Copia un fichero al ZIP contabilizando los bytes realmente leídos para detectar crecimiento concurrente y evitar superar el presupuesto de contenido portable. Entradas: source: fichero origen; zip: salida ZIP; previousTotal: bytes ya exportados. Salida: devuelve el total actualizado. */ private long copyExportFile(Path source,ZipOutputStream zip,long previousTotal) throws IOException {
        long total=previousTotal;
        byte[] buffer=new byte[STREAM_BUFFER_BYTES];
        try(InputStream input=Files.newInputStream(source)) {
            int read;
            while((read=input.read(buffer))!=-1) {
                if(read==0) continue;
                total=addBytes(total,read,properties.getMaxArchiveSizeBytes(),"The portable project content exceeds the configured limit");
                zip.write(buffer,0,read);
            }
        }
        return total;
    }

    /** Descripción: Comprueba inmediatamente antes de leer que una entrada sigue siendo un fichero regular y no se ha convertido en enlace simbólico. Entradas: path: entrada previamente inventariada. Salida: no devuelve valor; rechaza cambios de tipo inseguros. */ private void verifyPortableSource(Path path) {
        if(Files.isSymbolicLink(path)||(!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS)&&!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))) throw invalid("Project entry changed while the archive was being created: "+path);
    }

    /** Descripción: Añade una entrada al inventario de exportación respetando el máximo configurado. Entradas: entries: inventario actual; entry: nueva entrada; maximumEntries: máximo permitido. Salida: no devuelve valor. */ private void addEntry(List<ExportEntry> entries,ExportEntry entry,int maximumEntries) {
        if(entries.size()>=maximumEntries) throw tooLarge("The project contains too many portable entries");
        entries.add(entry);
    }

    /** Descripción: Suma bytes de forma segura y valida el presupuesto acumulado. Entradas: current: total previo; delta: incremento; maximum: límite; message: mensaje de error. Salida: devuelve el nuevo total. */ private long addBytes(long current,long delta,long maximum,String message) {
        final long next;
        try { next=Math.addExact(current,delta); }
        catch(ArithmeticException exception) { throw tooLarge(message); }
        if(next>maximum) throw tooLarge(message);
        return next;
    }

    /** Descripción: Valida que la ruta relativa no supere la profundidad portable configurada. Entradas: relative: ruta portable; maximumDepth: profundidad máxima. Salida: no devuelve valor. */ private void verifyDepth(String relative,int maximumDepth) {
        if(Path.of(relative).getNameCount()>maximumDepth) throw tooLarge("The project contains entries deeper than the configured tree limit");
    }

    /** Descripción: Verifica que la extracción no supere el tiempo máximo configurado. Entradas: startedNanos: instante monotónico de inicio. Salida: no devuelve valor. */ private void verifyReadTime(long startedNanos) {
        long maximumMillis=Math.max(1L,properties.getMaxArchiveReadMillis());
        long elapsedMillis=(System.nanoTime()-startedNanos)/1_000_000L;
        if(elapsedMillis>maximumMillis) throw tooLarge("Project archive extraction exceeded the configured time limit");
    }

    /** Descripción: Rechaza expansiones con una relación comprimido/descomprimido superior a la configurada. Entradas: expandedBytes: bytes expandidos; compressedBytes: tamaño del ZIP recibido. Salida: no devuelve valor. */ private void verifyCompressionRatio(long expandedBytes,long compressedBytes) {
        if(compressedBytes<=0) return;
        double ratio=(double)expandedBytes/(double)compressedBytes;
        if(ratio>properties.getMaxArchiveCompressionRatio()) throw tooLarge("Project archive exceeds the configured compression ratio");
    }

    /** Descripción: Obtiene una ruta portable relativa a la raíz normalizando separadores. Entradas: root: raíz; path: entrada. Salida: devuelve la ruta relativa portable. */ private String portableRelative(Path root,Path path) {
        return root.relativize(path).toString().replace('\\','/');
    }

    /** Descripción: Normaliza un nombre de entrada ZIP y rechaza rutas absolutas, vacías o que escapen mediante segmentos padre. Entradas: value: nombre recibido. Salida: devuelve un nombre portable canónico. */ private String normalizeEntryName(String value) {
        if(value==null||value.isBlank()||value.indexOf('\0')>=0) throw invalid("The project archive contains an invalid entry name");
        String normalized=value.replace('\\','/');
        while(normalized.startsWith("./")) normalized=normalized.substring(2);
        if(normalized.startsWith("/")||normalized.matches("^[A-Za-z]:.*")) throw invalid("The project archive contains an absolute entry");
        Path portable=Path.of(normalized).normalize();
        if(portable.isAbsolute()||portable.getNameCount()==0||portable.startsWith("..")) throw invalid("Unsafe entry in project archive: "+value);
        String result=portable.toString().replace('\\','/');
        if(result.equals(".")) throw invalid("The project archive contains an invalid entry name");
        return result;
    }

    /** Descripción: Ejecuta la operación «invalid» correspondiente a la responsabilidad de ProjectZipService. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ProjectException invalid(String message) {
        return new ProjectException(ProjectException.Reason.INVALID_REQUEST,message);
    }

    /** Descripción: Ejecuta la operación «tooLarge» correspondiente a la responsabilidad de ProjectZipService. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ProjectException tooLarge(String message) {
        return new ProjectException(ProjectException.Reason.FILE_TOO_LARGE,message);
    }

    /** Entrada inmutable del inventario previo a la exportación ZIP. */
    private record ExportEntry(Path path,String relative,boolean directory,long size,long lastModifiedMillis) { }
}
