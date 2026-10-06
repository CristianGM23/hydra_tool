package com.example.editor.portability.archive;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configuración acotada y segura del formato físico de paquetes HYDRA. */
@Component
@ConfigurationProperties(prefix = "dsl.portability.packages")
public class CapabilityPackageArchiveProperties {
    private boolean enabled = true;
    private Path cacheDirectory = Path.of("./data/capability-packages/cache");
    private long maximumArchiveBytes = 134_217_728L;
    private long maximumEntryBytes = 33_554_432L;
    private int maximumEntries = 4096;
    private boolean cacheEnabled = true;
    private boolean requireSignature = false;
    private String signatureSecret = "";
    private String signatureKeyId = "local";

    /** Descripción: Evalúa la condición «isEnabled» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isEnabled() { return enabled; }
    /** Descripción: Actualiza el estado gestionado por CapabilityPackageArchiveProperties mediante la operación «setEnabled». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setEnabled(boolean value) { enabled = value; }
    /** Descripción: Obtiene la información correspondiente a «getCacheDirectory» dentro de CapabilityPackageArchiveProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Path getCacheDirectory() { return cacheDirectory; }
    /** Descripción: Actualiza el estado gestionado por CapabilityPackageArchiveProperties mediante la operación «setCacheDirectory». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setCacheDirectory(Path value) { cacheDirectory = value; }
    /** Descripción: Obtiene la información correspondiente a «getMaximumArchiveBytes» dentro de CapabilityPackageArchiveProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public long getMaximumArchiveBytes() { return maximumArchiveBytes; }
    /** Descripción: Actualiza el estado gestionado por CapabilityPackageArchiveProperties mediante la operación «setMaximumArchiveBytes». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumArchiveBytes(long value) { maximumArchiveBytes = Math.max(1_048_576L, value); }
    /** Descripción: Obtiene la información correspondiente a «getMaximumEntryBytes» dentro de CapabilityPackageArchiveProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public long getMaximumEntryBytes() { return maximumEntryBytes; }
    /** Descripción: Actualiza el estado gestionado por CapabilityPackageArchiveProperties mediante la operación «setMaximumEntryBytes». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumEntryBytes(long value) { maximumEntryBytes = Math.max(1_024L, value); }
    /** Descripción: Obtiene la información correspondiente a «getMaximumEntries» dentro de CapabilityPackageArchiveProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumEntries() { return maximumEntries; }
    /** Descripción: Actualiza el estado gestionado por CapabilityPackageArchiveProperties mediante la operación «setMaximumEntries». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumEntries(int value) { maximumEntries = Math.max(16, value); }
    /** Descripción: Evalúa la condición «isCacheEnabled» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isCacheEnabled() { return cacheEnabled; }
    /** Descripción: Actualiza el estado gestionado por CapabilityPackageArchiveProperties mediante la operación «setCacheEnabled». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setCacheEnabled(boolean value) { cacheEnabled = value; }
    /** Descripción: Evalúa la condición «isRequireSignature» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isRequireSignature() { return requireSignature; }
    /** Descripción: Actualiza el estado gestionado por CapabilityPackageArchiveProperties mediante la operación «setRequireSignature». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setRequireSignature(boolean value) { requireSignature = value; }
    /** Descripción: Obtiene la información correspondiente a «getSignatureSecret» dentro de CapabilityPackageArchiveProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getSignatureSecret() { return signatureSecret; }
    /** Descripción: Actualiza el estado gestionado por CapabilityPackageArchiveProperties mediante la operación «setSignatureSecret». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setSignatureSecret(String value) { signatureSecret = value == null ? "" : value; }
    /** Descripción: Obtiene la información correspondiente a «getSignatureKeyId» dentro de CapabilityPackageArchiveProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getSignatureKeyId() { return signatureKeyId; }
    /** Descripción: Actualiza el estado gestionado por CapabilityPackageArchiveProperties mediante la operación «setSignatureKeyId». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setSignatureKeyId(String value) { signatureKeyId = value == null || value.isBlank() ? "local" : value.trim(); }
}
