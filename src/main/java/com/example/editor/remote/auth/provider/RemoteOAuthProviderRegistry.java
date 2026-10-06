package com.example.editor.remote.auth.provider;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.example.editor.remote.auth.RemoteAuthException;
import com.example.editor.remote.storage.RemoteStorageProviderType;

/** Registro extensible de proveedores OAuth con detección de colisiones. */
@Component
public class RemoteOAuthProviderRegistry {
    private final Map<RemoteStorageProviderType,RemoteOAuthProvider> providers;
    /** Descripción: Inicializa una instancia de RemoteOAuthProviderRegistry con sus dependencias y datos de entrada. Entradas: available: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public RemoteOAuthProviderRegistry(List<RemoteOAuthProvider> available){
        Map<RemoteStorageProviderType,RemoteOAuthProvider> indexed=new EnumMap<>(RemoteStorageProviderType.class);
        for(RemoteOAuthProvider provider:available==null?List.<RemoteOAuthProvider>of():available)if(indexed.put(provider.type(),provider)!=null)throw new IllegalStateException("Duplicate remote OAuth provider: "+provider.type());
        providers=Map.copyOf(indexed);
    }
    /** Descripción: Valida las condiciones necesarias para «require» y rechaza entradas no válidas. Entradas: type: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public RemoteOAuthProvider require(RemoteStorageProviderType type){RemoteOAuthProvider provider=providers.get(type);if(provider==null)throw new RemoteAuthException(RemoteAuthException.Reason.PROVIDER_NOT_CONFIGURED,"OAuth provider is not available: "+type);return provider;}
}
