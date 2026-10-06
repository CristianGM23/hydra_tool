package com.example.editor.provenance;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configures bounded, local-only storage for AV distribution evidence. */
@Component
@ConfigurationProperties(prefix = "hydra.provenance.distributions")
public class HydraDistributionProvenanceProperties {
    private String storageRoot = "./data/provenance/distributions";
    private int maximumReports = 200;

    /** Descripción: Obtiene la información correspondiente a «getStorageRoot» dentro de HydraDistributionProvenanceProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getStorageRoot(){return storageRoot;}
    /** Descripción: Actualiza el estado gestionado por HydraDistributionProvenanceProperties mediante la operación «setStorageRoot». Entradas: storageRoot: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setStorageRoot(String storageRoot){this.storageRoot = text(storageRoot, "./data/provenance/distributions");}
    /** Descripción: Obtiene la información correspondiente a «getMaximumReports» dentro de HydraDistributionProvenanceProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumReports(){return maximumReports;}
    /** Descripción: Actualiza el estado gestionado por HydraDistributionProvenanceProperties mediante la operación «setMaximumReports». Entradas: maximumReports: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumReports(int maximumReports){this.maximumReports = Math.max(1, Math.min(10_000, maximumReports));}
    /** Descripción: Ejecuta la operación «storageRootPath» correspondiente a la responsabilidad de HydraDistributionProvenanceProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Path storageRootPath(){return Path.of(storageRoot).toAbsolutePath().normalize();}

    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de HydraDistributionProvenanceProperties. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value, String fallback){return value == null || value.isBlank() ? fallback : value.trim();}
}
