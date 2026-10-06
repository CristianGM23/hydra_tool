package com.example.editor.metamodel.importing.updatesite;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;

/** Copia uploads multipart a staging en disco para no duplicarlos en el heap. */
@Component
public class UpdateSiteUploadStagingService {
    private static final int BUFFER_SIZE = 64 * 1024;
    private final UpdateSiteImportProperties properties;

    /** Descripción: Inicializa una instancia de UpdateSiteUploadStagingService con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteUploadStagingService(UpdateSiteImportProperties properties) {
        this.properties = properties;
    }

    /** Descripción: Ejecuta la operación «stage» correspondiente a la responsabilidad de UpdateSiteUploadStagingService. Entradas: files: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ List<UpdateSiteInputFile> stage(List<MultipartFile> files) {
        if(files == null || files.isEmpty()) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "At least one update-site file is required");
        }
        Path root = properties.storageRootPath().resolve("staging").normalize();
        List<Path> stagedPaths = new ArrayList<>();
        List<UpdateSiteInputFile> staged = new ArrayList<>();
        long total = 0;
        try {
            Files.createDirectories(root);
            for(MultipartFile file : files) {
                String logicalPath = file.getOriginalFilename();
                if(logicalPath == null || logicalPath.isBlank()) logicalPath = file.getName();
                Path destination = root.resolve(UUID.randomUUID() + ".upload").normalize();
                if(!destination.startsWith(root)) throw new IllegalStateException("Unsafe update-site staging path");
                long copied = copy(file, destination, properties.getMaxUploadBytes() - total);
                total += copied;
                if(total > properties.getMaxUploadBytes()) {
                    throw new MetamodelDesignException(Reason.INVALID_REQUEST,
                            "Update-site upload exceeds the configured size limit");
                }
                stagedPaths.add(destination);
                staged.add(UpdateSiteInputFile.staged(logicalPath, destination, copied, true));
            }
            return List.copyOf(staged);
        } catch(RuntimeException | IOException exception) {
            stagedPaths.forEach(this::deleteQuietly);
            if(exception instanceof MetamodelDesignException designException) throw designException;
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Could not stage update-site upload", exception);
        }
    }

    /** Descripción: Ejecuta la operación «cleanup» correspondiente a la responsabilidad de UpdateSiteUploadStagingService. Entradas: files: archivo o contenido que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void cleanup(List<UpdateSiteInputFile> files) {
        if(files == null) return;
        files.forEach(UpdateSiteInputFile::cleanup);
    }

    /** Descripción: Ejecuta la operación «copy» correspondiente a la responsabilidad de UpdateSiteUploadStagingService. Entradas: file: archivo o contenido que debe procesarse; destination: dato de entrada necesario para la operación; remainingBudget: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private long copy(MultipartFile file, Path destination, long remainingBudget) throws IOException {
        if(remainingBudget <= 0) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Update-site upload exceeds the configured size limit");
        }
        long copied = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try(InputStream input = file.getInputStream();
                OutputStream output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            int read;
            while((read = input.read(buffer)) >= 0) {
                if(read == 0) continue;
                copied += read;
                if(copied > remainingBudget) {
                    throw new MetamodelDesignException(Reason.INVALID_REQUEST,
                            "Update-site upload exceeds the configured size limit");
                }
                output.write(buffer, 0, read);
            }
        } catch(RuntimeException | IOException exception) {
            deleteQuietly(destination);
            throw exception;
        }
        return copied;
    }

    /** Descripción: Elimina o libera el recurso gestionado por «deleteQuietly» de forma controlada. Entradas: path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void deleteQuietly(Path path) {
        try { Files.deleteIfExists(path); } catch(IOException ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/metamodel/importing/updatesite/UpdateSiteUploadStagingService.java",ignored);}
    }
}
