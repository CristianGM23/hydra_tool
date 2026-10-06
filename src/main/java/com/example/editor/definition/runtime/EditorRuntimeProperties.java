package com.example.editor.definition.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Límites de memoria y métricas del compilador de editores. */
@Component
@ConfigurationProperties(prefix = "dsl.editors.runtime")
public class EditorRuntimeProperties {
    private int maximumCachedPlans = 256;
    /** Descripción: Obtiene la información correspondiente a «getMaximumCachedPlans» dentro de EditorRuntimeProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumCachedPlans() { return maximumCachedPlans; }
    /** Descripción: Actualiza el estado gestionado por EditorRuntimeProperties mediante la operación «setMaximumCachedPlans». Entradas: maximumCachedPlans: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumCachedPlans(int maximumCachedPlans) {
        this.maximumCachedPlans = Math.max(1, maximumCachedPlans);
    }
}
