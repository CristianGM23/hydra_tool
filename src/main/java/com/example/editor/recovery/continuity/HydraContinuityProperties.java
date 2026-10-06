package com.example.editor.recovery.continuity;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** BE local continuity plan/report retention. */
@ConfigurationProperties(prefix="hydra.recovery.continuity")
public class HydraContinuityProperties {
    private Path storageRoot=Path.of("./data/recovery/continuity"); private int maximumPlans=100; private int maximumReports=500;
    /** Descripción: Obtiene la información correspondiente a «getStorageRoot» dentro de HydraContinuityProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Path getStorageRoot(){return storageRoot;} /** Descripción: Actualiza el estado gestionado por HydraContinuityProperties mediante la operación «setStorageRoot». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setStorageRoot(Path value){storageRoot=value;}
    /** Descripción: Obtiene la información correspondiente a «getMaximumPlans» dentro de HydraContinuityProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumPlans(){return maximumPlans;} /** Descripción: Actualiza el estado gestionado por HydraContinuityProperties mediante la operación «setMaximumPlans». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumPlans(int value){maximumPlans=Math.max(1,value);}
    /** Descripción: Obtiene la información correspondiente a «getMaximumReports» dentro de HydraContinuityProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumReports(){return maximumReports;} /** Descripción: Actualiza el estado gestionado por HydraContinuityProperties mediante la operación «setMaximumReports». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumReports(int value){maximumReports=Math.max(1,value);}
}
