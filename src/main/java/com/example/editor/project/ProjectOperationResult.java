package com.example.editor.project;

/** Resultado común de una operación de escritura. */
public record ProjectOperationResult(ProjectSession session,String relativePath,String etag,ProjectTreeNode tree) {
    public ProjectOperationResult { relativePath = relativePath == null ? "" : relativePath; etag = etag == null ? "" : etag; }
}
