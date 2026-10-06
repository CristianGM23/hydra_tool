package com.example.editor.transformation.text;

/** Artefacto textual generado en memoria por una regla M2T. */
public record TransformationTextArtifact(String path,String content,String ruleId,String sourceElementId) {
    public TransformationTextArtifact {
        path=path==null?"":path;
        content=content==null?"":content;
        ruleId=ruleId==null?"":ruleId;
        sourceElementId=sourceElementId==null?"":sourceElementId;
    }
}
