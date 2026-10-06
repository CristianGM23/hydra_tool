package com.example.editor.metamodel.importing.importers;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.importing.MetamodelImportFormat;
import com.example.editor.metamodel.importing.MetamodelImportOptions;
import com.example.editor.metamodel.importing.MetamodelImportOutput;
import com.example.editor.metamodel.importing.MetamodelImportSource;
import com.example.editor.metamodel.importing.MetamodelImporter;
import com.example.editor.metamodel.importing.support.ImportParsingSupport;
import com.example.editor.metamodel.importing.canonicalization.EcoreImportMetadata;

/**
 * Importa el Markdown para agentes mediante su proyección PlantUML y metadatos.
 */
@Component
public class AgentMarkdownMetamodelImporter implements MetamodelImporter {

    private static final Pattern PLANTUML = Pattern.compile("(?s)```plantuml\\s*(.*?)```");
    private final PlantUmlMetamodelImporter plantUmlImporter;

    /** Descripción: Inicializa una instancia de AgentMarkdownMetamodelImporter con sus dependencias y datos de entrada. Entradas: plantUmlImporter: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public AgentMarkdownMetamodelImporter(PlantUmlMetamodelImporter plantUmlImporter) {
        this.plantUmlImporter = plantUmlImporter;
    }

    @Override
    /** Descripción: Ejecuta la operación «format» correspondiente a la responsabilidad de AgentMarkdownMetamodelImporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportFormat format() {
        return MetamodelImportFormat.MARKDOWN_AGENT;
    }

    @Override
    /** Descripción: Ejecuta la operación «importMetamodel» correspondiente a la responsabilidad de AgentMarkdownMetamodelImporter. Entradas: source: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportOutput importMetamodel(MetamodelImportSource source, MetamodelImportOptions options) {
        String content = new String(source.content(), StandardCharsets.UTF_8);
        Matcher matcher = PLANTUML.matcher(content);

        if(!matcher.find()) {
            throw new com.example.editor.metamodel.design.MetamodelDesignException(com.example.editor.metamodel.design.MetamodelDesignException.Reason.INVALID_REQUEST, "Agent Markdown does not contain a PlantUML projection: " + source.fileName());
        }

        MetamodelImportOutput plantOutput = plantUmlImporter.importContent(matcher.group(1), source.fileName());
        Map<String, String> metadata = readFrontMatter(content);
        MetaModel current = plantOutput.metamodel();
        String id = ImportParsingSupport.safeId(metadata.getOrDefault("metamodelId", current.id()), current.id());
        String name = metadata.getOrDefault("name", current.name());
        String schemaVersion = metadata.getOrDefault("schemaVersion", current.schemaVersion());
        String namespaceUri = metadata.getOrDefault("namespaceUri", EcoreImportMetadata.text(current.annotations(), EcoreImportMetadata.NAMESPACE_URI, "urn:imported:" + id));
        String namespacePrefix = metadata.getOrDefault("namespacePrefix", EcoreImportMetadata.text(current.annotations(), EcoreImportMetadata.NAMESPACE_PREFIX, id.replace('-', '_')));
        Map<String, String> annotations = new LinkedHashMap<>(current.annotations());
        annotations.put("sourceFormat", "markdown-agent");
        MetaModel metamodel = new MetaModel(schemaVersion, id, name, current.classes(), current.enums(), current.dataTypes(), current.relations(), EcoreImportMetadata.model(annotations, id + ".ecore", namespaceUri, namespacePrefix));
        return new MetamodelImportOutput(metamodel, plantOutput.diagram(), plantOutput.warnings());
    }

    /** Descripción: Obtiene la información correspondiente a «readFrontMatter» dentro de AgentMarkdownMetamodelImporter. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, String> readFrontMatter(String content) {
        Map<String, String> result = new LinkedHashMap<>();
        String normalized = content.replace("\r", "");

        if(!normalized.startsWith("---\n")) {
            return result;
        }

        int end = normalized.indexOf("\n---", 4);

        if(end < 0) {
            return result;
        }

        String block = normalized.substring(4, end);

        for(String line : block.split("\n")) {
            int separator = line.indexOf(':');

            if(separator > 0) {
                result.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
            }
        }

        return result;
    }
}
