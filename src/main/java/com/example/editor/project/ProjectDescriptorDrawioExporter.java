package com.example.editor.project;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import com.example.editor.projection.drawio.DrawioArtifactExporter;
import com.example.editor.projection.drawio.DrawioExportOptions;
import com.example.editor.projection.drawio.DrawioGraphBuilder;

/** Proyecta el contrato y las capacidades de un proyecto a draw.io. */
@Component
public class ProjectDescriptorDrawioExporter implements DrawioArtifactExporter<ProjectDescriptor> {
    /** Descripción: Ejecuta la operación «artifactType» correspondiente a la responsabilidad de ProjectDescriptorDrawioExporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public String artifactType() { return "project-descriptor"; }
    /** Descripción: Ejecuta la operación «sourceType» correspondiente a la responsabilidad de ProjectDescriptorDrawioExporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Class<ProjectDescriptor> sourceType() { return ProjectDescriptor.class; }
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de ProjectDescriptorDrawioExporter. Entradas: source: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ @Override public String export(ProjectDescriptor source,DrawioExportOptions options) {
        String title=options.title().isBlank()?source.name():options.title();
        DrawioGraphBuilder graph=new DrawioGraphBuilder("project-"+source.id(),title);
        String root="project";
        graph.vertex(root,DrawioGraphBuilder.html(source.name()+"\n"+source.id()+"\n"+source.storageMode()),40,40,300,110,"rounded=1;whiteSpace=wrap;html=1;fillColor=#dbeafe;strokeColor=#2563eb;fontStyle=1;");
        List<String> capabilities=new ArrayList<>();
        for(ProjectCapability capability:source.enabledCapabilities()) capabilities.add(capability.name());
        graph.vertex("capabilities",DrawioGraphBuilder.html("Capabilities\n"+String.join("\n",capabilities)),400,40,300,Math.max(100,40+capabilities.size()*18),"rounded=1;whiteSpace=wrap;html=1;fillColor=#f0fdf4;strokeColor=#16a34a;");
        graph.edge("edge-capabilities","enables",root,"capabilities","endArrow=block;html=1;");
        int index=0;
        for(var entry:source.layout().logicalRoots().entrySet()) {
            double x=40+(index%4)*280; double y=240+(index/4)*110;
            String id="root-"+index;
            graph.vertex(id,DrawioGraphBuilder.html(entry.getKey()+"\n"+entry.getValue()),x,y,240,70,"rounded=1;whiteSpace=wrap;html=1;fillColor=#f8fafc;strokeColor=#64748b;");
            graph.edge("edge-"+id,"",root,id,"endArrow=open;html=1;"); index++;
        }
        return graph.build();
    }
}
