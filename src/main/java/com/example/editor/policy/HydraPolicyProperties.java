package com.example.editor.policy;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** AY policy and revocation storage. */
@ConfigurationProperties(prefix="hydra.policies")
public class HydraPolicyProperties {
    private Path storageRoot=Path.of("./data/policies"); private int maximumRules=200; private int maximumRevocations=1000;
    /** Descripción: Obtiene la información correspondiente a «getStorageRoot» dentro de HydraPolicyProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Path getStorageRoot(){return storageRoot;} /** Descripción: Actualiza el estado gestionado por HydraPolicyProperties mediante la operación «setStorageRoot». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setStorageRoot(Path value){storageRoot=value;}
    /** Descripción: Obtiene la información correspondiente a «getMaximumRules» dentro de HydraPolicyProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumRules(){return maximumRules;} /** Descripción: Actualiza el estado gestionado por HydraPolicyProperties mediante la operación «setMaximumRules». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumRules(int value){maximumRules=Math.max(1,value);}
    /** Descripción: Obtiene la información correspondiente a «getMaximumRevocations» dentro de HydraPolicyProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumRevocations(){return maximumRevocations;} /** Descripción: Actualiza el estado gestionado por HydraPolicyProperties mediante la operación «setMaximumRevocations». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumRevocations(int value){maximumRevocations=Math.max(1,value);}
}
