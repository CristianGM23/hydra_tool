package com.example.editor.gamification.projection;

import org.springframework.stereotype.Component;
import com.example.editor.gamification.GamificationBindingModel;
import com.example.editor.gamification.GamificationRule;
import com.example.editor.projection.drawio.DrawioArtifactExporter;
import com.example.editor.projection.drawio.DrawioExportOptions;
import com.example.editor.projection.drawio.DrawioGraphBuilder;

/** Proyección modular de las reglas que convierten evidencia en progreso. */
@Component
public class GamificationBindingDrawioExporter implements DrawioArtifactExporter<GamificationBindingModel> {
    public static final String ARTIFACT_TYPE="gamification-bindings";
    /** Descripción: Ejecuta la operación «artifactType» correspondiente a la responsabilidad de GamificationBindingDrawioExporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public String artifactType() { return ARTIFACT_TYPE; }
    /** Descripción: Ejecuta la operación «sourceType» correspondiente a la responsabilidad de GamificationBindingDrawioExporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Class<GamificationBindingModel> sourceType() { return GamificationBindingModel.class; }
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de GamificationBindingDrawioExporter. Entradas: source: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ @Override public String export(GamificationBindingModel source,DrawioExportOptions options) {
        DrawioGraphBuilder graph=new DrawioGraphBuilder(source.id(),options.title().isBlank()?"Gamification rules":options.title());
        int index=0;
        for(GamificationRule rule:source.rules()) {
            String eventId="event:"+rule.id();
            graph.vertex(eventId,DrawioGraphBuilder.html("VERIFIED EVENT\n"+rule.eventTypeId()),40,40+index*125,290,75,
                    "rounded=1;whiteSpace=wrap;html=1;fillColor=#d5e8d4;strokeColor=#82b366;");
            String value=rule.name()+"\n"+rule.points()+" "+rule.pointsTypeId()+"\n"+rule.repeatPolicy();
            graph.vertex(rule.id(),DrawioGraphBuilder.html(value),400,40+index*125,260,85,
                    rule.enabled()?"rounded=1;whiteSpace=wrap;html=1;fillColor=#e1d5e7;strokeColor=#9673a6;":"rounded=1;whiteSpace=wrap;html=1;fillColor=#f5f5f5;strokeColor=#666666;dashed=1;");
            graph.edge("evidence-"+rule.id(),"evidence",eventId,rule.id(),"edgeStyle=orthogonalEdgeStyle;html=1;endArrow=block;");
            if(!rule.rewardId().isBlank()) {
                String reward="reward:"+rule.rewardId();
                graph.vertex(reward,DrawioGraphBuilder.html("REWARD\n"+rule.rewardId()),740,40+index*125,230,65,
                        "rounded=1;whiteSpace=wrap;html=1;fillColor=#fff2cc;strokeColor=#d6b656;");
                graph.edge("reward-"+rule.id(),"grants",rule.id(),reward,"edgeStyle=orthogonalEdgeStyle;html=1;endArrow=block;");
            }
            index++;
        }
        return graph.build();
    }
}
