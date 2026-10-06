package com.example.editor.artifact.registry;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Límites configurables del inventario de artefactos para mantener acotados memoria, E/S y tiempo de escaneo. */
@ConfigurationProperties(prefix = "hydra.artifacts")
public class HydraArtifactScannerProperties {
    private int maximumScanFiles = 8192;
    private long maximumScanFileBytes = 33_554_432L;
    private long maximumScanTotalBytes = 268_435_456L;
    private int maximumScanDepth = 6;

    /** Descripción: Obtiene el máximo de ficheros regulares que puede aceptar un escaneo de artefactos. Entradas: no requiere parámetros. Salida: devuelve el límite configurado. */
    /** Descripción: Obtiene la información correspondiente a «getMaximumScanFiles» dentro de HydraArtifactScannerProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumScanFiles() { return maximumScanFiles; }
    /** Descripción: Configura el máximo de ficheros regulares admitidos por un escaneo. Entradas: value: límite solicitado. Salida: aplica un mínimo seguro de un fichero. */
    /** Descripción: Actualiza el estado gestionado por HydraArtifactScannerProperties mediante la operación «setMaximumScanFiles». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumScanFiles(int value) { maximumScanFiles = Math.max(1, value); }
    /** Descripción: Obtiene el máximo de bytes permitido para un único artefacto escaneado. Entradas: no requiere parámetros. Salida: devuelve el límite configurado. */
    /** Descripción: Obtiene la información correspondiente a «getMaximumScanFileBytes» dentro de HydraArtifactScannerProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public long getMaximumScanFileBytes() { return maximumScanFileBytes; }
    /** Descripción: Configura el máximo de bytes por artefacto escaneado. Entradas: value: presupuesto solicitado. Salida: aplica un mínimo seguro de 1 KiB. */
    /** Descripción: Actualiza el estado gestionado por HydraArtifactScannerProperties mediante la operación «setMaximumScanFileBytes». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumScanFileBytes(long value) { maximumScanFileBytes = Math.max(1024L, value); }
    /** Descripción: Obtiene el máximo acumulado de bytes de los artefactos aceptados en un escaneo. Entradas: no requiere parámetros. Salida: devuelve el presupuesto total configurado. */
    /** Descripción: Obtiene la información correspondiente a «getMaximumScanTotalBytes» dentro de HydraArtifactScannerProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public long getMaximumScanTotalBytes() { return maximumScanTotalBytes; }
    /** Descripción: Configura el máximo acumulado de bytes aceptado por un escaneo. Entradas: value: presupuesto solicitado. Salida: aplica un mínimo seguro de 1 KiB sin depender del orden de binding de otras propiedades. */
    /** Descripción: Actualiza el estado gestionado por HydraArtifactScannerProperties mediante la operación «setMaximumScanTotalBytes». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumScanTotalBytes(long value) { maximumScanTotalBytes = Math.max(1024L, value); }
    /** Descripción: Obtiene la profundidad máxima del árbol que recorre el inventario. Entradas: no requiere parámetros. Salida: devuelve la profundidad configurada. */
    /** Descripción: Obtiene la información correspondiente a «getMaximumScanDepth» dentro de HydraArtifactScannerProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumScanDepth() { return maximumScanDepth; }
    /** Descripción: Configura la profundidad máxima del árbol de artefactos. Entradas: value: profundidad solicitada. Salida: restringe el valor al intervalo seguro 1..64. */
    /** Descripción: Actualiza el estado gestionado por HydraArtifactScannerProperties mediante la operación «setMaximumScanDepth». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumScanDepth(int value) { maximumScanDepth = Math.max(1, Math.min(64, value)); }
}
