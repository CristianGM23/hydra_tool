package com.example.editor.security.input;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Límites transversales de entrada HTTP y formularios.
 *
 * Los límites son deliberadamente conservadores para JSON/formularios normales,
 * mientras que los uploads multipart mantienen un presupuesto independiente y
 * mayor para no romper importaciones legítimas de proyectos o update sites.
 */
@ConfigurationProperties(prefix = "hydra.security.input")
public class HydraInputSecurityProperties {
    private long maxStructuredRequestBytes = 16L * 1024L * 1024L;
    private long maxMultipartRequestBytes = 512L * 1024L * 1024L;
    private int maxRequestUriCharacters = 4096;
    private int maxQueryCharacters = 8192;
    private int maxHeaderCharacters = 8192;
    private int maxParameterCharacters = 8192;
    private int maxPlainTextCharacters = 65536;
    private int maxGenericStringCharacters = 12 * 1024 * 1024;
    private int maxPayloadNodes = 250000;

    /** Descripción: Obtiene la información correspondiente a «getMaxStructuredRequestBytes» dentro de HydraInputSecurityProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public long getMaxStructuredRequestBytes() { return maxStructuredRequestBytes; }
    /** Descripción: Actualiza el estado gestionado por HydraInputSecurityProperties mediante la operación «setMaxStructuredRequestBytes». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaxStructuredRequestBytes(long value) { maxStructuredRequestBytes = positive(value, "maxStructuredRequestBytes"); }
    /** Descripción: Obtiene la información correspondiente a «getMaxMultipartRequestBytes» dentro de HydraInputSecurityProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public long getMaxMultipartRequestBytes() { return maxMultipartRequestBytes; }
    /** Descripción: Actualiza el estado gestionado por HydraInputSecurityProperties mediante la operación «setMaxMultipartRequestBytes». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaxMultipartRequestBytes(long value) { maxMultipartRequestBytes = positive(value, "maxMultipartRequestBytes"); }
    /** Descripción: Obtiene la información correspondiente a «getMaxRequestUriCharacters» dentro de HydraInputSecurityProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaxRequestUriCharacters() { return maxRequestUriCharacters; }
    /** Descripción: Actualiza el estado gestionado por HydraInputSecurityProperties mediante la operación «setMaxRequestUriCharacters». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaxRequestUriCharacters(int value) { maxRequestUriCharacters = positive(value, "maxRequestUriCharacters"); }
    /** Descripción: Obtiene la información correspondiente a «getMaxQueryCharacters» dentro de HydraInputSecurityProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaxQueryCharacters() { return maxQueryCharacters; }
    /** Descripción: Actualiza el estado gestionado por HydraInputSecurityProperties mediante la operación «setMaxQueryCharacters». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaxQueryCharacters(int value) { maxQueryCharacters = positive(value, "maxQueryCharacters"); }
    /** Descripción: Obtiene la información correspondiente a «getMaxHeaderCharacters» dentro de HydraInputSecurityProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaxHeaderCharacters() { return maxHeaderCharacters; }
    /** Descripción: Actualiza el estado gestionado por HydraInputSecurityProperties mediante la operación «setMaxHeaderCharacters». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaxHeaderCharacters(int value) { maxHeaderCharacters = positive(value, "maxHeaderCharacters"); }
    /** Descripción: Obtiene la información correspondiente a «getMaxParameterCharacters» dentro de HydraInputSecurityProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaxParameterCharacters() { return maxParameterCharacters; }
    /** Descripción: Actualiza el estado gestionado por HydraInputSecurityProperties mediante la operación «setMaxParameterCharacters». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaxParameterCharacters(int value) { maxParameterCharacters = positive(value, "maxParameterCharacters"); }
    /** Descripción: Obtiene la información correspondiente a «getMaxPlainTextCharacters» dentro de HydraInputSecurityProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaxPlainTextCharacters() { return maxPlainTextCharacters; }
    /** Descripción: Actualiza el estado gestionado por HydraInputSecurityProperties mediante la operación «setMaxPlainTextCharacters». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaxPlainTextCharacters(int value) { maxPlainTextCharacters = positive(value, "maxPlainTextCharacters"); }
    /** Descripción: Obtiene la información correspondiente a «getMaxGenericStringCharacters» dentro de HydraInputSecurityProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaxGenericStringCharacters() { return maxGenericStringCharacters; }
    /** Descripción: Actualiza el estado gestionado por HydraInputSecurityProperties mediante la operación «setMaxGenericStringCharacters». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaxGenericStringCharacters(int value) { maxGenericStringCharacters = positive(value, "maxGenericStringCharacters"); }
    /** Descripción: Obtiene la información correspondiente a «getMaxPayloadNodes» dentro de HydraInputSecurityProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaxPayloadNodes() { return maxPayloadNodes; }
    /** Descripción: Actualiza el estado gestionado por HydraInputSecurityProperties mediante la operación «setMaxPayloadNodes». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaxPayloadNodes(int value) { maxPayloadNodes = positive(value, "maxPayloadNodes"); }

    /** Descripción: Ejecuta la operación «positive» correspondiente a la responsabilidad de HydraInputSecurityProperties. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static int positive(int value, String field) {
        if(value <= 0) throw new IllegalArgumentException(field + " must be greater than zero");
        return value;
    }

    /** Descripción: Ejecuta la operación «positive» correspondiente a la responsabilidad de HydraInputSecurityProperties. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static long positive(long value, String field) {
        if(value <= 0) throw new IllegalArgumentException(field + " must be greater than zero");
        return value;
    }
}
