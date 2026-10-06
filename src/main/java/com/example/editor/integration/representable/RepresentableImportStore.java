package com.example.editor.integration.representable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import com.example.editor.io.AtomicFiles;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Atomic persistence for approved neutral change packages. */
@Component
public class RepresentableImportStore {
    private final JsonMapper mapper;
    /** Descripción: Inicializa una instancia de RepresentableImportStore con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public RepresentableImportStore(JsonMapper mapper){this.mapper = mapper;}

    /** Descripción: Actualiza el estado gestionado por RepresentableImportStore mediante la operación «save». Entradas: root: ruta o ubicación utilizada por la operación; preview: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public Path save(Path root, RepresentableImportPreview preview){
        Path safeRoot = root.toAbsolutePath().normalize();
        String fileName = preview.previewId().replaceAll("[^A-Za-z0-9._-]+", "-") + ".representable-import.json";
        Path target = safeRoot.resolve(fileName).normalize();
        if(!target.startsWith(safeRoot)){throw new IllegalArgumentException("Import path escapes configured root");}
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try{
            Files.createDirectories(safeRoot);
            mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), preview);
            AtomicFiles.moveReplacing(temporary, target);
            return target;
        }catch(IOException | JacksonException exception){
            throw new IllegalStateException("Could not persist representable import", exception);
        }finally{
            try{Files.deleteIfExists(temporary);}catch(IOException ignored){/* Best-effort cleanup. */}
        }
    }
}
