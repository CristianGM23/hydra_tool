package com.example.editor.metamodel.importing.importers;

import java.util.List;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.TransformationWarning;
import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;
import com.example.editor.metamodel.importing.MetamodelImportFormat;
import com.example.editor.metamodel.importing.MetamodelImportOptions;
import com.example.editor.metamodel.importing.MetamodelImportOutput;
import com.example.editor.metamodel.importing.MetamodelImportSource;
import com.example.editor.metamodel.importing.MetamodelImporter;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Importa el JSON neutral bruto o el documento JSON exportado.
 */
@Component
public class NeutralJsonMetamodelImporter implements MetamodelImporter {

    private final ObjectMapper objectMapper;

    /** Inicializa el importador neutral con el contrato interno actual. */
    /** Descripción: Inicializa una instancia de NeutralJsonMetamodelImporter con sus dependencias y datos de entrada. Entradas: objectMapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public NeutralJsonMetamodelImporter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    /** Descripción: Ejecuta la operación «format» correspondiente a la responsabilidad de NeutralJsonMetamodelImporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportFormat format() {
        return MetamodelImportFormat.JSON;
    }

    @Override
    /** Descripción: Ejecuta la operación «importMetamodel» correspondiente a la responsabilidad de NeutralJsonMetamodelImporter. Entradas: source: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportOutput importMetamodel(MetamodelImportSource source, MetamodelImportOptions options) {
        try {
            JsonNode root = objectMapper.readTree(source.content());
            JsonNode modelNode = root.get("metamodel");
            MetaModel metamodel;
            MetamodelDiagramDocument diagram = null;
            List<TransformationWarning> warnings = List.of();

            if (modelNode != null && !modelNode.isNull()) {
                metamodel = readCurrentMetamodel(objectMapper.writeValueAsBytes(modelNode));
                JsonNode diagramNode = root.get("diagram");
                JsonNode warningsNode = root.get("warnings");

                if (diagramNode != null && !diagramNode.isNull()) {
                    diagram = objectMapper.treeToValue(diagramNode, MetamodelDiagramDocument.class);
                }

                if (warningsNode != null && warningsNode.isArray()) {
                    warnings = objectMapper.readerForListOf(TransformationWarning.class).readValue(warningsNode);
                }
            } else {
                metamodel = readCurrentMetamodel(source.content());
            }

            return new MetamodelImportOutput(metamodel, diagram, warnings);
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Could not parse neutral metamodel JSON: " + source.fileName(), exception);
        }
    }

    /** Descripción: Obtiene la información correspondiente a «readCurrentMetamodel» dentro de NeutralJsonMetamodelImporter. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private MetaModel readCurrentMetamodel(byte[] content) throws JacksonException {
        MetaModel metamodel = objectMapper.readValue(content, MetaModel.class);
        if (!MetaModel.CURRENT_SCHEMA_VERSION.equals(metamodel.schemaVersion())) {
            throw new IllegalArgumentException("Unsupported MetaModel schema version: " + metamodel.schemaVersion()
                    + ". Export or convert the source through an external-format importer first.");
        }
        return metamodel;
    }

    /**
     * Representa la envoltura generada por el exportador JSON.
     */
    public record JsonImportDocument(MetaModel metamodel, MetamodelDiagramDocument diagram, List<TransformationWarning> warnings) {

        public JsonImportDocument {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }
}
