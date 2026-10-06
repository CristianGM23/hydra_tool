package com.example.editor.remote.storage.provider;

import tools.jackson.core.JacksonException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import com.example.editor.remote.storage.RemoteStorageException;
import com.example.editor.remote.auth.RemoteSecretRedactor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Helpers JSON compartidos por adaptadores sin introducir SDKs externos. */
final class RemoteProviderJsonSupport {
    /** Descripción: Inicializa una instancia de RemoteProviderJsonSupport con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private RemoteProviderJsonSupport(){}
    /** Descripción: Ejecuta la operación «parse» correspondiente a la responsabilidad de RemoteProviderJsonSupport. Entradas: mapper: componente de conversión utilizado durante el procesamiento; response: dato de entrada necesario para la operación; operation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static JsonNode parse(ObjectMapper mapper,HttpResponse<byte[]> response,String operation) {
        try { return mapper.readTree(response.body()); }
        catch(JacksonException exception) { throw new RemoteStorageException(RemoteStorageException.Reason.REMOTE_ERROR,operation+" returned invalid JSON",exception); }
    }
    /** Descripción: Ejecuta la operación «parse» correspondiente a la responsabilidad de RemoteProviderJsonSupport. Entradas: mapper: componente de conversión utilizado durante el procesamiento; response: dato de entrada necesario para la operación; operation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static JsonNode parse(ObjectMapper mapper,RemoteStorageHttpClient.BoundedResponse response,String operation) {
        try { return mapper.readTree(response.body()); }
        catch(JacksonException exception) { throw new RemoteStorageException(RemoteStorageException.Reason.REMOTE_ERROR,operation+" returned invalid JSON",exception); }
    }
    /** Descripción: Ejecuta la operación «json» correspondiente a la responsabilidad de RemoteProviderJsonSupport. Entradas: mapper: componente de conversión utilizado durante el procesamiento; value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ static byte[] json(ObjectMapper mapper,Object value) {
        try { return mapper.writeValueAsBytes(value); }
        catch(JacksonException exception) { throw new RemoteStorageException(RemoteStorageException.Reason.REMOTE_ERROR,"Remote request could not be serialized",exception); }
    }
    /** Descripción: Ejecuta la operación «base64Json» correspondiente a la responsabilidad de RemoteProviderJsonSupport. Entradas: mapper: componente de conversión utilizado durante el procesamiento; metadata: dato de entrada necesario para la operación; contentField: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ static HttpRequest.BodyPublisher base64Json(ObjectMapper mapper,Map<String,Object> metadata,String contentField,byte[] content) {
        return Base64JsonBodyPublisher.create(mapper,metadata,contentField,content);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de RemoteProviderJsonSupport. Entradas: response: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static String text(HttpResponse<byte[]> response) {
        String value=new String(response.body(),StandardCharsets.UTF_8).trim();
        return RemoteSecretRedactor.redact(value.length()>500?value.substring(0,500)+"…":value);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de RemoteProviderJsonSupport. Entradas: response: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static String text(RemoteStorageHttpClient.BoundedResponse response) {
        String value=new String(response.body(),StandardCharsets.UTF_8).trim();
        return RemoteSecretRedactor.redact(value.length()>500?value.substring(0,500)+"…":value);
    }
    /** Descripción: Ejecuta la operación «base» correspondiente a la responsabilidad de RemoteProviderJsonSupport. Entradas: configured: configuración que gobierna el comportamiento; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static String base(String configured,String fallback) {
        String value=configured==null||configured.isBlank()?fallback:configured.trim();
        while(value.endsWith("/")) value=value.substring(0,value.length()-1);
        return value;
    }
    /** Descripción: Ejecuta la operación «uri» correspondiente a la responsabilidad de RemoteProviderJsonSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ static URI uri(String value) {
        try { return URI.create(value); }
        catch(IllegalArgumentException exception) { throw new RemoteStorageException(RemoteStorageException.Reason.INVALID_REQUEST,"Configured remote URL is invalid",exception); }
    }
}
