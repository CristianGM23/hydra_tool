package com.example.editor.remote.auth;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/** Rechaza downgrade, user-info y esquemas no seguros antes de cualquier OAuth o descarga preautenticada. */
public final class RemoteNetworkPolicy {
    /** Descripción: Inicializa una instancia de RemoteNetworkPolicy con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private RemoteNetworkPolicy(){}
    /** Descripción: Valida las condiciones necesarias para «requireSecure» y rechaza entradas no válidas. Entradas: uri: dato de entrada necesario para la operación; allowLoopbackHttp: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static URI requireSecure(URI uri,boolean allowLoopbackHttp){
        if(uri==null||uri.getHost()==null||uri.getHost().isBlank()) throw invalid("Remote URL must include a host");
        if(uri.getUserInfo()!=null) throw invalid("Remote URLs cannot embed user credentials");
        if(uri.getFragment()!=null) throw invalid("Remote URLs cannot contain fragments");
        String scheme=uri.getScheme()==null?"":uri.getScheme().toLowerCase(Locale.ROOT);
        if("https".equals(scheme)) return uri;
        if("http".equals(scheme)&&allowLoopbackHttp&&isLoopbackLiteral(uri.getHost())) return uri;
        throw invalid("Remote connections require HTTPS; plain HTTP is allowed only on a loopback IP address");
    }
    /** Descripción: Valida las condiciones necesarias para «requireHttps» y rechaza entradas no válidas. Entradas: uri: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static URI requireHttps(URI uri){return requireSecure(uri,false);}
    /** Descripción: Evalúa la condición «isLoopbackLiteral» sin modificar el estado externo. Entradas: host: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public static boolean isLoopbackLiteral(String host){
        if(host==null) return false;
        String value=host.replace("[","").replace("]","");
        if(!value.equals("127.0.0.1")&&!value.equals("::1")) return false;
        try {return InetAddress.getByName(value).isLoopbackAddress();}
        catch(UnknownHostException exception){return false;}
    }
    /** Descripción: Ejecuta la operación «invalid» correspondiente a la responsabilidad de RemoteNetworkPolicy. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static RemoteAuthException invalid(String message){return new RemoteAuthException(RemoteAuthException.Reason.INVALID_REQUEST,message);}
}
