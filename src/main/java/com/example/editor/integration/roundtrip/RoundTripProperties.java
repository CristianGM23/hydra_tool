package com.example.editor.integration.roundtrip;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Límites de seguridad y memoria para inspección, preservación y resolución round-trip. */
@Component
@ConfigurationProperties(prefix="dsl.integrations.round-trip")
public class RoundTripProperties {
    private long maximumFileBytes=33_554_432L;

    /** Descripción: Obtiene el máximo de bytes que HYDRA inspecciona de un fichero nativo durante preview, preservación de regiones o resolución de conflictos. Entradas: no requiere parámetros. Salida: devuelve el presupuesto máximo por fichero. */ public long getMaximumFileBytes(){return maximumFileBytes;}
    /** Descripción: Configura el máximo de bytes por fichero para mantener acotados memoria y tiempo del flujo round-trip. Entradas: value: presupuesto solicitado. Salida: no devuelve valor; aplica un mínimo seguro de 1 KiB. */ public void setMaximumFileBytes(long value){maximumFileBytes=Math.max(1024L,value);}
}
