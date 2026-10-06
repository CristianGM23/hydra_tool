package com.example.editor.metamodel.importing.updatesite;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.editor.io.AtomicFiles;
import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;

/**
 * Almacena blobs del importador p2 fuera del heap utilizando SHA-256 como clave.
 * El contenido se escribe una sola vez y puede reutilizarse entre análisis.
 */
@Component
public class UpdateSiteArtifactBlobStore {
    private static final int BUFFER_SIZE = 64 * 1024;
    private final Path root;

    @Autowired
    /** Descripción: Inicializa una instancia de UpdateSiteArtifactBlobStore con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteArtifactBlobStore(UpdateSiteImportProperties properties) {
        this(properties.storageRootPath().resolve("artifact-cache").resolve("objects"));
    }

    /** Descripción: Inicializa una instancia de UpdateSiteArtifactBlobStore con sus dependencias y datos de entrada. Entradas: root: ruta o ubicación utilizada por la operación. Salida: deja la instancia inicializada y lista para su uso. */ UpdateSiteArtifactBlobStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /** Descripción: Ejecuta la operación «store» correspondiente a la responsabilidad de UpdateSiteArtifactBlobStore. Entradas: source: dato de entrada necesario para la operación; maximumBytes: dato de entrada necesario para la operación; logicalPath: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ UpdateSiteStoredArtifact store(InputStream source, long maximumBytes, String logicalPath) {
        prepareRoot();
        Path temporary = root.resolve(".tmp-" + UUID.randomUUID()).normalize();
        if(!temporary.startsWith(root)) throw new IllegalStateException("Unsafe temporary blob path");
        MessageDigest digest = digest();
        long size = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try(OutputStream output = Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            int read;
            while((read = source.read(buffer)) >= 0) {
                if(read == 0) continue;
                size += read;
                if(size > maximumBytes) {
                    throw new MetamodelDesignException(Reason.INVALID_REQUEST,
                            "Update-site entry exceeds the configured limit: " + logicalPath);
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        } catch(MetamodelDesignException exception) {
            deleteQuietly(temporary);
            throw exception;
        } catch(IOException exception) {
            deleteQuietly(temporary);
            throw new MetamodelDesignException(Reason.STORAGE_ERROR,
                    "Could not stage update-site artifact: " + logicalPath, exception);
        }
        String sha256 = HexFormat.of().formatHex(digest.digest());
        Path destination = objectPath(sha256);
        try {
            Files.createDirectories(destination.getParent());
            if(Files.exists(destination)) {
                deleteQuietly(temporary);
            } else {
                try { AtomicFiles.moveNew(temporary,destination); }
                catch(FileAlreadyExistsException race) { deleteQuietly(temporary); }
            }
            return new UpdateSiteStoredArtifact(sha256, size, destination);
        } catch(IOException exception) {
            deleteQuietly(temporary);
            throw new MetamodelDesignException(Reason.STORAGE_ERROR,
                    "Could not persist update-site artifact: " + logicalPath, exception);
        }
    }

    /** Descripción: Ejecuta la operación «store» correspondiente a la responsabilidad de UpdateSiteArtifactBlobStore. Entradas: content: valor de entrada que debe procesarse; maximumBytes: dato de entrada necesario para la operación; logicalPath: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ UpdateSiteStoredArtifact store(byte[] content, long maximumBytes, String logicalPath) {
        return store(new java.io.ByteArrayInputStream(content == null ? new byte[0] : content), maximumBytes, logicalPath);
    }

    /** Descripción: Crea o prepara el resultado de «open» dentro de UpdateSiteArtifactBlobStore. Entradas: artifact: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ InputStream open(UpdateSiteStoredArtifact artifact) {
        try {
            return Files.newInputStream(artifact.path(), StandardOpenOption.READ);
        } catch(IOException exception) {
            throw new MetamodelDesignException(Reason.STORAGE_ERROR,
                    "Could not open cached update-site artifact: " + artifact.sha256(), exception);
        }
    }

    /** Descripción: Obtiene la información correspondiente a «read» dentro de UpdateSiteArtifactBlobStore. Entradas: artifact: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ byte[] read(UpdateSiteStoredArtifact artifact) {
        try {
            return com.example.editor.io.BoundedIo.readInternal(artifact.path());
        } catch(IOException exception) {
            throw new MetamodelDesignException(Reason.STORAGE_ERROR,
                    "Could not read cached update-site artifact: " + artifact.sha256(), exception);
        }
    }

    /** Descripción: Ejecuta la operación «root» correspondiente a la responsabilidad de UpdateSiteArtifactBlobStore. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ Path root() {
        return root;
    }

    /** Descripción: Ejecuta la operación «objectPath» correspondiente a la responsabilidad de UpdateSiteArtifactBlobStore. Entradas: sha256: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Path objectPath(String sha256) {
        String prefix = sha256.substring(0, 2);
        Path result = root.resolve(prefix).resolve(sha256).normalize();
        if(!result.startsWith(root)) throw new IllegalStateException("Unsafe blob path");
        return result;
    }

    /** Descripción: Ejecuta la operación «prepareRoot» correspondiente a la responsabilidad de UpdateSiteArtifactBlobStore. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void prepareRoot() {
        try {
            Files.createDirectories(root);
        } catch(IOException exception) {
            throw new MetamodelDesignException(Reason.STORAGE_ERROR,
                    "Could not create update-site artifact cache: " + root, exception);
        }
    }

    /** Descripción: Ejecuta la operación «digest» correspondiente a la responsabilidad de UpdateSiteArtifactBlobStore. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch(NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /** Descripción: Elimina o libera el recurso gestionado por «deleteQuietly» de forma controlada. Entradas: path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void deleteQuietly(Path path) {
        try { Files.deleteIfExists(path); } catch(IOException ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/metamodel/importing/updatesite/UpdateSiteArtifactBlobStore.java",ignored);}
    }
}
