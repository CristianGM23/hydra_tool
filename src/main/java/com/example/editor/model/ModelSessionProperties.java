package com.example.editor.model;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configura los límites de las sesiones de modelos abiertas en memoria.
 *
 * La configuración es deliberadamente local a la JVM. El futuro almacenamiento
 * compartido podrá sustituir esta política sin acoplar ModelDocumentService a
 * usuarios, workspaces o una base de datos concreta.
 */
@Component
@ConfigurationProperties(prefix = "dsl.models.sessions")
public class ModelSessionProperties {
    private int maxActive = 128;
    private Duration idleTimeout = Duration.ofHours(2);

    /** Descripción: Obtiene la información correspondiente a «getMaxActive» dentro de ModelSessionProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaxActive() {
        return maxActive;
    }

    /** Descripción: Actualiza el estado gestionado por ModelSessionProperties mediante la operación «setMaxActive». Entradas: maxActive: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaxActive(int maxActive) {
        this.maxActive = Math.max(1, maxActive);
    }

    /** Descripción: Obtiene la información correspondiente a «getIdleTimeout» dentro de ModelSessionProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Duration getIdleTimeout() {
        return idleTimeout;
    }

    /** Descripción: Actualiza el estado gestionado por ModelSessionProperties mediante la operación «setIdleTimeout». Entradas: idleTimeout: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setIdleTimeout(Duration idleTimeout) {
        if(idleTimeout == null || idleTimeout.isNegative() || idleTimeout.isZero()) {
            this.idleTimeout = Duration.ofHours(2);
            return;
        }
        this.idleTimeout = idleTimeout;
    }
}
