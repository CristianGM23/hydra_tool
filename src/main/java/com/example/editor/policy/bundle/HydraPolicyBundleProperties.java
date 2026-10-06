package com.example.editor.policy.bundle;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** BD bundle and simulation retention. */
@ConfigurationProperties(prefix="hydra.policies.bundles")
public class HydraPolicyBundleProperties {
    private Path storageRoot=Path.of("./data/policies/bundles"); private int maximumVersions=300; private int maximumSimulations=500;
    /** Descripción: Obtiene la información correspondiente a «getStorageRoot» dentro de HydraPolicyBundleProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Path getStorageRoot(){return storageRoot;} /** Descripción: Actualiza el estado gestionado por HydraPolicyBundleProperties mediante la operación «setStorageRoot». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setStorageRoot(Path value){storageRoot=value;}
    /** Descripción: Obtiene la información correspondiente a «getMaximumVersions» dentro de HydraPolicyBundleProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumVersions(){return maximumVersions;} /** Descripción: Actualiza el estado gestionado por HydraPolicyBundleProperties mediante la operación «setMaximumVersions». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumVersions(int value){maximumVersions=Math.max(1,value);}
    /** Descripción: Obtiene la información correspondiente a «getMaximumSimulations» dentro de HydraPolicyBundleProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumSimulations(){return maximumSimulations;} /** Descripción: Actualiza el estado gestionado por HydraPolicyBundleProperties mediante la operación «setMaximumSimulations». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumSimulations(int value){maximumSimulations=Math.max(1,value);}
}
