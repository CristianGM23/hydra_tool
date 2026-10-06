package com.example.editor.event.flow.projection;

import org.springframework.stereotype.Component;
import com.example.editor.event.flow.EventFlowModel;
import com.example.editor.event.flow.FlowDefinition;
import com.example.editor.event.flow.FlowNode;
import com.example.editor.event.flow.FlowTransition;
import com.example.editor.projection.drawio.DrawioArtifactExporter;
import com.example.editor.projection.drawio.DrawioExportOptions;
import com.example.editor.projection.drawio.DrawioGraphBuilder;

/** Registra EventFlowModel en Draw.io sin introducir un switch central. */
@Component
public class EventFlowModelDrawioExporter implements DrawioArtifactExporter<EventFlowModel> {
    public static final String ARTIFACT_TYPE="event-flow";
    /** Descripción: Ejecuta la operación «artifactType» correspondiente a la responsabilidad de EventFlowModelDrawioExporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public String artifactType() { return ARTIFACT_TYPE; }
    /** Descripción: Ejecuta la operación «sourceType» correspondiente a la responsabilidad de EventFlowModelDrawioExporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Class<EventFlowModel> sourceType() { return EventFlowModel.class; }
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de EventFlowModelDrawioExporter. Entradas: source: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ @Override public String export(EventFlowModel source,DrawioExportOptions options) {
        DrawioGraphBuilder graph=new DrawioGraphBuilder(source.id(),options.title().isBlank()?"Event flows":options.title());
        int flowOffset=0;
        for(FlowDefinition flow:source.flows()) {
            String flowId="flow:"+flow.id();
            graph.vertex(flowId,DrawioGraphBuilder.html(flow.name()+"\n"+flow.id()+"\nTriggers: "+String.join(", ",flow.triggerEventTypeIds())),40,40+flowOffset,300,95,
                    "rounded=1;whiteSpace=wrap;html=1;fillColor=#dae8fc;strokeColor=#6c8ebf;fontStyle=1;");
            int index=0;
            for(FlowNode node:flow.nodes()) {
                double x=390+(index%5)*225; double y=40+flowOffset+(index/5)*135;
                String style=switch(node.kind()) {
                    case START, SOURCE -> "ellipse;whiteSpace=wrap;html=1;fillColor=#d5e8d4;strokeColor=#82b366;";
                    case SUCCESS_END -> "ellipse;whiteSpace=wrap;html=1;fillColor=#d5e8d4;strokeColor=#82b366;double=1;";
                    case ERROR_END -> "ellipse;whiteSpace=wrap;html=1;fillColor=#f8cecc;strokeColor=#b85450;double=1;";
                    case DECISION, ROUTE -> "rhombus;whiteSpace=wrap;html=1;fillColor=#fff2cc;strokeColor=#d6b656;";
                    case COMPENSATION_STEP, FALLBACK, ERROR_HANDLER -> "rounded=1;whiteSpace=wrap;html=1;fillColor=#f8cecc;strokeColor=#b85450;";
                    default -> "rounded=1;whiteSpace=wrap;html=1;fillColor=#e1d5e7;strokeColor=#9673a6;";
                };
                graph.vertex(node.id(),DrawioGraphBuilder.html(node.kind()+"\n"+node.name()),x,y,190,80,style);
                graph.edge(flowId+":node:"+node.id(),"contains",flowId,node.id(),"edgeStyle=orthogonalEdgeStyle;html=1;endArrow=open;dashed=1;");
                index++;
            }
            for(FlowTransition transition:flow.transitions()) {
                graph.edge(transition.id(),transition.kind()+ (transition.label().isBlank()?"":" / "+transition.label()),transition.sourceNodeId(),transition.targetNodeId(),
                        "edgeStyle=orthogonalEdgeStyle;html=1;endArrow=block;");
            }
            flowOffset+=Math.max(210,((flow.nodes().size()+4)/5)*135+90);
        }
        return graph.build();
    }
}
