package com.example.editor.metamodel.importing;

import java.util.List;

/**
 * Describe un formato visible en el frontend.
 *
 * @param id contiene el identificador.
 * @param displayName contiene el nombre visible.
 * @param category contiene la categoría.
 * @param supportLevel contiene el nivel de recuperación.
 * @param extensions contiene las extensiones.
 */
public record MetamodelImportFormatResponse(String id, String displayName, String category, String supportLevel, List<String> extensions) {
}
