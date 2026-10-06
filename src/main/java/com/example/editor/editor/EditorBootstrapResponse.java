package com.example.editor.editor;

import java.util.List;

import com.example.editor.metamodel.MetamodelDescriptor;

/**
 * Contiene la información necesaria para abrir el editor gráfico.
 *
 * @param metamodelId identificador del metamodelo.
 * @param fileName nombre del archivo Ecore.
 * @param palette elementos disponibles en la paleta.
 * @param descriptor descripción completa del metamodelo.
 */
public record EditorBootstrapResponse(String metamodelId, String fileName, List<PaletteEntry> palette, MetamodelDescriptor descriptor) {

    /**
     * Representa una clase Ecore que puede crearse desde la paleta.
     *
     * @param classId identificador completo de la EClass.
     * @param packageName nombre del paquete que contiene la clase.
     * @param className nombre de la clase.
     */
    public record PaletteEntry(String classId, String packageName, String className) {
    }
}