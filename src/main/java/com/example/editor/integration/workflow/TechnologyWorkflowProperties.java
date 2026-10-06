package com.example.editor.integration.workflow;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import com.example.editor.integration.IntegrationSupportLevel;

/** Pesos configurables para evaluar la paridad de los recorridos tecnológicos. */
@Component
@ConfigurationProperties(prefix = "dsl.integrations.workflow")
public class TechnologyWorkflowProperties {
    private int configureWeight = 5;
    private int analyzeWeight = 10;
    private int generateWeight = 20;
    private int verifyWeight = 15;
    private int previewWeight = 10;
    private int applyWeight = 10;
    private int executeWeight = 10;
    private int roundTripWeight = 15;
    private int exportWeight = 5;
    private int partialPercent = 65;
    private int externalPercent = 40;
    private int fullWorkflowThreshold = 85;
    private int operationalThreshold = 60;

    /** Descripción: Ejecuta la operación «weight» correspondiente a la responsabilidad de TechnologyWorkflowProperties. Entradas: stage: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public int weight(TechnologyWorkflowStage stage){
        return switch(stage){
            case CONFIGURE -> configureWeight;
            case ANALYZE -> analyzeWeight;
            case GENERATE -> generateWeight;
            case VERIFY -> verifyWeight;
            case PREVIEW -> previewWeight;
            case APPLY -> applyWeight;
            case EXECUTE -> executeWeight;
            case ROUND_TRIP -> roundTripWeight;
            case EXPORT -> exportWeight;
        };
    }

    /** Descripción: Ejecuta la operación «supportPercent» correspondiente a la responsabilidad de TechnologyWorkflowProperties. Entradas: level: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public int supportPercent(IntegrationSupportLevel level){
        return switch(level){
            case FULL -> 100;
            case PARTIAL -> partialPercent;
            case EXTERNAL -> externalPercent;
            case UNSUPPORTED -> 0;
        };
    }

    /** Descripción: Obtiene la información correspondiente a «getConfigureWeight» dentro de TechnologyWorkflowProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getConfigureWeight(){return configureWeight;}
    /** Descripción: Actualiza el estado gestionado por TechnologyWorkflowProperties mediante la operación «setConfigureWeight». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setConfigureWeight(int value){configureWeight=positive(value);}
    /** Descripción: Obtiene la información correspondiente a «getAnalyzeWeight» dentro de TechnologyWorkflowProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getAnalyzeWeight(){return analyzeWeight;}
    /** Descripción: Actualiza el estado gestionado por TechnologyWorkflowProperties mediante la operación «setAnalyzeWeight». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setAnalyzeWeight(int value){analyzeWeight=positive(value);}
    /** Descripción: Obtiene la información correspondiente a «getGenerateWeight» dentro de TechnologyWorkflowProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getGenerateWeight(){return generateWeight;}
    /** Descripción: Actualiza el estado gestionado por TechnologyWorkflowProperties mediante la operación «setGenerateWeight». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setGenerateWeight(int value){generateWeight=positive(value);}
    /** Descripción: Obtiene la información correspondiente a «getVerifyWeight» dentro de TechnologyWorkflowProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getVerifyWeight(){return verifyWeight;}
    /** Descripción: Actualiza el estado gestionado por TechnologyWorkflowProperties mediante la operación «setVerifyWeight». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setVerifyWeight(int value){verifyWeight=positive(value);}
    /** Descripción: Obtiene la información correspondiente a «getPreviewWeight» dentro de TechnologyWorkflowProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getPreviewWeight(){return previewWeight;}
    /** Descripción: Actualiza el estado gestionado por TechnologyWorkflowProperties mediante la operación «setPreviewWeight». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setPreviewWeight(int value){previewWeight=positive(value);}
    /** Descripción: Obtiene la información correspondiente a «getApplyWeight» dentro de TechnologyWorkflowProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getApplyWeight(){return applyWeight;}
    /** Descripción: Actualiza el estado gestionado por TechnologyWorkflowProperties mediante la operación «setApplyWeight». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setApplyWeight(int value){applyWeight=positive(value);}
    /** Descripción: Obtiene la información correspondiente a «getExecuteWeight» dentro de TechnologyWorkflowProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getExecuteWeight(){return executeWeight;}
    /** Descripción: Actualiza el estado gestionado por TechnologyWorkflowProperties mediante la operación «setExecuteWeight». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setExecuteWeight(int value){executeWeight=positive(value);}
    /** Descripción: Obtiene la información correspondiente a «getRoundTripWeight» dentro de TechnologyWorkflowProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getRoundTripWeight(){return roundTripWeight;}
    /** Descripción: Actualiza el estado gestionado por TechnologyWorkflowProperties mediante la operación «setRoundTripWeight». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setRoundTripWeight(int value){roundTripWeight=positive(value);}
    /** Descripción: Obtiene la información correspondiente a «getExportWeight» dentro de TechnologyWorkflowProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getExportWeight(){return exportWeight;}
    /** Descripción: Actualiza el estado gestionado por TechnologyWorkflowProperties mediante la operación «setExportWeight». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setExportWeight(int value){exportWeight=positive(value);}
    /** Descripción: Obtiene la información correspondiente a «getPartialPercent» dentro de TechnologyWorkflowProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getPartialPercent(){return partialPercent;}
    /** Descripción: Actualiza el estado gestionado por TechnologyWorkflowProperties mediante la operación «setPartialPercent». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setPartialPercent(int value){partialPercent=percent(value);}
    /** Descripción: Obtiene la información correspondiente a «getExternalPercent» dentro de TechnologyWorkflowProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getExternalPercent(){return externalPercent;}
    /** Descripción: Actualiza el estado gestionado por TechnologyWorkflowProperties mediante la operación «setExternalPercent». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setExternalPercent(int value){externalPercent=percent(value);}
    /** Descripción: Obtiene la información correspondiente a «getFullWorkflowThreshold» dentro de TechnologyWorkflowProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getFullWorkflowThreshold(){return Math.max(fullWorkflowThreshold, operationalThreshold);}
    /** Descripción: Actualiza el estado gestionado por TechnologyWorkflowProperties mediante la operación «setFullWorkflowThreshold». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setFullWorkflowThreshold(int value){fullWorkflowThreshold=percent(value);}
    /** Descripción: Obtiene la información correspondiente a «getOperationalThreshold» dentro de TechnologyWorkflowProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getOperationalThreshold(){return Math.min(operationalThreshold, fullWorkflowThreshold);}
    /** Descripción: Actualiza el estado gestionado por TechnologyWorkflowProperties mediante la operación «setOperationalThreshold». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setOperationalThreshold(int value){operationalThreshold=percent(value);}

    /** Descripción: Ejecuta la operación «positive» correspondiente a la responsabilidad de TechnologyWorkflowProperties. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private int positive(int value){return Math.max(0, value);}
    /** Descripción: Ejecuta la operación «percent» correspondiente a la responsabilidad de TechnologyWorkflowProperties. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private int percent(int value){return Math.max(0, Math.min(100, value));}
}
