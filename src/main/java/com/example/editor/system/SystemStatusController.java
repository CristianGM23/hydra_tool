package com.example.editor.system;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.editor.product.HydraProductIdentity;

/**
 * Expone información básica sobre el estado de la aplicación.
 *
 * Este controlador permite comprobar mediante HTTP que el backend está
 * arrancado y responde correctamente.
 */
@RestController
@RequestMapping("/api/system")
public class SystemStatusController {

    private final String applicationName;

    /**
     * Descripción:
     * Crea el controlador utilizando el nombre configurado para la aplicación.
     *
     * Entrada:
     * @param applicationName es el valor de spring.application.name.
     *
     * Salida:
     * Crea una instancia de SystemStatusController.
     */
    /** Descripción: Inicializa una instancia de SystemStatusController con sus dependencias y datos de entrada. Entradas: applicationName: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public SystemStatusController(@Value("${spring.application.name}") String applicationName) {
        this.applicationName = applicationName;
    }

    /**
     * Descripción:
     * Devuelve el nombre y el estado actual de la aplicación.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * @return una respuesta con el nombre de la aplicación y el estado UP.
     */
    @GetMapping("/status")
    /** Descripción: Ejecuta la operación «status» correspondiente a la responsabilidad de SystemStatusController. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public SystemStatusResponse status() {
        return new SystemStatusResponse(applicationName, "UP", HydraProductIdentity.PRODUCT, HydraProductIdentity.ACRONYM);
    }

    /**
     * Representa la respuesta enviada por el endpoint de estado.
     *
     * @param application nombre de la aplicación.
     * @param status estado actual de la aplicación.
     */
    public record SystemStatusResponse(String application, String status, String product, String acronym) {
        /** Permite construir una respuesta de estado completando la identidad estable de HYDRA. */
        /** Descripción: Inicializa una instancia de SystemStatusResponse con sus dependencias y datos de entrada. Entradas: application: dato de entrada necesario para la operación; status: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public SystemStatusResponse(String application, String status) {
            this(application, status, HydraProductIdentity.PRODUCT, HydraProductIdentity.ACRONYM);
        }
    }
}