package com.example.editor.metamodel.validation;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Persistencia local tolerante a fallos para el último gate de validación de cada metamodelo. */
@Component
public class MetamodelValidationReceiptStore {
    private final MetamodelValidationProperties properties;
    private final JsonMapper mapper;
    /** Descripción: Inicializa una instancia de MetamodelValidationReceiptStore con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelValidationReceiptStore(MetamodelValidationProperties properties, JsonMapper mapper){this.properties = properties; this.mapper = mapper;}

    public synchronized Optional<MetamodelValidationReceipt> find(String metamodelId){
        Path path = path(metamodelId);
        if(!Files.isRegularFile(path)){return Optional.empty();}
        try{return Optional.of(mapper.readValue(com.example.editor.io.BoundedIo.readInternal(path), MetamodelValidationReceipt.class));}
        catch(IOException | RuntimeException exception){return Optional.empty();}
    }

    public synchronized void save(MetamodelValidationReceipt receipt){
        Path path = path(receipt.metamodelId()); Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try{
            Files.createDirectories(path.getParent()); Files.write(temporary, mapper.writeValueAsBytes(receipt));
            try{Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException exception){Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);}
        }catch(IOException | RuntimeException exception){
            try{Files.deleteIfExists(temporary);}catch(IOException ignored){/* best effort cleanup */}
            throw new IllegalStateException("Could not persist metamodel validation receipt", exception);
        }
    }

    /** Descripción: Elimina o libera el recurso gestionado por «deleteIfExists» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public synchronized void deleteIfExists(String metamodelId){try{Files.deleteIfExists(path(metamodelId));}catch(IOException exception){throw new IllegalStateException("Could not delete metamodel validation receipt", exception);}}

    /** Descripción: Ejecuta la operación «path» correspondiente a la responsabilidad de MetamodelValidationReceiptStore. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Path path(String metamodelId){
        if(metamodelId == null || !metamodelId.matches("[A-Za-z0-9_-]+")){throw new IllegalArgumentException("Unsafe metamodel validation id");}
        Path root = properties.getReceiptRoot().toAbsolutePath().normalize(); Path result = root.resolve(metamodelId + ".validation.json").normalize();
        if(!result.startsWith(root)){throw new IllegalArgumentException("Metamodel validation path escapes configured root");}
        return result;
    }
}
