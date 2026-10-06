package com.example.editor.event.flow.projection;

import org.springframework.stereotype.Component;
import com.example.editor.event.EventDefinitionModel;
import com.example.editor.event.EventTypeDefinition;
import com.example.editor.projection.drawio.DrawioArtifactExporter;
import com.example.editor.projection.drawio.DrawioExportOptions;
import com.example.editor.projection.drawio.DrawioGraphBuilder;

/** Registra el catálogo de eventos en la SPI modular de Draw.io. */
@Component
public class EventDefinitionModelDrawioExporter implements DrawioArtifactExporter<EventDefinitionModel> {
    public static final String ARTIFACT_TYPE="event-catalog";
    /** Descripción: Ejecuta la operación «artifactType» correspondiente a la responsabilidad de EventDefinitionModelDrawioExporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public String artifactType() { return ARTIFACT_TYPE; }
    /** Descripción: Ejecuta la operación «sourceType» correspondiente a la responsabilidad de EventDefinitionModelDrawioExporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Class<EventDefinitionModel> sourceType() { return EventDefinitionModel.class; }
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de EventDefinitionModelDrawioExporter. Entradas: source: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ @Override public String export(EventDefinitionModel source,DrawioExportOptions options) {
        String title=options.title().isBlank()?"Event catalog":options.title();
        DrawioGraphBuilder graph=new DrawioGraphBuilder(source.id(),title);
        graph.vertex("catalog",DrawioGraphBuilder.html("Event catalog\n"+source.namespace()+"\n"+source.metamodelId()),40,40,300,100,
                "rounded=1;whiteSpace=wrap;html=1;fillColor=#dae8fc;strokeColor=#6c8ebf;fontStyle=1;");
        int index=0;
        for(EventTypeDefinition type:source.eventTypes()) {
            double x=400+(index%4)*260; double y=40+(index/4)*145;
            String value=type.name()+"\n"+type.id()+"\n"+type.category()+" / "+type.persistencePolicy()+"\nFields: "+type.fields().size();
            graph.vertex(type.id(),DrawioGraphBuilder.html(value),x,y,225,100,
                    type.enabled()?"rounded=1;whiteSpace=wrap;html=1;fillColor=#d5e8d4;strokeColor=#82b366;":"rounded=1;whiteSpace=wrap;html=1;fillColor=#f5f5f5;strokeColor=#666666;dashed=1;");
            graph.edge("catalog-"+type.id(),"defines","catalog",type.id(),"edgeStyle=orthogonalEdgeStyle;html=1;endArrow=block;");
            index++;
        }
        return graph.build();
    }
}
