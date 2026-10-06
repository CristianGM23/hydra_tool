package com.example.editor.project.recovery;

import java.util.List;

/** Nodo de la topología inferida del proyecto externo. */
public record ExternalProjectTopologyNode(String id, String label, String type, String path,
        String parentId, List<String> technologies) {
    public ExternalProjectTopologyNode {
        id = id == null ? "" : id;
        label = label == null ? "" : label;
        type = type == null ? "RESOURCE" : type;
        path = path == null ? "" : path;
        parentId = parentId == null ? "" : parentId;
        technologies = technologies == null ? List.of() : List.copyOf(technologies);
    }
}
