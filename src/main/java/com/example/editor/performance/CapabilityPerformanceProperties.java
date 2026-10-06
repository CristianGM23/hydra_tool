package com.example.editor.performance;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Storage and retention for comparable capability performance reports. */
@Component
@ConfigurationProperties(prefix = "dsl.performance.capabilities")
public class CapabilityPerformanceProperties {
    private String storageRoot = "./data/performance/capabilities";
    private int maximumReports = 200;
    /** Descripción: Obtiene la información correspondiente a «getStorageRoot» dentro de CapabilityPerformanceProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getStorageRoot(){return storageRoot;}
    /** Descripción: Actualiza el estado gestionado por CapabilityPerformanceProperties mediante la operación «setStorageRoot». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setStorageRoot(String value){storageRoot = value == null || value.isBlank() ? "./data/performance/capabilities" : value;}
    /** Descripción: Ejecuta la operación «storageRootPath» correspondiente a la responsabilidad de CapabilityPerformanceProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Path storageRootPath(){return Path.of(storageRoot).toAbsolutePath().normalize();}
    /** Descripción: Obtiene la información correspondiente a «getMaximumReports» dentro de CapabilityPerformanceProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumReports(){return maximumReports;}
    /** Descripción: Actualiza el estado gestionado por CapabilityPerformanceProperties mediante la operación «setMaximumReports». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumReports(int value){maximumReports = Math.max(1, Math.min(5000, value));}
}
