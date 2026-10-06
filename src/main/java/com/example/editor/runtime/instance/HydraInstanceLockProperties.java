package com.example.editor.runtime.instance;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for the process-wide lock that protects local HYDRA data. */
@ConfigurationProperties(prefix = "hydra.instance-lock")
public class HydraInstanceLockProperties {
    private boolean enabled = true;
    private Path path;

    /** Descripción: Evalúa la condición «isEnabled» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isEnabled() { return enabled; }
    /** Descripción: Actualiza el estado gestionado por HydraInstanceLockProperties mediante la operación «setEnabled». Entradas: enabled: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setEnabled(boolean enabled) { this.enabled = enabled; }
    /** Descripción: Obtiene la información correspondiente a «getPath» dentro de HydraInstanceLockProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Path getPath() { return path; }
    /** Descripción: Actualiza el estado gestionado por HydraInstanceLockProperties mediante la operación «setPath». Entradas: path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setPath(Path path) { if (path != null) this.path = path; }
}
