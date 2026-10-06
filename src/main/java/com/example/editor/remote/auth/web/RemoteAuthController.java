package com.example.editor.remote.auth.web;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.example.editor.remote.auth.*;
import com.example.editor.remote.storage.RemoteStorageProviderType;

/** API de conexiones externas; jamás devuelve access_token, refresh_token ni client_secret. */
@RestController
@RequestMapping("/api/remote-auth")
public class RemoteAuthController {
    private final RemoteAuthService auth;private final RemoteRequestGuard guard;private final RemoteOAuthProviderSetupService providerSetup;
    /** Descripción: Inicializa una instancia de RemoteAuthController con sus dependencias y datos de entrada. Entradas: auth: dato de entrada necesario para la operación; guard: dato de entrada necesario para la operación; providerSetup: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public RemoteAuthController(RemoteAuthService auth,RemoteRequestGuard guard,RemoteOAuthProviderSetupService providerSetup){this.auth=auth;this.guard=guard;this.providerSetup=providerSetup;}

    @GetMapping("/security-context") public ResponseEntity<RemoteRequestSecurityContext> securityContext(){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(guard.context());}
    @GetMapping("/profiles") public ResponseEntity<List<RemoteAuthProfileDescriptor>> profiles(){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(auth.profiles());}
    @GetMapping("/provider-setups") public ResponseEntity<List<RemoteOAuthProviderSetupDescriptor>> providerSetups(){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(providerSetup.list());}
    @GetMapping("/provider-setups/{provider}/registration-kit") public ResponseEntity<RemoteOAuthRegistrationKit> providerRegistrationKit(@PathVariable RemoteStorageProviderType provider){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(providerSetup.registrationKit(provider));}
    @PutMapping("/provider-setups/{provider}") public RemoteOAuthProviderSetupDescriptor updateProviderSetup(@PathVariable RemoteStorageProviderType provider,@RequestBody RemoteOAuthProviderSetupRequest request,@RequestHeader(value="X-Hydra-Request-Token",required=false) String token,@RequestHeader(value="Sec-Fetch-Site",required=false) String fetchSite){guard.verify(token,fetchSite);return providerSetup.update(provider,request);}
    @DeleteMapping("/provider-setups/{provider}") public RemoteOAuthProviderSetupDescriptor resetProviderSetup(@PathVariable RemoteStorageProviderType provider,@RequestHeader(value="X-Hydra-Request-Token",required=false) String token,@RequestHeader(value="Sec-Fetch-Site",required=false) String fetchSite){guard.verify(token,fetchSite);return providerSetup.reset(provider);}

    @PostMapping("/start") public RemoteAuthStartResponse start(@RequestBody RemoteAuthStartRequest request,@RequestHeader(value="X-Hydra-Request-Token",required=false) String token,@RequestHeader(value="Sec-Fetch-Site",required=false) String fetchSite){guard.verify(token,fetchSite);return auth.start(request);}
    @PostMapping("/device-poll") public RemoteAuthPollResponse poll(@RequestBody RemoteAuthPollRequest request,@RequestHeader(value="X-Hydra-Request-Token",required=false) String token,@RequestHeader(value="Sec-Fetch-Site",required=false) String fetchSite){guard.verify(token,fetchSite);return auth.poll(request);}
    @DeleteMapping("/connection") public void disconnect(@RequestParam String profileId,@RequestHeader(value="X-Hydra-Request-Token",required=false) String token,@RequestHeader(value="Sec-Fetch-Site",required=false) String fetchSite){guard.verify(token,fetchSite);auth.disconnect(profileId);}

    @GetMapping(value="/callback",produces=MediaType.TEXT_HTML_VALUE)
    /** Descripción: Ejecuta la operación «callback» correspondiente a la responsabilidad de RemoteAuthController. Entradas: state: dato de entrada necesario para la operación; code: dato de entrada necesario para la operación; error: dato de entrada necesario para la operación; errorDescription: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<String> callback(@RequestParam String state,@RequestParam(required=false) String code,@RequestParam(required=false) String error,@RequestParam(name="error_description",required=false) String errorDescription){
        String language="en";
        try {language=auth.pendingLanguage(state);} catch(RuntimeException ignored){/* Invalid/expired state: keep safe English fallback. */}
        boolean spanish="es".equals(language);String title=spanish?"Conexión externa de HYDRA":"HYDRA external connection";String message;boolean success=true;
        try {
            RemoteAuthConnectionDescriptor connection=auth.complete(state,code,error,errorDescription);String account=escape(connection.accountLabel().isBlank()?connection.provider().name():connection.accountLabel());
            message=spanish?"Conexión completada para "+account+". Puedes cerrar esta pestaña y volver a HYDRA.":"Connection completed for "+account+". You can close this tab and return to HYDRA.";
        } catch(RuntimeException exception){success=false;message=spanish?"No se pudo completar la conexión externa. Vuelve a HYDRA e inténtalo de nuevo.":"The external connection could not be completed. Return to HYDRA and try again.";}
        String heading=success?(spanish?"Conectado":"Connected"):(spanish?"La conexión ha fallado":"Connection failed");
        String html="<!doctype html><html lang=\""+language+"\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>"+title+"</title></head><body><main><h1>"+heading+"</h1><p>"+message+"</p></main></body></html>";
        return ResponseEntity.status(success?200:400).contentType(new MediaType("text","html",StandardCharsets.UTF_8)).cacheControl(CacheControl.noStore()).header(HttpHeaders.PRAGMA,"no-cache").header("Content-Security-Policy","default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'").header("Referrer-Policy","no-referrer").header("X-Content-Type-Options","nosniff").header("Cross-Origin-Opener-Policy","same-origin").header("Cross-Origin-Resource-Policy","same-origin").header("Permissions-Policy","camera=(), microphone=(), geolocation=()").body(html);
    }
    /** Descripción: Ejecuta la operación «escape» correspondiente a la responsabilidad de RemoteAuthController. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String escape(String value){return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
}
