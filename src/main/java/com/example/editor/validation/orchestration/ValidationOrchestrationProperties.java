package com.example.editor.validation.orchestration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configuración del historial de Problems y de la política de bloqueo. */
@Component
@ConfigurationProperties(prefix = "dsl.validation.orchestration")
public class ValidationOrchestrationProperties {
    private boolean enabled = true;
    private int maxTrackedModels = 256;
    private boolean blockSaveOnErrors = true;
    private boolean blockExportOnErrors = true;

    /** Descripción: Evalúa la condición «isEnabled» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isEnabled() { return enabled; }
    /** Descripción: Actualiza el estado gestionado por ValidationOrchestrationProperties mediante la operación «setEnabled». Entradas: enabled: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setEnabled(boolean enabled) { this.enabled = enabled; }
    /** Descripción: Obtiene la información correspondiente a «getMaxTrackedModels» dentro de ValidationOrchestrationProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaxTrackedModels() { return maxTrackedModels; }
    /** Descripción: Actualiza el estado gestionado por ValidationOrchestrationProperties mediante la operación «setMaxTrackedModels». Entradas: maxTrackedModels: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaxTrackedModels(int maxTrackedModels) {
        if(maxTrackedModels < 1) throw new IllegalArgumentException("maxTrackedModels must be at least 1");
        this.maxTrackedModels = maxTrackedModels;
    }
    /** Descripción: Evalúa la condición «isBlockSaveOnErrors» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isBlockSaveOnErrors() { return blockSaveOnErrors; }
    /** Descripción: Actualiza el estado gestionado por ValidationOrchestrationProperties mediante la operación «setBlockSaveOnErrors». Entradas: blockSaveOnErrors: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setBlockSaveOnErrors(boolean blockSaveOnErrors) { this.blockSaveOnErrors = blockSaveOnErrors; }
    /** Descripción: Evalúa la condición «isBlockExportOnErrors» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isBlockExportOnErrors() { return blockExportOnErrors; }
    /** Descripción: Actualiza el estado gestionado por ValidationOrchestrationProperties mediante la operación «setBlockExportOnErrors». Entradas: blockExportOnErrors: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setBlockExportOnErrors(boolean blockExportOnErrors) { this.blockExportOnErrors = blockExportOnErrors; }
}
