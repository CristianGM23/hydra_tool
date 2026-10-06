package com.example.editor.metamodel.validation;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuración del gate de validación del metamodelo antes de generar proyectos derivados. */
@ConfigurationProperties(prefix = "dsl.metamodel.validation")
public class MetamodelValidationProperties {
    private Path receiptRoot = Path.of("./data/metamodel-validation");
    private boolean requireCurrentValidationForProjectExports = true;
    private boolean warningsBlockProjectExports = false;
    private int maximumDiagnostics = 5000;

    /** Descripción: Obtiene la información correspondiente a «getReceiptRoot» dentro de MetamodelValidationProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Path getReceiptRoot(){return receiptRoot;}
    /** Descripción: Actualiza el estado gestionado por MetamodelValidationProperties mediante la operación «setReceiptRoot». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setReceiptRoot(Path value){if(value != null){receiptRoot = value;}}
    /** Descripción: Evalúa la condición «isRequireCurrentValidationForProjectExports» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isRequireCurrentValidationForProjectExports(){return requireCurrentValidationForProjectExports;}
    /** Descripción: Actualiza el estado gestionado por MetamodelValidationProperties mediante la operación «setRequireCurrentValidationForProjectExports». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setRequireCurrentValidationForProjectExports(boolean value){requireCurrentValidationForProjectExports = value;}
    /** Descripción: Evalúa la condición «isWarningsBlockProjectExports» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isWarningsBlockProjectExports(){return warningsBlockProjectExports;}
    /** Descripción: Actualiza el estado gestionado por MetamodelValidationProperties mediante la operación «setWarningsBlockProjectExports». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setWarningsBlockProjectExports(boolean value){warningsBlockProjectExports = value;}
    /** Descripción: Obtiene la información correspondiente a «getMaximumDiagnostics» dentro de MetamodelValidationProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumDiagnostics(){return maximumDiagnostics;}
    /** Descripción: Actualiza el estado gestionado por MetamodelValidationProperties mediante la operación «setMaximumDiagnostics». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumDiagnostics(int value){maximumDiagnostics = Math.max(10, value);}
}
