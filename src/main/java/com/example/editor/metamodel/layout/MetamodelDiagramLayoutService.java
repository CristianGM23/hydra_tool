package com.example.editor.metamodel.layout;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;
import com.example.editor.metamodel.importing.canonicalization.ImportedMetamodelCanonicalizer;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.metamodel.validation.ValidationReport;

/** Coordina el layout directamente sobre MetaModel 2.0. */
@Service
public class MetamodelDiagramLayoutService {

    private final MetamodelLayoutProperties properties;
    private final MetamodelDiagramLayoutEngineRegistry registry;
    private final NeutralMetamodelValidator validator;

    /** Descripción: Inicializa una instancia de MetamodelDiagramLayoutService con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; registry: dato de entrada necesario para la operación; validator: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelDiagramLayoutService(MetamodelLayoutProperties properties, MetamodelDiagramLayoutEngineRegistry registry, NeutralMetamodelValidator validator) {
        this.properties = properties;
        this.registry = registry;
        this.validator = validator;
    }

    /** Descripción: Ejecuta la operación «layout» correspondiente a la responsabilidad de MetamodelDiagramLayoutService. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelLayoutResponse layout(MetamodelLayoutRequest request) {
        ensureEnabled();
        if (request == null || request.metamodel() == null) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Metamodel layout request must contain a metamodel");
        }
        MetaModel metamodel;
        try {
            metamodel = ImportedMetamodelCanonicalizer.prepare(request.metamodel()).metamodel();
        } catch (IllegalArgumentException exception) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, exception.getMessage());
        }
        validate(metamodel);
        MetamodelLayoutOptions options = request.options() == null ? MetamodelLayoutOptions.defaults(properties) : request.options();
        MetamodelLayoutResult result = layout(metamodel, normalizeDiagram(metamodel.id(), request.diagram()), options);
        return new MetamodelLayoutResponse(result.diagram(), result.warnings(), result.statistics());
    }

    /** Descripción: Ejecuta la operación «layout» correspondiente a la responsabilidad de MetamodelDiagramLayoutService. Entradas: metamodel: dato de entrada necesario para la operación; currentDiagram: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelLayoutResult layout(MetaModel metamodel, MetamodelDiagramDocument currentDiagram, MetamodelLayoutOptions options) {
        ensureEnabled();
        if (metamodel == null) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Metamodel must not be null");
        }
        validate(metamodel);
        MetamodelLayoutOptions effectiveOptions = options == null ? MetamodelLayoutOptions.defaults(properties) : options;
        return registry.require(effectiveOptions.algorithm()).layout(metamodel, normalizeDiagram(metamodel.id(), currentDiagram), effectiveOptions);
    }

    /** Descripción: Ejecuta la operación «defaultOptions» correspondiente a la responsabilidad de MetamodelDiagramLayoutService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelLayoutOptions defaultOptions() { return MetamodelLayoutOptions.defaults(properties); }
    /** Descripción: Ejecuta la operación «algorithms» correspondiente a la responsabilidad de MetamodelDiagramLayoutService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<String> algorithms() { return registry.ids(); }

    /** Descripción: Valida las condiciones necesarias para «ensureEnabled» y rechaza entradas no válidas. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void ensureEnabled() {
        if (!properties.isEnabled()) throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Metamodel automatic layout is disabled");
    }

    /** Descripción: Ejecuta la operación «normalizeDiagram» correspondiente a la responsabilidad de MetamodelDiagramLayoutService. Entradas: metamodelId: identificador que delimita el recurso objetivo; diagram: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetamodelDiagramDocument normalizeDiagram(String metamodelId, MetamodelDiagramDocument diagram) {
        if (diagram == null) return new MetamodelDiagramDocument(metamodelId, List.of(), List.of());
        return new MetamodelDiagramDocument(metamodelId, diagram.nodes(), diagram.edges());
    }

    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: metamodel: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validate(MetaModel metamodel) {
        ValidationReport report = validator.validate(metamodel);
        if (report.hasErrors()) {
            String message = report.errors().stream().map(item -> item.code() + ": " + item.message()).collect(java.util.stream.Collectors.joining("; "));
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Metamodel layout validation failed: " + message);
        }
    }
}
