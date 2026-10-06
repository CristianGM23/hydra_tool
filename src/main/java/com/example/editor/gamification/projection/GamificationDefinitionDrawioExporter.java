package com.example.editor.gamification.projection;

import org.springframework.stereotype.Component;
import com.example.editor.gamification.AchievementDefinition;
import com.example.editor.gamification.ChallengeDefinition;
import com.example.editor.gamification.GamificationDefinitionModel;
import com.example.editor.gamification.LevelDefinition;
import com.example.editor.gamification.PointTypeDefinition;
import com.example.editor.gamification.RewardDefinition;
import com.example.editor.projection.drawio.DrawioArtifactExporter;
import com.example.editor.projection.drawio.DrawioExportOptions;
import com.example.editor.projection.drawio.DrawioGraphBuilder;

/** Proyección modular del catálogo y la progresión de gamificación. */
@Component
public class GamificationDefinitionDrawioExporter implements DrawioArtifactExporter<GamificationDefinitionModel> {
    public static final String ARTIFACT_TYPE="gamification-definition";
    /** Descripción: Ejecuta la operación «artifactType» correspondiente a la responsabilidad de GamificationDefinitionDrawioExporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public String artifactType() { return ARTIFACT_TYPE; }
    /** Descripción: Ejecuta la operación «sourceType» correspondiente a la responsabilidad de GamificationDefinitionDrawioExporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Class<GamificationDefinitionModel> sourceType() { return GamificationDefinitionModel.class; }
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de GamificationDefinitionDrawioExporter. Entradas: source: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ @Override public String export(GamificationDefinitionModel source,DrawioExportOptions options) {
        DrawioGraphBuilder graph=new DrawioGraphBuilder(source.id(),options.title().isBlank()?source.name():options.title());
        graph.vertex("definition",DrawioGraphBuilder.html(source.name()+"\n"+source.id()),40,40,280,85,
                "rounded=1;whiteSpace=wrap;html=1;fillColor=#dae8fc;strokeColor=#6c8ebf;fontStyle=1;");
        int index=0;
        for(PointTypeDefinition point:source.pointTypes()) {
            graph.vertex(point.id(),DrawioGraphBuilder.html("POINTS\n"+point.name()),390,40+index*105,210,70,
                    "rounded=1;whiteSpace=wrap;html=1;fillColor=#d5e8d4;strokeColor=#82b366;");
            graph.edge("definition-point-"+point.id(),"defines","definition",point.id(),"endArrow=block;html=1;"); index++;
        }
        index=0;
        for(LevelDefinition level:source.levels()) {
            graph.vertex(level.id(),DrawioGraphBuilder.html("LEVEL\n"+level.name()+"\n"+level.minimumPoints()+" "+level.pointTypeId()),680,40+index*105,220,75,
                    "rounded=1;whiteSpace=wrap;html=1;fillColor=#e1d5e7;strokeColor=#9673a6;");
            graph.edge("point-level-"+level.id(),"progression",level.pointTypeId(),level.id(),"endArrow=block;html=1;"); index++;
        }
        index=0;
        for(AchievementDefinition achievement:source.achievements()) {
            graph.vertex(achievement.id(),DrawioGraphBuilder.html("ACHIEVEMENT\n"+achievement.name()),980,40+index*105,220,70,
                    "rounded=1;whiteSpace=wrap;html=1;fillColor=#fff2cc;strokeColor=#d6b656;"); index++;
        }
        for(ChallengeDefinition challenge:source.challenges()) {
            graph.vertex(challenge.id(),DrawioGraphBuilder.html("CHALLENGE\n"+challenge.name()+"\nTarget: "+challenge.targetCount()),980,40+index*105,220,75,
                    "rounded=1;whiteSpace=wrap;html=1;fillColor=#ffe6cc;strokeColor=#d79b00;"); index++;
        }
        for(RewardDefinition reward:source.rewards()) {
            graph.vertex(reward.id(),DrawioGraphBuilder.html("REWARD\n"+reward.name()),1260,40+index*95,220,70,
                    "rounded=1;whiteSpace=wrap;html=1;fillColor=#d5e8d4;strokeColor=#82b366;"); index++;
        }
        return graph.build();
    }
}
