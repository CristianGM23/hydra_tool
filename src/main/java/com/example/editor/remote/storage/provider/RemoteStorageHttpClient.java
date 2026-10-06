package com.example.editor.remote.storage.provider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.example.editor.remote.storage.RemoteStorageException;
import com.example.editor.remote.storage.RemoteStorageProperties;
import com.example.editor.remote.auth.RemoteAuthProperties;
import com.example.editor.remote.auth.RemoteNetworkPolicy;

/** Cliente HTTP único y acotado para todos los adaptadores remotos. */
@Component
public class RemoteStorageHttpClient {
    private static final long MAXIMUM_ERROR_BODY_BYTES=65536L;
    private static final long MAXIMUM_METADATA_RESPONSE_BYTES=1048576L;
    private final HttpClient client;
    private final Duration requestTimeout;
    private final boolean allowLoopbackHttp;
    /** Descripción: Inicializa una instancia de RemoteStorageHttpClient con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; authProperties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public RemoteStorageHttpClient(RemoteStorageProperties properties,RemoteAuthProperties authProperties) {
        client=HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).followRedirects(HttpClient.Redirect.NEVER).build();
        requestTimeout=properties.getRequestTimeout();
        allowLoopbackHttp=authProperties.isAllowLoopbackHttp();
    }


    /** Respuesta JSON/metadata siempre acotada, también en caminos de éxito. */
    /** Descripción: Ejecuta la operación «sendSmall» correspondiente a la responsabilidad de BoundedResponse. Entradas: method: dato de entrada necesario para la operación; uri: dato de entrada necesario para la operación; headers: dato de entrada necesario para la operación; body: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public BoundedResponse sendSmall(String method,URI uri,Map<String,String> headers,byte[] body) {
        return sendBoundedInternal(method,uri,headers,body,MAXIMUM_METADATA_RESPONSE_BYTES);
    }

    /** Igual que sendSmall, pero permite un BodyPublisher streaming de longitud conocida. */
    /** Descripción: Ejecuta la operación «sendSmallPublisher» correspondiente a la responsabilidad de BoundedResponse. Entradas: method: dato de entrada necesario para la operación; uri: dato de entrada necesario para la operación; headers: dato de entrada necesario para la operación; body: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public BoundedResponse sendSmallPublisher(String method,URI uri,Map<String,String> headers,HttpRequest.BodyPublisher body) {
        return sendBoundedPublisherInternal(method,uri,headers,body,MAXIMUM_METADATA_RESPONSE_BYTES);
    }

    /**
     * Descarga una respuesta sin permitir que un proveedor remoto haga crecer el
     * heap por encima del límite configurado. Las respuestas de error se drenan
     * solo hasta un máximo pequeño, suficiente para diagnóstico.
     */
    /** Descripción: Ejecuta la operación «sendBounded» correspondiente a la responsabilidad de BoundedResponse. Entradas: method: dato de entrada necesario para la operación; uri: dato de entrada necesario para la operación; headers: dato de entrada necesario para la operación; body: dato de entrada necesario para la operación; maximumBytes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public BoundedResponse sendBounded(String method,URI uri,Map<String,String> headers,byte[] body,long maximumBytes) {
        return sendBoundedInternal(method,uri,headers,body,maximumBytes);
    }

    /** Descarga una URL preautenticada sin propagar cabeceras OAuth fuera del proveedor. */
    /** Descripción: Ejecuta la operación «sendUnauthenticatedBounded» correspondiente a la responsabilidad de BoundedResponse. Entradas: uri: dato de entrada necesario para la operación; maximumBytes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public BoundedResponse sendUnauthenticatedBounded(URI uri,long maximumBytes) {
        return sendBoundedInternal("GET",RemoteNetworkPolicy.requireHttps(uri),Map.of("Accept","application/octet-stream"),null,maximumBytes);
    }

    /** Descripción: Ejecuta la operación «sendBoundedPublisherInternal» correspondiente a la responsabilidad de BoundedResponse. Entradas: method: dato de entrada necesario para la operación; uri: dato de entrada necesario para la operación; headers: dato de entrada necesario para la operación; body: dato de entrada necesario para la operación; maximumBytes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private BoundedResponse sendBoundedPublisherInternal(String method,URI uri,Map<String,String> headers,HttpRequest.BodyPublisher body,long maximumBytes) {
        try {
            HttpResponse<InputStream> response=client.send(request(method,uri,headers,body),HttpResponse.BodyHandlers.ofInputStream());
            long limit=response.statusCode()>=200&&response.statusCode()<300?Math.max(1L,maximumBytes):MAXIMUM_ERROR_BODY_BYTES;
            try(InputStream input=response.body()) {
                return new BoundedResponse(response.statusCode(),response.headers(),readBounded(input,limit,response.statusCode()>=200&&response.statusCode()<300));
            }
        } catch(InterruptedException exception) {
            Thread.currentThread().interrupt();throw new RemoteStorageException(RemoteStorageException.Reason.REMOTE_ERROR,"Remote request was interrupted",exception);
        } catch(IOException|IllegalArgumentException exception) {
            throw new RemoteStorageException(RemoteStorageException.Reason.REMOTE_ERROR,"Remote request could not be completed: "+exception.getMessage(),exception);
        }
    }

    /** Descripción: Ejecuta la operación «sendBoundedInternal» correspondiente a la responsabilidad de BoundedResponse. Entradas: method: dato de entrada necesario para la operación; uri: dato de entrada necesario para la operación; headers: dato de entrada necesario para la operación; body: dato de entrada necesario para la operación; maximumBytes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private BoundedResponse sendBoundedInternal(String method,URI uri,Map<String,String> headers,byte[] body,long maximumBytes) {
        try {
            HttpResponse<InputStream> response=client.send(request(method,uri,headers,body),HttpResponse.BodyHandlers.ofInputStream());
            long limit=response.statusCode()>=200&&response.statusCode()<300?Math.max(1L,maximumBytes):MAXIMUM_ERROR_BODY_BYTES;
            try(InputStream input=response.body()) {
                return new BoundedResponse(response.statusCode(),response.headers(),readBounded(input,limit,response.statusCode()>=200&&response.statusCode()<300));
            }
        } catch(InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RemoteStorageException(RemoteStorageException.Reason.REMOTE_ERROR,"Remote request was interrupted",exception);
        } catch(IOException|IllegalArgumentException exception) {
            throw new RemoteStorageException(RemoteStorageException.Reason.REMOTE_ERROR,"Remote request could not be completed: "+exception.getMessage(),exception);
        }
    }

    /** Descripción: Ejecuta la operación «request» correspondiente a la responsabilidad de BoundedResponse. Entradas: method: dato de entrada necesario para la operación; uri: dato de entrada necesario para la operación; headers: dato de entrada necesario para la operación; body: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private HttpRequest request(String method,URI uri,Map<String,String> headers,HttpRequest.BodyPublisher body) {
        URI secured=RemoteNetworkPolicy.requireSecure(uri,allowLoopbackHttp);
        HttpRequest.Builder builder=HttpRequest.newBuilder(secured).timeout(requestTimeout).header("User-Agent","HYDRA-Remote-Storage").header("Cache-Control","no-store");
        if(headers!=null)headers.forEach(builder::header);
        if("GET".equals(method))builder.GET();else if("HEAD".equals(method))builder.method("HEAD",HttpRequest.BodyPublishers.noBody());else if("DELETE".equals(method)&&body==null)builder.DELETE();else builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():body);
        return builder.build();
    }

    /** Descripción: Ejecuta la operación «request» correspondiente a la responsabilidad de BoundedResponse. Entradas: method: dato de entrada necesario para la operación; uri: dato de entrada necesario para la operación; headers: dato de entrada necesario para la operación; body: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private HttpRequest request(String method,URI uri,Map<String,String> headers,byte[] body) {
        URI secured=RemoteNetworkPolicy.requireSecure(uri,allowLoopbackHttp);
        HttpRequest.Builder builder=HttpRequest.newBuilder(secured).timeout(requestTimeout).header("User-Agent","HYDRA-Remote-Storage").header("Cache-Control","no-store");
        if(headers!=null) headers.forEach(builder::header);
        if("GET".equals(method)) builder.GET();
        else if("HEAD".equals(method)) builder.method("HEAD",HttpRequest.BodyPublishers.noBody());
        else if("DELETE".equals(method)) builder.DELETE();
        else builder.method(method,HttpRequest.BodyPublishers.ofByteArray(body==null?new byte[0]:body));
        return builder.build();
    }

    /** Descripción: Obtiene la información correspondiente a «readBounded» dentro de BoundedResponse. Entradas: input: valor de entrada que debe procesarse; maximumBytes: dato de entrada necesario para la operación; enforceArtifactLimit: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] readBounded(InputStream input,long maximumBytes,boolean enforceArtifactLimit) throws IOException {
        int initial=(int)Math.min(Math.max(1024L,maximumBytes),65536L);
        ByteArrayOutputStream output=new ByteArrayOutputStream(initial);
        byte[] buffer=new byte[16384];
        long total=0;
        int read;
        while((read=input.read(buffer))>=0) {
            if(read==0) continue;
            total+=read;
            if(total>maximumBytes) {
                if(enforceArtifactLimit) throw new RemoteStorageException(RemoteStorageException.Reason.FILE_TOO_LARGE,"Remote artifact exceeds the configured limit of "+maximumBytes+" bytes");
                break;
            }
            output.write(buffer,0,read);
        }
        return output.toByteArray();
    }

    /** Buffer HTTP de vida corta; se transfiere por propiedad para no duplicar artefactos grandes en heap. */
    public record BoundedResponse(int statusCode,HttpHeaders headers,byte[] body) {
        public BoundedResponse { body=body==null?new byte[0]:body; }
    }
}
