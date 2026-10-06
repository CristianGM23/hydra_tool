package com.example.editor.artifact.registry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import com.example.editor.io.AtomicFiles;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Atomic JSON persistence for the neutral artifact registry. */
@Component
public class HydraArtifactRegistryStore {
    public static final String FILE_NAME = "hydra-artifact-registry.json";
    private final JsonMapper mapper;
    /** Descripción: Inicializa una instancia de HydraArtifactRegistryStore con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public HydraArtifactRegistryStore(JsonMapper mapper){this.mapper = mapper;}

    /** Descripción: Obtiene la información correspondiente a «load» dentro de HydraArtifactRegistryStore. Entradas: root: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ public HydraArtifactRegistrySnapshot load(Path root){
        Path path = safe(root).resolve(FILE_NAME);
        if(!Files.isRegularFile(path)){return new HydraArtifactRegistrySnapshot("1.0", Instant.EPOCH, List.of());}
        try{return mapper.readValue(path.toFile(), HydraArtifactRegistrySnapshot.class);}
        catch(JacksonException exception){throw new IllegalStateException("Could not parse the artifact registry", exception);}
    }

    /** Descripción: Actualiza el estado gestionado por HydraArtifactRegistryStore mediante la operación «save». Entradas: root: ruta o ubicación utilizada por la operación; snapshot: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public HydraArtifactRegistrySnapshot save(Path root, HydraArtifactRegistrySnapshot snapshot){
        Path safeRoot = safe(root);
        Path target = safeRoot.resolve(FILE_NAME);
        Path temporary = safeRoot.resolve(FILE_NAME + ".tmp");
        try{
            Files.createDirectories(safeRoot);
            mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), snapshot);
            AtomicFiles.moveReplacing(temporary, target);
            return snapshot;
        }catch(IOException | JacksonException exception){
            throw new IllegalStateException("Could not persist the artifact registry", exception);
        }finally{
            try{Files.deleteIfExists(temporary);}catch(IOException ignored){/* Best-effort cleanup. */}
        }
    }

    /** Descripción: Ejecuta la operación «safe» correspondiente a la responsabilidad de HydraArtifactRegistryStore. Entradas: root: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Path safe(Path root){
        if(root == null){throw new IllegalArgumentException("registry root is required");}
        return root.toAbsolutePath().normalize();
    }
}
