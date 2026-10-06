package com.example.editor.remote.storage;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Registro extensible de adaptadores remotos; detecta colisiones al arrancar. */
@Component
public class RemoteStorageProviderRegistry {
    private final Map<RemoteStorageProviderType,RemoteArtifactStorageProvider> providers;
    /** Descripción: Inicializa una instancia de RemoteStorageProviderRegistry con sus dependencias y datos de entrada. Entradas: available: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public RemoteStorageProviderRegistry(List<RemoteArtifactStorageProvider> available) {
        Map<RemoteStorageProviderType,RemoteArtifactStorageProvider> indexed=new EnumMap<>(RemoteStorageProviderType.class);
        for(RemoteArtifactStorageProvider provider:available==null?List.<RemoteArtifactStorageProvider>of():available) {
            if(indexed.put(provider.type(),provider)!=null) throw new IllegalStateException("Duplicate remote storage provider: "+provider.type());
        }
        providers=Map.copyOf(indexed);
    }
    /** Descripción: Valida las condiciones necesarias para «require» y rechaza entradas no válidas. Entradas: type: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public RemoteArtifactStorageProvider require(RemoteStorageProviderType type) {
        RemoteArtifactStorageProvider provider=providers.get(type);
        if(provider==null) throw new RemoteStorageException(RemoteStorageException.Reason.INVALID_REQUEST,"Remote provider is not available: "+type);
        return provider;
    }
}
