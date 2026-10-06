package com.example.editor.project;

import java.time.Instant;
import java.util.List;

/** Nodo neutral consumido por Project Explorer. */
public record ProjectTreeNode(
        String id,
        String name,
        String relativePath,
        ProjectNodeType nodeType,
        ArtifactKind artifactKind,
        FileOwnership ownership,
        ProjectNodeStatus status,
        boolean editable,
        long size,
        Instant lastModified,
        String etag,
        List<ProjectTreeNode> children
) {
    public ProjectTreeNode {
        relativePath = relativePath == null ? "" : relativePath;
        artifactKind = artifactKind == null ? ArtifactKind.GENERIC_FILE : artifactKind;
        ownership = ownership == null ? FileOwnership.USER_MANAGED : ownership;
        status = status == null ? ProjectNodeStatus.NORMAL : status;
        etag = etag == null ? "" : etag;
        children = children == null ? List.of() : List.copyOf(children);
    }
}
