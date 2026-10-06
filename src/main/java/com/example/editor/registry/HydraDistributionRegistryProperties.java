package com.example.editor.registry;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Local storage limits for the AW registry. */
@ConfigurationProperties(prefix="hydra.registry.distributions")
public class HydraDistributionRegistryProperties {
    private Path storageRoot = Path.of("./data/registry/distributions");
    private int maximumEntries = 500;
    /** Descripción: Obtiene la información correspondiente a «getStorageRoot» dentro de HydraDistributionRegistryProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Path getStorageRoot(){return storageRoot;}
    /** Descripción: Actualiza el estado gestionado por HydraDistributionRegistryProperties mediante la operación «setStorageRoot». Entradas: storageRoot: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setStorageRoot(Path storageRoot){this.storageRoot=storageRoot;}
    /** Descripción: Obtiene la información correspondiente a «getMaximumEntries» dentro de HydraDistributionRegistryProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumEntries(){return maximumEntries;}
    /** Descripción: Actualiza el estado gestionado por HydraDistributionRegistryProperties mediante la operación «setMaximumEntries». Entradas: maximumEntries: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumEntries(int maximumEntries){this.maximumEntries=Math.max(1,maximumEntries);}
}
