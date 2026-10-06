package com.example.editor.remote.auth;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Cliente OAuth sin redirecciones automáticas, cuerpos acotados y errores saneados. */
@Component
public class RemoteOAuthHttpClient {
    private static final int MAXIMUM_RESPONSE_BYTES=65536;
    private final HttpClient client;
    private final Duration timeout;
    private final boolean allowLoopbackHttp;
    /** Descripción: Inicializa una instancia de RemoteOAuthHttpClient con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public RemoteOAuthHttpClient(RemoteAuthProperties properties){
        client=HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).followRedirects(HttpClient.Redirect.NEVER).build();
        timeout=properties.getRequestTimeout();
        allowLoopbackHttp=properties.isAllowLoopbackHttp();
    }

    /** Descripción: Ejecuta la operación «postForm» correspondiente a la responsabilidad de Response. Entradas: uri: dato de entrada necesario para la operación; values: colección de elementos que debe procesarse; headers: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public Response postForm(URI uri,Map<String,String> values,Map<String,String> headers){
        String body=(values==null?Map.<String,String>of():values).entrySet().stream().map(entry->encode(entry.getKey())+"="+encode(entry.getValue())).collect(Collectors.joining("&"));
        Map<String,String> merged=new LinkedHashMap<>();
        merged.put("Content-Type","application/x-www-form-urlencoded");
        merged.put("Accept","application/json");
        if(headers!=null) merged.putAll(headers);
        return send(HttpRequest.newBuilder(secure(uri)).timeout(timeout).POST(HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8)),merged);
    }

    /** Descripción: Obtiene la información correspondiente a «get» dentro de Response. Entradas: uri: dato de entrada necesario para la operación; headers: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public Response get(URI uri,Map<String,String> headers){return send(HttpRequest.newBuilder(secure(uri)).timeout(timeout).GET(),headers);}
    /** Descripción: Ejecuta la operación «postEmpty» correspondiente a la responsabilidad de Response. Entradas: uri: dato de entrada necesario para la operación; headers: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public Response postEmpty(URI uri,Map<String,String> headers){return send(HttpRequest.newBuilder(secure(uri)).timeout(timeout).POST(HttpRequest.BodyPublishers.noBody()),headers);}
    /** Descripción: Elimina o libera el recurso gestionado por «deleteJson» de forma controlada. Entradas: uri: dato de entrada necesario para la operación; body: dato de entrada necesario para la operación; headers: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public Response deleteJson(URI uri,byte[] body,Map<String,String> headers){
        Map<String,String> merged=new LinkedHashMap<>();merged.put("Content-Type","application/json");merged.put("Accept","application/json");if(headers!=null)merged.putAll(headers);
        return send(HttpRequest.newBuilder(secure(uri)).timeout(timeout).method("DELETE",HttpRequest.BodyPublishers.ofByteArray(body==null?new byte[0]:body)),merged);
    }

    /** Descripción: Ejecuta la operación «send» correspondiente a la responsabilidad de Response. Entradas: builder: dato de entrada necesario para la operación; headers: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Response send(HttpRequest.Builder builder,Map<String,String> headers){
        if(headers!=null) headers.forEach(builder::header);
        builder.header("User-Agent","HYDRA-Remote-Auth").header("Cache-Control","no-store");
        try {
            HttpResponse<InputStream> response=client.send(builder.build(),HttpResponse.BodyHandlers.ofInputStream());
            try(InputStream input=response.body()){
                byte[] body=readBounded(input);
                if(response.statusCode()>=300&&response.statusCode()<400) throw new RemoteAuthException(RemoteAuthException.Reason.REMOTE_ERROR,"OAuth endpoint returned an unexpected redirect");
                return new Response(response.statusCode(),response.headers().firstValue("content-type").orElse(""),body);
            }
        } catch(InterruptedException exception){
            Thread.currentThread().interrupt();throw new RemoteAuthException(RemoteAuthException.Reason.REMOTE_ERROR,"External authorization request was interrupted",exception);
        } catch(IOException exception){throw new RemoteAuthException(RemoteAuthException.Reason.REMOTE_ERROR,"External authorization request could not be completed",exception);}
    }

    /** Descripción: Obtiene la información correspondiente a «readBounded» dentro de Response. Entradas: input: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream output=new ByteArrayOutputStream(4096);byte[] buffer=new byte[4096];int total=0;int read;
        while((read=input.read(buffer))>=0){if(read==0)continue;total+=read;if(total>MAXIMUM_RESPONSE_BYTES)throw new RemoteAuthException(RemoteAuthException.Reason.REMOTE_ERROR,"OAuth provider response exceeded the safe response limit");output.write(buffer,0,read);}return output.toByteArray();
    }
    /** Descripción: Ejecuta la operación «secure» correspondiente a la responsabilidad de Response. Entradas: uri: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private URI secure(URI uri){return RemoteNetworkPolicy.requireSecure(uri,allowLoopbackHttp);}
    /** Descripción: Ejecuta la operación «encode» correspondiente a la responsabilidad de Response. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String encode(String value){return URLEncoder.encode(value==null?"":value,StandardCharsets.UTF_8);}
    public record Response(int statusCode,String contentType,byte[] body){
        /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de Response. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String text(){return new String(body,StandardCharsets.UTF_8);}
        /** Descripción: Ejecuta la operación «safeText» correspondiente a la responsabilidad de Response. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String safeText(){return RemoteSecretRedactor.redact(text());}
        /** Descripción: Ejecuta la operación «successful» correspondiente a la responsabilidad de Response. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean successful(){return statusCode>=200&&statusCode<300;}
    }
}
