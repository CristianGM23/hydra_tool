package com.example.editor.validation.contract;

/**
 * Centraliza las versiones y los sufijos de los contratos canónicos relacionados
 * con la validación de modelos.
 *
 * Esta clase no contiene semántica de ejecución y no depende de EMF, OCL, EVL,
 * Sirius o Xtext. Los adaptadores externos deben traducir hacia estos contratos.
 */
public final class ValidationContractVersions {

    public static final String CANONICAL_MODEL_DOCUMENT = "1.0";
    public static final String EXPRESSION_MODEL = "1.0";
    public static final String VALIDATION_MODEL = "1.0";
    public static final String ACTION_MODEL = "1.0";
    public static final String VALIDATION_REPORT = "1.0";
    public static final String EXECUTABLE_VALIDATION_PLAN = "1.0";

    public static final String MODEL_DOCUMENT_SUFFIX = ".model.json";
    public static final String EXPRESSION_DOCUMENT_SUFFIX = ".expressions.json";
    public static final String VALIDATION_DOCUMENT_SUFFIX = ".validations.json";
    public static final String ACTION_DOCUMENT_SUFFIX = ".actions.json";

    /** Descripción: Inicializa una instancia de ValidationContractVersions con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private ValidationContractVersions() {
    }
}
