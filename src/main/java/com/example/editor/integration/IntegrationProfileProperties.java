package com.example.editor.integration;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
/** Configura la persistencia de perfiles de integración. */
@Component
@ConfigurationProperties(prefix = "dsl.integrations")
public class IntegrationProfileProperties {
    private boolean enabled = true;
    private Path directory = Path.of("./data/integrations");
    private String fileSuffix = ".integration.json";
    private boolean useEtag = true;
    /** Descripción: Evalúa la condición «isEnabled» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isEnabled() { return enabled; }
    /** Descripción: Actualiza el estado gestionado por IntegrationProfileProperties mediante la operación «setEnabled». Entradas: enabled: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setEnabled(boolean enabled) { this.enabled = enabled; }
    /** Descripción: Obtiene la información correspondiente a «getDirectory» dentro de IntegrationProfileProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Path getDirectory() { return directory; }
    /** Descripción: Actualiza el estado gestionado por IntegrationProfileProperties mediante la operación «setDirectory». Entradas: directory: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setDirectory(Path directory) { this.directory = directory; }
    /** Descripción: Obtiene la información correspondiente a «getFileSuffix» dentro de IntegrationProfileProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getFileSuffix() { return fileSuffix; }
    /** Descripción: Actualiza el estado gestionado por IntegrationProfileProperties mediante la operación «setFileSuffix». Entradas: fileSuffix: archivo o contenido que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setFileSuffix(String fileSuffix) { this.fileSuffix = fileSuffix; }
    /** Descripción: Evalúa la condición «isUseEtag» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isUseEtag() { return useEtag; }
    /** Descripción: Actualiza el estado gestionado por IntegrationProfileProperties mediante la operación «setUseEtag». Entradas: useEtag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setUseEtag(boolean useEtag) { this.useEtag = useEtag; }
}
