package com.example.editor.metamodel.importing.importers;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.importing.MetamodelImportFormat;
import com.example.editor.metamodel.importing.MetamodelImportOptions;
import com.example.editor.metamodel.importing.MetamodelImportOutput;
import com.example.editor.metamodel.importing.MetamodelImportSource;
import com.example.editor.metamodel.importing.MetamodelImporter;
import com.example.editor.metamodel.importing.support.GeneratedGrammarImportSupport;

/**
 * Importa la sintaxis Xtext generada por la herramienta.
 */
@Component
public class XtextMetamodelImporter implements MetamodelImporter {

    private static final Pattern GRAMMAR = Pattern.compile("(?m)^grammar\\s+([A-Za-z_][A-Za-z0-9_.]*)");
    private static final Pattern IMPORT = Pattern.compile("(?m)^import\\s+\"([^\"]+)\"");

    @Override
    /** Descripción: Ejecuta la operación «format» correspondiente a la responsabilidad de XtextMetamodelImporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportFormat format() {
        return MetamodelImportFormat.XTEXT;
    }

    @Override
    /** Descripción: Ejecuta la operación «importMetamodel» correspondiente a la responsabilidad de XtextMetamodelImporter. Entradas: source: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportOutput importMetamodel(MetamodelImportSource source, MetamodelImportOptions options) {
        String content = new String(source.content(), StandardCharsets.UTF_8);
        Matcher grammarMatcher = GRAMMAR.matcher(content);
        Matcher importMatcher = IMPORT.matcher(content);
        String grammarName = grammarMatcher.find() ? grammarMatcher.group(1) : "ImportedXtext";
        String namespaceUri = importMatcher.find() ? importMatcher.group(1) : "";
        var parsed = GeneratedGrammarImportSupport.parseTextual(content, grammarName, namespaceUri, "xtext");
        return new MetamodelImportOutput(parsed.metamodel(), null, parsed.warnings());
    }
}
