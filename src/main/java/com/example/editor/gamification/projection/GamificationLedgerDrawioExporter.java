package com.example.editor.gamification.projection;

import org.springframework.stereotype.Component;
import com.example.editor.gamification.GamificationLedger;
import com.example.editor.gamification.GamificationLedgerEntry;
import com.example.editor.projection.drawio.DrawioArtifactExporter;
import com.example.editor.projection.drawio.DrawioExportOptions;
import com.example.editor.projection.drawio.DrawioGraphBuilder;

/** Proyección auditable y resumida del ledger compensable. */
@Component
public class GamificationLedgerDrawioExporter implements DrawioArtifactExporter<GamificationLedger> {
    public static final String ARTIFACT_TYPE="gamification-ledger";
    /** Descripción: Ejecuta la operación «artifactType» correspondiente a la responsabilidad de GamificationLedgerDrawioExporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public String artifactType() { return ARTIFACT_TYPE; }
    /** Descripción: Ejecuta la operación «sourceType» correspondiente a la responsabilidad de GamificationLedgerDrawioExporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Class<GamificationLedger> sourceType() { return GamificationLedger.class; }
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de GamificationLedgerDrawioExporter. Entradas: source: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ @Override public String export(GamificationLedger source,DrawioExportOptions options) {
        DrawioGraphBuilder graph=new DrawioGraphBuilder(source.id(),options.title().isBlank()?"Gamification ledger":options.title());
        int index=0;
        for(GamificationLedgerEntry entry:source.entries()) {
            double x=40+(index%4)*300; double y=40+(index/4)*135;
            String value=entry.status()+"\nActor: "+entry.actorId()+"\n"+entry.pointsDelta()+" "+entry.pointsTypeId()+"\nEvent: "+entry.sourceEventId();
            graph.vertex(entry.id(),DrawioGraphBuilder.html(value),x,y,260,95,
                    entry.compensationOf().isBlank()?"rounded=1;whiteSpace=wrap;html=1;fillColor=#d5e8d4;strokeColor=#82b366;":"rounded=1;whiteSpace=wrap;html=1;fillColor=#f8cecc;strokeColor=#b85450;");
            if(!entry.compensationOf().isBlank()) graph.edge("compensation-"+entry.id(),"compensates",entry.id(),entry.compensationOf(),"edgeStyle=orthogonalEdgeStyle;html=1;endArrow=block;dashed=1;");
            index++;
        }
        return graph.build();
    }
}
