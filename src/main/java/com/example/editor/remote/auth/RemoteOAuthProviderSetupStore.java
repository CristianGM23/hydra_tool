package com.example.editor.remote.auth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.example.editor.io.BoundedIo;
import com.example.editor.remote.storage.RemoteStorageProviderType;
import com.example.editor.persistence.support.HydraPersistenceSupport;
import tools.jackson.databind.ObjectMapper;

/** Atomic bounded store for public OAuth application coordinates. No OAuth secret is stored here. */
@Component
public class RemoteOAuthProviderSetupStore {
    private static final long MAXIMUM_BYTES=131072L;
    private final RemoteAuthProperties properties;
    private final ObjectMapper mapper;
    private final Object lock=new Object();

    /** Descripción: Inicializa una instancia de RemoteOAuthProviderSetupStore con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public RemoteOAuthProviderSetupStore(RemoteAuthProperties properties,ObjectMapper mapper){this.properties=properties;this.mapper=mapper;}

    /** Descripción: Obtiene la información correspondiente a «load» dentro de RemoteOAuthProviderSetupStore. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Map<RemoteStorageProviderType,RemoteOAuthProviderSetupValue> load(){
        synchronized(lock){
            Path file=file();
            if(!Files.exists(file))return Map.of();
            rejectSymlink(file);
            try {
                if(Files.size(file)>MAXIMUM_BYTES)throw new RemoteAuthException(RemoteAuthException.Reason.INVALID_REQUEST,"OAuth provider setup file exceeds the safe size limit");
                RemoteOAuthProviderSetupDocument document=mapper.readValue(BoundedIo.readInternal(file),RemoteOAuthProviderSetupDocument.class);
                if(!"1.0".equals(document.schemaVersion()))throw new RemoteAuthException(RemoteAuthException.Reason.INVALID_REQUEST,"OAuth provider setup schema is not supported");
                return document.providers();
            } catch(IOException|RuntimeException exception){
                if(exception instanceof RemoteAuthException remote)throw remote;
                throw new RemoteAuthException(RemoteAuthException.Reason.INVALID_REQUEST,"OAuth provider setup could not be read",exception);
            }
        }
    }

    /** Descripción: Ejecuta la operación «put» correspondiente a la responsabilidad de RemoteOAuthProviderSetupStore. Entradas: provider: dato de entrada necesario para la operación; value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void put(RemoteStorageProviderType provider,RemoteOAuthProviderSetupValue value){
        synchronized(lock){
            Map<RemoteStorageProviderType,RemoteOAuthProviderSetupValue> next=new EnumMap<>(RemoteStorageProviderType.class);
            next.putAll(load());next.put(provider,value);write(next);
        }
    }

    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: provider: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void delete(RemoteStorageProviderType provider){
        synchronized(lock){
            Map<RemoteStorageProviderType,RemoteOAuthProviderSetupValue> next=new EnumMap<>(RemoteStorageProviderType.class);
            next.putAll(load());next.remove(provider);write(next);
        }
    }

    /** Descripción: Actualiza el estado gestionado por RemoteOAuthProviderSetupStore mediante la operación «write». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void write(Map<RemoteStorageProviderType,RemoteOAuthProviderSetupValue> value){
        Path file=file();rejectSymlinkIfPresent(file);rejectSymlinkIfPresent(file.getParent());
        try {
            byte[] bytes=mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(new RemoteOAuthProviderSetupDocument("1.0",value));
            if(bytes.length>MAXIMUM_BYTES)throw new RemoteAuthException(RemoteAuthException.Reason.INVALID_REQUEST,"OAuth provider setup exceeds the safe size limit");
            HydraPersistenceSupport.atomicWrite(file,bytes);
        } catch(RemoteAuthException exception){throw exception;}
        catch(RuntimeException exception){throw new RemoteAuthException(RemoteAuthException.Reason.INVALID_REQUEST,"OAuth provider setup could not be stored",exception);}
    }

    /** Descripción: Ejecuta la operación «file» correspondiente a la responsabilidad de RemoteOAuthProviderSetupStore. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Path file(){return properties.getProviderSetupFile().toAbsolutePath().normalize();}
    /** Descripción: Ejecuta la operación «rejectSymlinkIfPresent» correspondiente a la responsabilidad de RemoteOAuthProviderSetupStore. Entradas: path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void rejectSymlinkIfPresent(Path path){if(path!=null&&Files.exists(path))rejectSymlink(path);}
    /** Descripción: Ejecuta la operación «rejectSymlink» correspondiente a la responsabilidad de RemoteOAuthProviderSetupStore. Entradas: path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void rejectSymlink(Path path){if(Files.isSymbolicLink(path))throw new RemoteAuthException(RemoteAuthException.Reason.INVALID_REQUEST,"OAuth provider setup paths cannot be symbolic links");}
}
