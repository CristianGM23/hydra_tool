package com.example.editor.metamodel.importing;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.EdgeDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.NodeDesign;
import com.example.editor.metamodel.layout.MetamodelDiagramLayoutService;

/**
 * Adapta el layout general al flujo de importación.
 */
@Component
public class AutomaticMetamodelDiagramLayoutService {

    private final MetamodelDiagramLayoutService layoutService;
    private final MetamodelImportProperties importProperties;
    private final MetamodelImportRoutingPolicy routingPolicy;

    /**
     * Descripción:
     * Crea el adaptador utilizado por Spring.
     *
     * @param layoutService contiene el servicio general.
     * @param importProperties contiene el umbral y la configuración compatible.
     * @param routingPolicy decide cuándo conservar rutas ortogonales.
     */
    @Autowired
    /** Descripción: Inicializa una instancia de AutomaticMetamodelDiagramLayoutService con sus dependencias y datos de entrada. Entradas: layoutService: servicio colaborador utilizado por la operación; importProperties: configuración que gobierna el comportamiento; routingPolicy: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public AutomaticMetamodelDiagramLayoutService(MetamodelDiagramLayoutService layoutService, MetamodelImportProperties importProperties, MetamodelImportRoutingPolicy routingPolicy) {
        this.layoutService = layoutService;
        this.importProperties = importProperties;
        this.routingPolicy = routingPolicy;
    }

    /**
     * Descripción:
     * Organiza automáticamente un metamodelo importado.
     *
     * @param metamodel contiene la semántica neutral.
     * @return el diagrama organizado.
     */
    /** Descripción: Ejecuta la operación «layout» correspondiente a la responsabilidad de AutomaticMetamodelDiagramLayoutService. Entradas: metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelDiagramDocument layout(MetaModel metamodel) {
        MetamodelDiagramDocument empty = new MetamodelDiagramDocument(metamodel.id(), List.of(), List.of());

        MetamodelDiagramDocument diagram;

        if(layoutService != null) {
            diagram = layoutService.layout(metamodel, empty, layoutService.defaultOptions()).diagram();
        } else {
            diagram = compatibilityGridLayout(metamodel);
        }

        return applyImportRoutingPolicy(metamodel, diagram);
    }


    /**
     * Descripción:
     * Mantiene el grid histórico únicamente para construcciones directas antiguas.
     *
     * @param metamodel contiene el metamodelo.
     * @return el diagrama compatible.
     */
    /** Descripción: Ejecuta la operación «compatibilityGridLayout» correspondiente a la responsabilidad de AutomaticMetamodelDiagramLayoutService. Entradas: metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetamodelDiagramDocument compatibilityGridLayout(MetaModel metamodel) {
        java.util.List<NodeDesign> nodes = new java.util.ArrayList<>();
        java.util.List<EdgeDesign> edges = new java.util.ArrayList<>();
        int index = 0;
        int columns = Math.max(1, importProperties.getLayoutColumns());
        double horizontalStep = 250 + Math.max(20, importProperties.getLayoutHorizontalGap());
        double verticalStep = 170 + Math.max(20, importProperties.getLayoutVerticalGap());

        for(var metaClass : metamodel.classes()) {
            double height = Math.max(130, 68 + metaClass.attributes().size() * 22);
            int column = index % columns;
            int row = index / columns;
            nodes.add(new NodeDesign(metaClass.id(), 60 + column * horizontalStep, 60 + row * verticalStep, 240, height));
            index += 1;
        }

        for(var metaEnum : metamodel.enums()) {
            int column = index % columns;
            int row = index / columns;
            nodes.add(new NodeDesign(metaEnum.id(), 60 + column * horizontalStep, 60 + row * verticalStep, 220, Math.max(110, 52 + metaEnum.literals().size() * 22)));
            index += 1;
        }

        for(var dataType : metamodel.dataTypes()) {
            int column = index % columns;
            int row = index / columns;
            nodes.add(new NodeDesign(dataType.id(), 60 + column * horizontalStep, 60 + row * verticalStep, 220, 100));
            index += 1;
        }

        for(var relation : metamodel.relations()) {
            edges.add(new EdgeDesign(relation.id(), "right-50", "left-50", relation.kind().name(), relation.sourceTypeId(), relation.targetTypeId(), relation.name()));
        }

        return new MetamodelDiagramDocument(metamodel.id(), nodes, edges);
    }

    /**
     * Aplica la política de routing de importación sin alterar nodos, puertos ni estilos.
     * Los metamodelos con hasta el umbral configurado usan curvas normales; los mayores conservan las rutas ortogonales calculadas o importadas.
     *
     * @param metamodel contiene la semántica importada.
     * @param diagram contiene el diagrama preparado.
     * @return el diagrama adaptado a la política configurada.
     */
    /** Descripción: Actualiza el estado gestionado por AutomaticMetamodelDiagramLayoutService mediante la operación «applyImportRoutingPolicy». Entradas: metamodel: dato de entrada necesario para la operación; diagram: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelDiagramDocument applyImportRoutingPolicy(MetaModel metamodel, MetamodelDiagramDocument diagram) {
        return routingPolicy.apply(metamodel, diagram);
    }

    /**
     * Indica si una importación debe conservar rutas ortogonales.
     * La comparación es estricta: con 14 clases se usan curvas normales y a partir de 15 se permiten rutas ortogonales con la configuración predeterminada.
     *
     * @param metamodel contiene las clases importadas.
     * @return true cuando supera el umbral.
     */
    /** Descripción: Ejecuta la operación «usesOrthogonalRouting» correspondiente a la responsabilidad de AutomaticMetamodelDiagramLayoutService. Entradas: metamodel: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean usesOrthogonalRouting(MetaModel metamodel) {
        return routingPolicy.usesOrthogonalRouting(metamodel);
    }

    /**
     * Resume la decisión de routing para los logs de importación.
     *
     * @param metamodel contiene el metamodelo.
     * @return detalles legibles y estables.
     */
    /** Descripción: Ejecuta la operación «describeImportRouting» correspondiente a la responsabilidad de AutomaticMetamodelDiagramLayoutService. Entradas: metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public String describeImportRouting(MetaModel metamodel) {
        return routingPolicy.describe(metamodel);
    }


    /**
     * Descripción:
     * Elimina estilos sin cambiar posiciones y conexiones.
     *
     * @param diagram contiene el diagrama.
     * @return el diagrama sin estilos.
     */
    /** Descripción: Ejecuta la operación «withoutStyles» correspondiente a la responsabilidad de AutomaticMetamodelDiagramLayoutService. Entradas: diagram: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelDiagramDocument withoutStyles(MetamodelDiagramDocument diagram) {
        List<NodeDesign> nodes = diagram.nodes().stream().map(item -> new NodeDesign(item.elementId(), item.x(), item.y(), item.width(), item.height())).toList();
        List<EdgeDesign> edges = diagram.edges().stream().map(item -> new EdgeDesign(item.relationId(), item.sourceHandle(), item.targetHandle(), item.kind(), item.sourceClassId(), item.targetClassId(), item.label(), null, item.routePoints(), item.labelPosition())).toList();
        return new MetamodelDiagramDocument(diagram.metamodelId(), nodes, edges);
    }
}
