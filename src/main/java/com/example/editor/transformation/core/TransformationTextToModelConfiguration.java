package com.example.editor.transformation.core;

/** Configuración T2M: gramática activa y proyección M2M opcional posterior. */
public record TransformationTextToModelConfiguration(String grammarId,String relativePath,String postTransformationId) {
    public TransformationTextToModelConfiguration {
        grammarId=text(grammarId); relativePath=text(relativePath); postTransformationId=text(postTransformationId);
        if(relativePath.isBlank()) relativePath="model.hydra";
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de TransformationTextToModelConfiguration. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value==null?"":value.trim();}
}
