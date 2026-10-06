package com.example.editor.remote.auth;

import java.net.URI;
import com.example.editor.remote.storage.RemoteStorageProviderType;

/**
 * Compatibility facade retained for older integrations and regression contracts.
 * Production services use RemoteAuthProperties directly so compilation does not
 * depend on this helper being present in an incremental overlay.
 */
public final class RemoteOAuthConfigurationRules {
    /** Descripción: Inicializa una instancia de RemoteOAuthConfigurationRules con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private RemoteOAuthConfigurationRules(){}

    /** Descripción: Obtiene la información correspondiente a «ready» dentro de RemoteOAuthConfigurationRules. Entradas: type: dato de entrada necesario para la operación; auth: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento. Salida: devuelve un indicador que expresa el resultado de la condición. */ public static boolean ready(RemoteStorageProviderType type,RemoteAuthProperties.Provider auth,RemoteAuthProperties properties){
        if(type==null||auth==null||!auth.isEnabled()||auth.getClientId().isBlank())return false;
        return switch(type){
            case GITHUB -> auth.isDeviceFlowPreferred()||!auth.getClientSecret().isBlank();
            case GOOGLE_DRIVE -> loopbackCallback(properties)||!auth.getClientSecret().isBlank();
            case GITLAB,ONEDRIVE,DROPBOX -> true;
            case GENERIC_GIT -> false;
        };
    }

    /** Descripción: Ejecuta la operación «loopbackCallback» correspondiente a la responsabilidad de RemoteOAuthConfigurationRules. Entradas: properties: configuración que gobierna el comportamiento. Salida: devuelve un indicador que expresa el resultado de la condición. */ public static boolean loopbackCallback(RemoteAuthProperties properties){
        if(properties==null)return false;
        try {
            URI uri=URI.create(properties.getCallbackBaseUrl());String host=uri.getHost();
            return host!=null&&(host.equalsIgnoreCase("localhost")||host.equals("127.0.0.1")||host.equals("::1")||host.equals("[::1]"));
        } catch(IllegalArgumentException exception){return false;}
    }

    /** Descripción: Ejecuta la operación «callbackUrl» correspondiente a la responsabilidad de RemoteOAuthConfigurationRules. Entradas: properties: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public static String callbackUrl(RemoteAuthProperties properties){
        String base=properties==null?"":properties.getCallbackBaseUrl();
        while(base.endsWith("/"))base=base.substring(0,base.length()-1);
        return base+"/api/remote-auth/callback";
    }
}
