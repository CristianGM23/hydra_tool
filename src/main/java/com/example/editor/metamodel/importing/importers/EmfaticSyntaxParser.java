package com.example.editor.metamodel.importing.importers;

/**
 * Package-safe facade for Emfatic parsing. The implementation lives in a
 * focused engine while the importer owns the intermediate specification.
 */
final class EmfaticSyntaxParser {
    private final String text;
    private final String sourceFile;

    /** Descripción: Inicializa una instancia de EmfaticSyntaxParser con sus dependencias y datos de entrada. Entradas: text: valor de entrada que debe procesarse; sourceFile: archivo o contenido que debe procesarse. Salida: deja la instancia inicializada y lista para su uso. */ EmfaticSyntaxParser(String text, String sourceFile) {
        this.text = text == null ? "" : text;
        this.sourceFile = sourceFile == null ? "" : sourceFile;
    }

    /** Descripción: Ejecuta la operación «parse» correspondiente a la responsabilidad de EmfaticSyntaxParser. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ EmfaticMetamodelImporter.ParsedEmfatic parse() {
        return EmfaticMetamodelImporter.parseSyntax(text, sourceFile);
    }
}
