package com.example.editor.metamodel.importing;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;

/** Copia uploads a staging mediante streaming, aplica el límite antes de materializar bytes y calcula SHA-256. */
@Service
public class MetamodelImportStagingService {
    private static final int BUFFER_SIZE = 64 * 1024;
    private final MetamodelImportProperties properties;

    /** Descripción: Inicializa una instancia de MetamodelImportStagingService con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelImportStagingService(MetamodelImportProperties properties){this.properties = properties;}

    /** Descripción: Ejecuta la operación «stage» correspondiente a la responsabilidad de MetamodelImportStagingService. Entradas: file: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportStagedFile stage(MultipartFile file){
        if(file == null){throw invalid("Metamodel import file is required");}
        Path root = properties.getStagingRoot().toAbsolutePath().normalize();
        String name = safeFileName(file.getOriginalFilename());
        String mediaType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        long declared = file.getSize(); long maximum = properties.getMaxFileSizeBytes();
        if(declared > maximum){throw tooLarge(declared, maximum);}
        Path path = root.resolve(UUID.randomUUID() + "-" + name).normalize();
        if(!path.startsWith(root)){throw invalid("Metamodel staging path is outside the configured root");}
        try{
            Files.createDirectories(root); MessageDigest digest = digest(); long total = 0;
            try(InputStream input = file.getInputStream(); var output = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)){
                byte[] buffer = new byte[BUFFER_SIZE]; int read;
                while((read = input.read(buffer)) >= 0){
                    if(read == 0){continue;}
                    total += read;
                    if(total > maximum){throw tooLarge(total, maximum);}
                    digest.update(buffer, 0, read); output.write(buffer, 0, read);
                }
            }catch(RuntimeException | IOException exception){deleteQuietly(path); throw exception;}
            return new MetamodelImportStagedFile(path, name, mediaType, total, HexFormat.of().formatHex(digest.digest()));
        }catch(MetamodelDesignException exception){throw exception;}
        catch(IOException exception){deleteQuietly(path); throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Could not stage uploaded metamodel import file", exception);}
    }

    /** Descripción: Obtiene la información correspondiente a «read» dentro de MetamodelImportStagingService. Entradas: staged: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public byte[] read(MetamodelImportStagedFile staged){
        try{
            long size = Files.size(staged.path());
            if(size > properties.getMaxFileSizeBytes()){throw tooLarge(size, properties.getMaxFileSizeBytes());}
            return com.example.editor.io.BoundedIo.readInternal(staged.path());
        }catch(MetamodelDesignException exception){throw exception;}
        catch(IOException exception){throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Could not read staged metamodel import file", exception);}
    }

    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: staged: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void delete(MetamodelImportStagedFile staged){if(staged != null){deleteQuietly(staged.path());}}

    /** Descripción: Ejecuta la operación «digest» correspondiente a la responsabilidad de MetamodelImportStagingService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException exception){throw new IllegalStateException("SHA-256 is unavailable", exception);}}
    /** Descripción: Ejecuta la operación «tooLarge» correspondiente a la responsabilidad de MetamodelImportStagingService. Entradas: size: dato de entrada necesario para la operación; maximum: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetamodelDesignException tooLarge(long size, long maximum){return invalid("Metamodel import file is too large: " + size + " bytes. Maximum allowed is " + maximum + " bytes.");}
    /** Descripción: Ejecuta la operación «invalid» correspondiente a la responsabilidad de MetamodelImportStagingService. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetamodelDesignException invalid(String message){return new MetamodelDesignException(Reason.INVALID_REQUEST, message);}
    /** Descripción: Elimina o libera el recurso gestionado por «deleteQuietly» de forma controlada. Entradas: path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void deleteQuietly(Path path){try{Files.deleteIfExists(path);}catch(IOException ignored){/* Best-effort cleanup; primary error is preserved. */}}
    /** Descripción: Ejecuta la operación «safeFileName» correspondiente a la responsabilidad de MetamodelImportStagingService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String safeFileName(String value){String normalized = value == null ? "" : value.replaceAll("[^A-Za-z0-9._-]", "-"); return normalized.isBlank() ? "imported-metamodel" : normalized;}
}
