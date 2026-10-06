package com.example.editor.metamodel.importing;

/**
 * Configura una importación.
 *
 * @param formatId contiene el formato solicitado o auto.
 * @param targetId contiene el identificador final opcional.
 * @param overwrite indica si permite reemplazar un metamodelo.
 * @param preserveLayout indica si conserva el layout disponible.
 * @param preserveStyles indica si conserva los estilos disponibles.
 * @param strict indica si bloquea reconstrucciones con pérdida semántica.
 */
public record MetamodelImportOptions(String formatId, String targetId, boolean overwrite, boolean preserveLayout, boolean preserveStyles, boolean strict) {

    public MetamodelImportOptions {
        if(formatId == null || formatId.isBlank()) {
            formatId = "auto";
        }

        if(targetId == null) {
            targetId = "";
        }
    }

    /** Descripción: Ejecuta la operación «defaults» correspondiente a la responsabilidad de MetamodelImportOptions. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static MetamodelImportOptions defaults() {
        return new MetamodelImportOptions("auto", "", false, true, true, false);
    }
}
