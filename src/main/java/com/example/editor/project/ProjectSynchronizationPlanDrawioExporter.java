package com.example.editor.project;

import org.springframework.stereotype.Component;
import com.example.editor.projection.drawio.DrawioArtifactExporter;
import com.example.editor.projection.drawio.DrawioExportOptions;
import com.example.editor.projection.drawio.DrawioGraphBuilder;

/** Visualiza cambios CREATE/REPLACE/SKIP antes de sincronizar. */
@Component
public class ProjectSynchronizationPlanDrawioExporter implements DrawioArtifactExporter<ProjectSynchronizationPlan> {
    /** Descripción: Ejecuta la operación «artifactType» correspondiente a la responsabilidad de ProjectSynchronizationPlanDrawioExporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public String artifactType() { return "project-sync-plan"; }
    /** Descripción: Ejecuta la operación «sourceType» correspondiente a la responsabilidad de ProjectSynchronizationPlanDrawioExporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Class<ProjectSynchronizationPlan> sourceType() { return ProjectSynchronizationPlan.class; }
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de ProjectSynchronizationPlanDrawioExporter. Entradas: source: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ @Override public String export(ProjectSynchronizationPlan source,DrawioExportOptions options) {
        DrawioGraphBuilder graph=new DrawioGraphBuilder("project-sync",options.title().isBlank()?"Project synchronization":options.title());
        graph.vertex("source",source.direction()==WorkspaceTransferDirection.WORKSPACE_TO_PROJECT?"Current workspace":"Local project",40,40,220,80,"rounded=1;whiteSpace=wrap;html=1;fillColor=#dbeafe;strokeColor=#2563eb;fontStyle=1;");
        graph.vertex("target",source.direction()==WorkspaceTransferDirection.WORKSPACE_TO_PROJECT?"Local project":"Current workspace",780,40,220,80,"rounded=1;whiteSpace=wrap;html=1;fillColor=#f0fdf4;strokeColor=#16a34a;fontStyle=1;");
        int row=0;
        for(ProjectSyncFileChange change:source.changes()) {
            String id="change-"+row; String fill=switch(change.operation()) { case "CREATE" -> "#dcfce7"; case "REPLACE" -> "#fef3c7"; default -> "#f1f5f9"; };
            graph.vertex(id,DrawioGraphBuilder.html(change.operation()+"\n"+change.artifactKind()+"\n"+change.destination()),340,20+row*92,380,72,"rounded=1;whiteSpace=wrap;html=1;fillColor="+fill+";strokeColor=#64748b;");
            graph.edge("in-"+row,"","source",id,"endArrow=open;html=1;"); graph.edge("out-"+row,"",id,"target","endArrow=block;html=1;"); row++;
        }
        return graph.build();
    }
}
