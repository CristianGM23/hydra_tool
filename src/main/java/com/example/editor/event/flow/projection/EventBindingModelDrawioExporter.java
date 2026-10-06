package com.example.editor.event.flow.projection;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.example.editor.event.EventBinding;
import com.example.editor.event.EventBindingModel;
import com.example.editor.projection.drawio.DrawioArtifactExporter;
import com.example.editor.projection.drawio.DrawioExportOptions;
import com.example.editor.projection.drawio.DrawioGraphBuilder;

/** Registra los bindings entre eventos en la SPI modular de Draw.io. */
@Component
public class EventBindingModelDrawioExporter implements DrawioArtifactExporter<EventBindingModel> {
    public static final String ARTIFACT_TYPE="event-bindings";
    /** Descripción: Ejecuta la operación «artifactType» correspondiente a la responsabilidad de EventBindingModelDrawioExporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public String artifactType() { return ARTIFACT_TYPE; }
    /** Descripción: Ejecuta la operación «sourceType» correspondiente a la responsabilidad de EventBindingModelDrawioExporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Class<EventBindingModel> sourceType() { return EventBindingModel.class; }
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de EventBindingModelDrawioExporter. Entradas: source: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ @Override public String export(EventBindingModel source,DrawioExportOptions options) {
        DrawioGraphBuilder graph=new DrawioGraphBuilder(source.id(),options.title().isBlank()?"Event bindings":options.title());
        Set<String> eventTypes=new LinkedHashSet<>();
        for(EventBinding binding:source.bindings()) { eventTypes.add(binding.sourceEventTypeId()); eventTypes.add(binding.targetEventTypeId()); }
        int index=0;
        for(String eventType:eventTypes) {
            double x=40+(index%4)*270; double y=40+(index/4)*150;
            graph.vertex(eventType,DrawioGraphBuilder.html("Event\n"+eventType),x,y,230,75,
                    "rounded=1;whiteSpace=wrap;html=1;fillColor=#d5e8d4;strokeColor=#82b366;");
            index++;
        }
        for(EventBinding binding:source.bindings()) {
            String label=binding.name()+"\n"+binding.errorPolicy()+(binding.conditionExpressionId().isBlank()?"":"\nif "+binding.conditionExpressionId());
            graph.edge(binding.id(),DrawioGraphBuilder.html(label),binding.sourceEventTypeId(),binding.targetEventTypeId(),
                    binding.enabled()?"edgeStyle=orthogonalEdgeStyle;html=1;endArrow=block;":"edgeStyle=orthogonalEdgeStyle;html=1;endArrow=block;dashed=1;");
        }
        return graph.build();
    }
}
