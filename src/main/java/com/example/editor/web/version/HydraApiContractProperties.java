package com.example.editor.web.version;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Public compatibility marker for the HTTP API without forcing a path migration. */
@ConfigurationProperties(prefix = "hydra.api")
public class HydraApiContractProperties {
    private String version = "1";
    /** Descripción: Obtiene la información correspondiente a «getVersion» dentro de HydraApiContractProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getVersion() { return version; }
    /** Descripción: Actualiza el estado gestionado por HydraApiContractProperties mediante la operación «setVersion». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setVersion(String value) { version = value == null || value.isBlank() ? "1" : value.trim(); }
}
