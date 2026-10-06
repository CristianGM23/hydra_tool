package com.example.editor.config.governance;

import java.net.InetAddress;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Optional local-only non-secret technical Settings API. */
@RestController
@RequestMapping("/api/system/technical-settings")
@ConditionalOnProperty(prefix="hydra.configuration", name="technical-settings-enabled", havingValue="true", matchIfMissing=true)
public class HydraTechnicalSettingsController {
    private final HydraTechnicalSettingsService service;
    /** Descripción: Inicializa una instancia de HydraTechnicalSettingsController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public HydraTechnicalSettingsController(HydraTechnicalSettingsService service){this.service=service;}

    @GetMapping
    /** Descripción: Obtiene la información correspondiente a «get» dentro de HydraTechnicalSettingsController. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<HydraTechnicalSettingsSnapshot> get(HttpServletRequest request){requireLocal(request);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.snapshot());}

    @PatchMapping
    /** Descripción: Actualiza el estado gestionado por HydraTechnicalSettingsController mediante la operación «update». Entradas: update: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<HydraTechnicalSettingsSnapshot> update(@RequestBody HydraTechnicalSettingsUpdate update,HttpServletRequest request){requireLocal(request);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.update(update.values()));}

    /** Descripción: Valida las condiciones necesarias para «requireLocal» y rechaza entradas no válidas. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void requireLocal(HttpServletRequest request){
        try {
            InetAddress remote=InetAddress.getByName(request.getRemoteAddr());
            if(!remote.isLoopbackAddress() && !remote.isAnyLocalAddress()) throw new IllegalArgumentException("Technical settings are available only from the local machine");
        } catch(java.net.UnknownHostException exception) { throw new IllegalArgumentException("Could not validate technical-settings client address",exception); }
    }
}
