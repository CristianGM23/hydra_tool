package com.example.editor.metamodel.export;

/**
 * Configura una operación de exportación.
 *
 * @param includeLayout indica si se usan las posiciones guardadas.
 * @param includeStyles indica si se usan los estilos personalizados.
 * @param includeWarnings indica si se incluyen advertencias.
 * @param transparentBackground indica si las imágenes usan fondo transparente.
 * @param imageScale contiene la escala de imágenes entre 1 y 4.
 * @param codePackageName contiene el paquete Java opcional.
 */
public record MetamodelExportOptions(boolean includeLayout, boolean includeStyles, boolean includeWarnings, boolean transparentBackground, int imageScale, String codePackageName) {

    public MetamodelExportOptions {
        if(imageScale < 1) {
            imageScale = 1;
        } else if(imageScale > 4) {
            imageScale = 4;
        }

        if(codePackageName == null) {
            codePackageName = "";
        }
    }

    /** Descripción: Ejecuta la operación «defaults» correspondiente a la responsabilidad de MetamodelExportOptions. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static MetamodelExportOptions defaults() {
        return new MetamodelExportOptions(true, true, true, false, 2, "");
    }
}
