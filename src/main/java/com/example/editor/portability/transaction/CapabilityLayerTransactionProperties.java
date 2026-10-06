package com.example.editor.portability.transaction;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Limits and journal location for portable multi-layer transactions. */
@Component
@ConfigurationProperties(prefix = "dsl.capability-transactions")
public class CapabilityLayerTransactionProperties {
    private String storageRoot = "./data/capability-transactions";
    private int maximumDocuments = 128;
    private long maximumBytes = 67_108_864L;
    /** Descripción: Obtiene la información correspondiente a «getStorageRoot» dentro de CapabilityLayerTransactionProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getStorageRoot(){return storageRoot;}
    /** Descripción: Actualiza el estado gestionado por CapabilityLayerTransactionProperties mediante la operación «setStorageRoot». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setStorageRoot(String value){storageRoot = value == null || value.isBlank() ? "./data/capability-transactions" : value;}
    /** Descripción: Ejecuta la operación «storageRootPath» correspondiente a la responsabilidad de CapabilityLayerTransactionProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Path storageRootPath(){return Path.of(storageRoot).toAbsolutePath().normalize();}
    /** Descripción: Obtiene la información correspondiente a «getMaximumDocuments» dentro de CapabilityLayerTransactionProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumDocuments(){return maximumDocuments;}
    /** Descripción: Actualiza el estado gestionado por CapabilityLayerTransactionProperties mediante la operación «setMaximumDocuments». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumDocuments(int value){maximumDocuments = Math.max(1, Math.min(2048, value));}
    /** Descripción: Obtiene la información correspondiente a «getMaximumBytes» dentro de CapabilityLayerTransactionProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public long getMaximumBytes(){return maximumBytes;}
    /** Descripción: Actualiza el estado gestionado por CapabilityLayerTransactionProperties mediante la operación «setMaximumBytes». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumBytes(long value){maximumBytes = Math.max(1024, value);}
}
