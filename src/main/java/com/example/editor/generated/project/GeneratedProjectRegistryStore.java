package com.example.editor.generated.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import com.example.editor.io.AtomicFiles;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Atomic persistence for generated-project registrations. */
@Component
public class GeneratedProjectRegistryStore {
    public static final String FILE_NAME = "generated-projects.json";
    private final JsonMapper mapper;
    /** Descripción: Inicializa una instancia de GeneratedProjectRegistryStore con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public GeneratedProjectRegistryStore(JsonMapper mapper){this.mapper = mapper;}

    /** Descripción: Obtiene la información correspondiente a «load» dentro de GeneratedProjectRegistryStore. Entradas: root: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ public GeneratedProjectRegistry load(Path root){
        Path path = root.resolve(FILE_NAME);
        if(!Files.isRegularFile(path)){return new GeneratedProjectRegistry("1.0", Instant.EPOCH, List.of());}
        try{return mapper.readValue(path.toFile(), GeneratedProjectRegistry.class);}
        catch(JacksonException exception){throw new IllegalStateException("Could not parse generated-project registry", exception);}
    }

    /** Descripción: Actualiza el estado gestionado por GeneratedProjectRegistryStore mediante la operación «save». Entradas: root: ruta o ubicación utilizada por la operación; registry: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public GeneratedProjectRegistry save(Path root, GeneratedProjectRegistry registry){
        Path target = root.resolve(FILE_NAME);
        Path temporary = root.resolve(FILE_NAME + ".tmp");
        try{
            Files.createDirectories(root);
            mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), registry);
            AtomicFiles.moveReplacing(temporary, target);
            return registry;
        }catch(IOException | JacksonException exception){throw new IllegalStateException("Could not persist generated-project registry", exception);}
        finally{try{Files.deleteIfExists(temporary);}catch(IOException ignored){/* Best-effort cleanup. */}}
    }
}
