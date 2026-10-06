package com.example.editor.transformation.text;

import java.util.List;

/** Resultado M2T determinista; los artefactos permanecen en memoria hasta que otra capa decida exportarlos. */
public record TransformationGenerationResult(String transformationId,List<TransformationTextArtifact> artifacts) {
    public TransformationGenerationResult {
        transformationId=transformationId==null?"":transformationId;
        artifacts=artifacts==null?List.of():List.copyOf(artifacts);
    }
}
