package com.example.editor.project;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.example.editor.projection.drawio.DrawioArtifactExporter;
import com.example.editor.projection.drawio.DrawioExportOptions;
import com.example.editor.projection.drawio.DrawioGraphBuilder;

/** Proyecta el árbol lógico o físico completo a draw.io. */
@Component
public class ProjectTreeDrawioExporter implements DrawioArtifactExporter<ProjectTreeNode> {
    /** Descripción: Ejecuta la operación «artifactType» correspondiente a la responsabilidad de Item. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public String artifactType() { return "project-tree"; }
    /** Descripción: Ejecuta la operación «sourceType» correspondiente a la responsabilidad de Item. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Class<ProjectTreeNode> sourceType() { return ProjectTreeNode.class; }
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de Item. Entradas: source: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ @Override public String export(ProjectTreeNode source,DrawioExportOptions options) {
        DrawioGraphBuilder graph=new DrawioGraphBuilder("project-tree",options.title().isBlank()?source.name():options.title());
        ArrayDeque<Item> queue=new ArrayDeque<>(); queue.add(new Item(source,null,0));
        Map<Integer,Integer> rows=new HashMap<>(); int sequence=0;
        while(!queue.isEmpty()) {
            Item item=queue.removeFirst(); int row=rows.merge(item.depth(),1,Integer::sum)-1;
            String id="node-"+(sequence++); double x=40+item.depth()*300; double y=30+row*86;
            String style=item.node().nodeType()==ProjectNodeType.PROJECT
                    ? "rounded=1;whiteSpace=wrap;html=1;fillColor=#dbeafe;strokeColor=#2563eb;fontStyle=1;"
                    : item.node().nodeType()==ProjectNodeType.FILE
                        ? "rounded=1;whiteSpace=wrap;html=1;fillColor=#ffffff;strokeColor=#94a3b8;"
                        : "rounded=1;whiteSpace=wrap;html=1;fillColor=#f1f5f9;strokeColor=#64748b;";
            graph.vertex(id,DrawioGraphBuilder.html(item.node().name()+"\n"+item.node().artifactKind()+"\n"+item.node().relativePath()),x,y,260,62,style);
            if(item.parentId()!=null) graph.edge("edge-"+id,"",item.parentId(),id,"endArrow=open;html=1;");
            for(ProjectTreeNode child:item.node().children()) queue.addLast(new Item(child,id,item.depth()+1));
        }
        return graph.build();
    }
    private record Item(ProjectTreeNode node,String parentId,int depth) { }
}
