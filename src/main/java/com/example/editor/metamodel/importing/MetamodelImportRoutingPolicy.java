package com.example.editor.metamodel.importing;

import java.util.List;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;

/**
 * Decide qué tipo de conexión debe conservar una importación según su número de clases.
 * La política está separada del motor de layout para que el umbral pueda configurarse y probarse sin depender de un algoritmo concreto.
 */
@Component
public class MetamodelImportRoutingPolicy {

    private final MetamodelImportProperties properties;

    /** Descripción: Inicializa una instancia de MetamodelImportRoutingPolicy con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelImportRoutingPolicy(MetamodelImportProperties properties) {
        this.properties = properties;
    }

    /**
     * Indica si el metamodelo supera el umbral configurado.
     * La comparación es estricta: con 14 clases se usan conexiones normales y con 15 se permiten ortogonales por defecto.
     */
    /** Descripción: Ejecuta la operación «usesOrthogonalRouting» correspondiente a la responsabilidad de MetamodelImportRoutingPolicy. Entradas: metamodel: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean usesOrthogonalRouting(MetaModel metamodel) {
        return metamodel.classes().size() > properties.getOrthogonalRoutingClassThreshold();
    }

    /**
     * Elimina rutas ortogonales de metamodelos pequeños sin alterar puertos, estilos, etiquetas ni extremos semánticos.
     */
    /** Descripción: Actualiza el estado gestionado por MetamodelImportRoutingPolicy mediante la operación «apply». Entradas: metamodel: dato de entrada necesario para la operación; diagram: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelDiagramDocument apply(MetaModel metamodel, MetamodelDiagramDocument diagram) {
        if(usesOrthogonalRouting(metamodel)) {
            return diagram;
        }

        var edges = diagram.edges().stream()
                .map(item -> item.withRoutePoints(List.of()).withLabelPosition(null))
                .toList();
        return new MetamodelDiagramDocument(diagram.metamodelId(), diagram.nodes(), edges);
    }

    /**
     * Produce datos estables para los logs del análisis de importación.
     */
    /** Descripción: Ejecuta la operación «describe» correspondiente a la responsabilidad de MetamodelImportRoutingPolicy. Entradas: metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public String describe(MetaModel metamodel) {
        return "routingMode=" + (usesOrthogonalRouting(metamodel) ? "ORTHOGONAL" : "NORMAL") + System.lineSeparator()
                + "classCount=" + metamodel.classes().size() + System.lineSeparator()
                + "orthogonalThreshold=" + properties.getOrthogonalRoutingClassThreshold();
    }
}
