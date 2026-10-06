package com.example.editor.metamodel.export;

import java.util.List;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.TransformationWarning;
import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Genera JSON directamente desde el núcleo neutral.
 */
@Component
public class JsonMetamodelExporter implements MetamodelTextExporter {

    private final ObjectMapper objectMapper;

    /** Descripción: Inicializa una instancia de JsonMetamodelExporter con sus dependencias y datos de entrada. Entradas: objectMapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public JsonMetamodelExporter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    /** Descripción: Ejecuta la operación «format» correspondiente a la responsabilidad de JsonExportDocument. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelExportFormat format() {
        return MetamodelExportFormat.JSON;
    }

    @Override
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de JsonExportDocument. Entradas: context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public String export(NeutralMetamodelExportContext context) {
        MetamodelDiagramDocument diagram = context.options().includeLayout() ? context.diagram() : new MetamodelDiagramDocument(context.neutralMetamodel().id(), List.of(), List.of());
        List<TransformationWarning> warnings = context.options().includeWarnings() ? context.warnings() : List.of();
        JsonExportDocument document = new JsonExportDocument(context.neutralMetamodel(), diagram, warnings);

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(document);
        } catch(JacksonException exception) {
            throw new MetamodelDesignException(Reason.STORAGE_ERROR, "Could not generate neutral metamodel JSON export", exception);
        }
    }

    public record JsonExportDocument(MetaModel metamodel, MetamodelDiagramDocument diagram, List<TransformationWarning> warnings) {

        public JsonExportDocument {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }
}
