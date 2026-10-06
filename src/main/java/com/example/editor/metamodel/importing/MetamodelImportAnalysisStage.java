package com.example.editor.metamodel.importing;

import java.util.Arrays;

import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;

/** Define las etapas estables y secuenciales del análisis canónico. */
public enum MetamodelImportAnalysisStage {
    FILE_RECEIVED(1, "File received"),
    CONFIGURATION(2, "Configuration"),
    FILE_VALIDATION(3, "File validation"),
    FORMAT_DETECTION(4, "Format detection"),
    PARSING(5, "Source parsing"),
    NORMALIZATION(6, "Canonical normalization"),
    DIAGRAM(7, "Diagram preparation"),
    CANONICALIZATION(8, "Canonical relation recovery"),
    VALIDATION(9, "Canonical validation"),
    SUMMARY(10, "Analysis summary");

    private final int sequence;
    private final String label;

    /** Descripción: Inicializa una instancia de MetamodelImportAnalysisStage con sus dependencias y datos de entrada. Entradas: sequence: dato de entrada necesario para la operación; label: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ MetamodelImportAnalysisStage(int sequence, String label) {
        this.sequence = sequence;
        this.label = label;
    }

    /** Descripción: Ejecuta la operación «sequence» correspondiente a la responsabilidad de MetamodelImportAnalysisStage. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int sequence() { return sequence; }
    /** Descripción: Ejecuta la operación «label» correspondiente a la responsabilidad de MetamodelImportAnalysisStage. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String label() { return label; }
    /** Descripción: Ejecuta la operación «id» correspondiente a la responsabilidad de MetamodelImportAnalysisStage. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String id() { return name(); }

    /** Descripción: Ejecuta la operación «fromId» correspondiente a la responsabilidad de MetamodelImportAnalysisStage. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public static MetamodelImportAnalysisStage fromId(String value) {
        return Arrays.stream(values()).filter(item -> item.name().equalsIgnoreCase(value)).findFirst().orElseThrow(() -> new MetamodelDesignException(Reason.INVALID_REQUEST, "Unknown metamodel import analysis stage: " + value));
    }
}
