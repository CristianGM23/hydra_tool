package com.example.editor.config.governance;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Governs the optional, safe technical-settings surface. */
@ConfigurationProperties(prefix = "hydra.configuration")
public class HydraConfigurationGovernanceProperties {
    private boolean technicalSettingsEnabled = true;
    /** Descripción: Evalúa la condición «isTechnicalSettingsEnabled» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isTechnicalSettingsEnabled() { return technicalSettingsEnabled; }
    /** Descripción: Actualiza el estado gestionado por HydraConfigurationGovernanceProperties mediante la operación «setTechnicalSettingsEnabled». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setTechnicalSettingsEnabled(boolean value) { technicalSettingsEnabled = value; }
}
