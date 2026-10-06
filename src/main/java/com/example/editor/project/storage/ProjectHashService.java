package com.example.editor.project.storage;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import com.example.editor.project.ProjectException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/** Calcula ETags y huellas reproducibles. */
@Component
public class ProjectHashService {
    /** Descripción: Ejecuta la operación «etag» correspondiente a la responsabilidad de ProjectHashService. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public String etag(byte[] content) {
        try {
            return "\"" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)) + "\"";
        } catch(NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available",exception);
        }
    }
    /** Descripción: Ejecuta la operación «etag» correspondiente a la responsabilidad de ProjectHashService. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ public String etag(Path path) {
        try(InputStream input=Files.newInputStream(path)) {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            byte[] buffer=new byte[8192]; int read;
            while((read=input.read(buffer))>=0) digest.update(buffer,0,read);
            return "\"" + HexFormat.of().formatHex(digest.digest()) + "\"";
        } catch(IOException exception) {
            throw new ProjectException(ProjectException.Reason.STORAGE_ERROR,"Could not hash " + path,exception);
        } catch(NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available",exception);
        }
    }

}
