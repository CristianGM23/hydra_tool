package com.example.editor.project.portability;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;
import com.example.editor.project.ProjectApplicationService;
import com.example.editor.project.ProjectException;
import com.example.editor.project.ProjectSession;
import com.example.editor.testing.fault.HydraFaultInjectionService;
import com.example.editor.product.HydraReleaseVersion;
import tools.jackson.databind.ObjectMapper;
/** Exporta/importa proyectos en un contenedor portable con manifiesto y hash verificable. */
@Service
public class ProjectPortablePackageService {
    private static final String MANIFEST="hydra-portable-manifest.json"; private static final String ARCHIVE="project.zip";
    private final ProjectApplicationService projects; private final ObjectMapper mapper; private final HydraFaultInjectionService faults;
    /** Descripción: Inicializa portabilidad con proyecto, mapper y fault injection. Entradas: projects, mapper, faults. Salida: servicio listo. */ public ProjectPortablePackageService(ProjectApplicationService projects,ObjectMapper mapper,HydraFaultInjectionService faults){this.projects=projects;this.mapper=mapper;this.faults=faults;}
    /** Descripción: Exporta el proyecto activo con manifiesto y SHA-256. Entradas: ninguna. Salida: bytes del contenedor portable. */ public byte[] exportPortable(){
        faults.check(HydraFaultInjectionService.Point.PROJECT_PORTABLE_EXPORT); byte[] archive=projects.exportArchive(); verifyNoSecretEntries(archive);
        ProjectPortableManifest manifest=new ProjectPortableManifest(ProjectPortableManifest.CURRENT_SCHEMA_VERSION,HydraReleaseVersion.VERSION,sha256(archive),archive.length,false,Instant.now());
        try(ByteArrayOutputStream out=new ByteArrayOutputStream();ZipOutputStream zip=new ZipOutputStream(out)){zip.putNextEntry(new ZipEntry(MANIFEST));zip.write(mapper.writeValueAsBytes(manifest));zip.closeEntry();zip.putNextEntry(new ZipEntry(ARCHIVE));zip.write(archive);zip.closeEntry();zip.finish();return out.toByteArray();}
        catch(IOException exception){throw new ProjectException(ProjectException.Reason.STORAGE_ERROR,"Could not create portable project package",exception);}
    }
    /** Descripción: Verifica e importa un contenedor portable sin confiar en su contenido interno. Entradas: parent, name, content. Salida: sesión del proyecto importado. */ public ProjectSession importPortable(String parent,String name,byte[] content){
        faults.check(HydraFaultInjectionService.Point.PROJECT_PORTABLE_IMPORT); ProjectPortableManifest manifest=null;byte[] archive=null;
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(content==null?new byte[0]:content))){ZipEntry entry;int seen=0;while((entry=zip.getNextEntry())!=null){if(++seen>4)throw invalid("Portable package contains unexpected entries");if(entry.isDirectory())continue;byte[] bytes=zip.readNBytes(268_435_457);if(bytes.length>268_435_456)throw invalid("Portable package entry is too large");if(MANIFEST.equals(entry.getName()))manifest=mapper.readValue(bytes,ProjectPortableManifest.class);else if(ARCHIVE.equals(entry.getName()))archive=bytes;else throw invalid("Portable package contains an unexpected entry: "+entry.getName());}}
        catch(IOException exception){throw new ProjectException(ProjectException.Reason.INVALID_REQUEST,"Could not read portable project package",exception);}
        if(manifest==null||archive==null)throw invalid("Portable package is incomplete");if(manifest.archiveBytes()!=archive.length||!manifest.archiveSha256().equals(sha256(archive)))throw invalid("Portable project checksum does not match the manifest");return projects.importArchive(parent,name,archive);
    }

    /** Descripción: Rechaza nombres de fichero típicamente asociados a secretos antes de marcar un paquete como libre de credenciales. Entradas: archive: ZIP de proyecto. Salida: no devuelve valor. */ private static void verifyNoSecretEntries(byte[] archive){
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(archive))){ZipEntry entry;while((entry=zip.getNextEntry())!=null){String n=entry.getName()==null?"":entry.getName().replace('\\','/').toLowerCase();String leaf=n.substring(n.lastIndexOf('/')+1);if(leaf.equals(".env")||leaf.equals("credentials.json")||leaf.equals("secrets.json")||leaf.equals("id_rsa")||leaf.endsWith(".pem")||leaf.endsWith(".key"))throw invalid("Portable export contains a secret-like file that must be externalized: "+entry.getName());}}
        catch(IOException exception){throw new ProjectException(ProjectException.Reason.INVALID_REQUEST,"Could not verify portable project entries",exception);}
    }

    /** Descripción: Calcula SHA-256 del contenido. Entradas: bytes: contenido. Salida: hash hexadecimal. */ private static String sha256(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    /** Descripción: Crea una excepción de solicitud portable inválida. Entradas: message: detalle. Salida: excepción tipada. */ private static ProjectException invalid(String message){return new ProjectException(ProjectException.Reason.INVALID_REQUEST,message);}
}
