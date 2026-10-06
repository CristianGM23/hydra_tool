package com.example.editor.standalone;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Límites y valores por defecto del generador web independiente. */
@Component
@ConfigurationProperties(prefix = "dsl.standalone.editors")
public class StandaloneEditorGenerationProperties {
    private boolean enabled = true;
    private long maximumArchiveBytes = 100L * 1024L * 1024L;
    private long maximumGeneratedModelBytes = 10L * 1024L * 1024L;
    private int maximumEntries = 512;
    private int defaultPort = 4173;
    private String defaultRuntimeBaseUrl = "http://localhost:8081";
    /** Descripción: Evalúa la condición «isEnabled» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isEnabled() { return enabled; }
    /** Descripción: Actualiza el estado gestionado por StandaloneEditorGenerationProperties mediante la operación «setEnabled». Entradas: enabled: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setEnabled(boolean enabled) { this.enabled = enabled; }
    /** Descripción: Obtiene la información correspondiente a «getMaximumArchiveBytes» dentro de StandaloneEditorGenerationProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public long getMaximumArchiveBytes() { return maximumArchiveBytes; }
    /** Descripción: Actualiza el estado gestionado por StandaloneEditorGenerationProperties mediante la operación «setMaximumArchiveBytes». Entradas: maximumArchiveBytes: archivo o contenido que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumArchiveBytes(long maximumArchiveBytes) { this.maximumArchiveBytes = maximumArchiveBytes; }
    /** Descripción: Obtiene la información correspondiente a «getMaximumGeneratedModelBytes» dentro de StandaloneEditorGenerationProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public long getMaximumGeneratedModelBytes() { return maximumGeneratedModelBytes; }
    /** Descripción: Actualiza el estado gestionado por StandaloneEditorGenerationProperties mediante la operación «setMaximumGeneratedModelBytes». Entradas: maximumGeneratedModelBytes: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumGeneratedModelBytes(long maximumGeneratedModelBytes) { this.maximumGeneratedModelBytes = maximumGeneratedModelBytes; }
    /** Descripción: Obtiene la información correspondiente a «getMaximumEntries» dentro de StandaloneEditorGenerationProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumEntries() { return maximumEntries; }
    /** Descripción: Actualiza el estado gestionado por StandaloneEditorGenerationProperties mediante la operación «setMaximumEntries». Entradas: maximumEntries: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumEntries(int maximumEntries) { this.maximumEntries = maximumEntries; }
    /** Descripción: Obtiene la información correspondiente a «getDefaultPort» dentro de StandaloneEditorGenerationProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getDefaultPort() { return defaultPort; }
    /** Descripción: Actualiza el estado gestionado por StandaloneEditorGenerationProperties mediante la operación «setDefaultPort». Entradas: defaultPort: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setDefaultPort(int defaultPort) { this.defaultPort = defaultPort; }
    /** Descripción: Obtiene la información correspondiente a «getDefaultRuntimeBaseUrl» dentro de StandaloneEditorGenerationProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getDefaultRuntimeBaseUrl() { return defaultRuntimeBaseUrl; }
    /** Descripción: Actualiza el estado gestionado por StandaloneEditorGenerationProperties mediante la operación «setDefaultRuntimeBaseUrl». Entradas: defaultRuntimeBaseUrl: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setDefaultRuntimeBaseUrl(String defaultRuntimeBaseUrl) { this.defaultRuntimeBaseUrl = defaultRuntimeBaseUrl; }
}
