package com.example.editor.registry.federation;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Local-only BB source and synchronization evidence limits. */
@ConfigurationProperties(prefix="hydra.registry.federation")
public class HydraCatalogFederationProperties {
    private Path storageRoot=Path.of("./data/registry/federation"); private int maximumSources=100; private int maximumReports=300;
    /** Descripción: Obtiene la información correspondiente a «getStorageRoot» dentro de HydraCatalogFederationProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Path getStorageRoot(){return storageRoot;} /** Descripción: Actualiza el estado gestionado por HydraCatalogFederationProperties mediante la operación «setStorageRoot». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setStorageRoot(Path value){storageRoot=value;}
    /** Descripción: Obtiene la información correspondiente a «getMaximumSources» dentro de HydraCatalogFederationProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumSources(){return maximumSources;} /** Descripción: Actualiza el estado gestionado por HydraCatalogFederationProperties mediante la operación «setMaximumSources». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumSources(int value){maximumSources=Math.max(1,value);}
    /** Descripción: Obtiene la información correspondiente a «getMaximumReports» dentro de HydraCatalogFederationProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumReports(){return maximumReports;} /** Descripción: Actualiza el estado gestionado por HydraCatalogFederationProperties mediante la operación «setMaximumReports». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumReports(int value){maximumReports=Math.max(1,value);}
}
