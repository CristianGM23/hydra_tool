package com.example.editor.validation.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Opciones reemplazables del compilador, la caché, la ejecución y las proyecciones. */
@Component
@ConfigurationProperties(prefix = "dsl.validation.runtime")
public class ValidationRuntimeProperties {
    private boolean enabled = true;
    private int maxCachedPlans = 256;
    private boolean allowPartialPlanExecution = true;
    private boolean drawioEnabled = true;

    /** Descripción: Evalúa la condición «isEnabled» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isEnabled() { return enabled; }
    /** Descripción: Actualiza el estado gestionado por ValidationRuntimeProperties mediante la operación «setEnabled». Entradas: enabled: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setEnabled(boolean enabled) { this.enabled = enabled; }
    /** Descripción: Obtiene la información correspondiente a «getMaxCachedPlans» dentro de ValidationRuntimeProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaxCachedPlans() { return maxCachedPlans; }
    /** Descripción: Actualiza el estado gestionado por ValidationRuntimeProperties mediante la operación «setMaxCachedPlans». Entradas: maxCachedPlans: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaxCachedPlans(int maxCachedPlans) {
        if(maxCachedPlans < 1) throw new IllegalArgumentException("maxCachedPlans must be at least 1");
        this.maxCachedPlans = maxCachedPlans;
    }
    /** Descripción: Evalúa la condición «isAllowPartialPlanExecution» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isAllowPartialPlanExecution() { return allowPartialPlanExecution; }
    /** Descripción: Actualiza el estado gestionado por ValidationRuntimeProperties mediante la operación «setAllowPartialPlanExecution». Entradas: allowPartialPlanExecution: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setAllowPartialPlanExecution(boolean allowPartialPlanExecution) { this.allowPartialPlanExecution = allowPartialPlanExecution; }
    /** Descripción: Evalúa la condición «isDrawioEnabled» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isDrawioEnabled() { return drawioEnabled; }
    /** Descripción: Actualiza el estado gestionado por ValidationRuntimeProperties mediante la operación «setDrawioEnabled». Entradas: drawioEnabled: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setDrawioEnabled(boolean drawioEnabled) { this.drawioEnabled = drawioEnabled; }
}
