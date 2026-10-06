package com.example.editor.integration.injection;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Límites y raíz permitida para aplicar integraciones tecnológicas locales. */
@Component
@ConfigurationProperties(prefix = "dsl.integrations.injection")
public class TechnologyInjectionProperties {
    private boolean enabled = true;
    private Path allowedRoot = Path.of("./data/injection-targets");
    private int maximumFiles = 4096;
    private long maximumTotalBytes = 134217728L;
    private long maximumFileBytes = 33554432L;

    /** Descripción: Evalúa la condición «isEnabled» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isEnabled(){return enabled;}
    /** Descripción: Actualiza el estado gestionado por TechnologyInjectionProperties mediante la operación «setEnabled». Entradas: enabled: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setEnabled(boolean enabled){this.enabled = enabled;}
    /** Descripción: Obtiene la información correspondiente a «getAllowedRoot» dentro de TechnologyInjectionProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Path getAllowedRoot(){return allowedRoot;}
    /** Descripción: Actualiza el estado gestionado por TechnologyInjectionProperties mediante la operación «setAllowedRoot». Entradas: allowedRoot: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setAllowedRoot(Path allowedRoot){this.allowedRoot = allowedRoot;}
    /** Descripción: Obtiene la información correspondiente a «getMaximumFiles» dentro de TechnologyInjectionProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumFiles(){return maximumFiles;}
    /** Descripción: Actualiza el estado gestionado por TechnologyInjectionProperties mediante la operación «setMaximumFiles». Entradas: maximumFiles: archivo o contenido que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumFiles(int maximumFiles){this.maximumFiles = maximumFiles;}
    /** Descripción: Obtiene la información correspondiente a «getMaximumTotalBytes» dentro de TechnologyInjectionProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public long getMaximumTotalBytes(){return maximumTotalBytes;}
    /** Descripción: Actualiza el estado gestionado por TechnologyInjectionProperties mediante la operación «setMaximumTotalBytes». Entradas: maximumTotalBytes: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumTotalBytes(long maximumTotalBytes){this.maximumTotalBytes = maximumTotalBytes;}
    /** Descripción: Obtiene la información correspondiente a «getMaximumFileBytes» dentro de TechnologyInjectionProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public long getMaximumFileBytes(){return maximumFileBytes;}
    /** Descripción: Actualiza el estado gestionado por TechnologyInjectionProperties mediante la operación «setMaximumFileBytes». Entradas: maximumFileBytes: archivo o contenido que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumFileBytes(long maximumFileBytes){this.maximumFileBytes = maximumFileBytes;}
}
