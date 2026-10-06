package com.example.editor.metamodel.importing.updatesite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.stereotype.Component;

import com.example.editor.io.AtomicFiles;
import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Persiste el paquete de recuperación mediante escritura atómica y ruta confinada. */
@Component
public class UpdateSiteRecoveryPackageStore {
    private final UpdateSiteImportProperties properties;
    private final JsonMapper mapper;

    /** Descripción: Inicializa una instancia de UpdateSiteRecoveryPackageStore con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteRecoveryPackageStore(UpdateSiteImportProperties properties, JsonMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    /** Descripción: Actualiza el estado gestionado por UpdateSiteRecoveryPackageStore mediante la operación «save». Entradas: recoveryPackage: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ String save(UpdateSiteRecoveryPackage recoveryPackage) {
        Path root = properties.storageRootPath();
        String fileName = safe(recoveryPackage.metamodelId()) + "." + safe(recoveryPackage.id()) + ".update-site-recovery.json";
        Path target = root.resolve(fileName).normalize();
        if(!target.startsWith(root)) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Unsafe update-site recovery package path");
        }
        try {
            Files.createDirectories(root);
            if(Files.isSymbolicLink(root) || Files.exists(target, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(target)) {
                throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Update-site recovery storage must not use symbolic links");
            }
            byte[] content = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(recoveryPackage);
            AtomicFiles.writeReplacing(target,content);
            return root.relativize(target).toString().replace('\\', '/');
        } catch(MetamodelDesignException exception) {
            throw exception;
        } catch(JacksonException | IOException exception) {
            throw new MetamodelDesignException(Reason.STORAGE_ERROR, "Could not persist update-site recovery package", exception);
        }
    }

    /** Descripción: Ejecuta la operación «safe» correspondiente a la responsabilidad de UpdateSiteRecoveryPackageStore. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String safe(String value) {
        String result = value == null ? "recovery" : value.replaceAll("[^A-Za-z0-9._-]", "-");
        return result.isBlank() ? "recovery" : result;
    }
}
