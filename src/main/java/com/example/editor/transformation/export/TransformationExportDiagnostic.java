package com.example.editor.transformation.export;

/** Diagnóstico de compatibilidad previo a generar artefactos de una tecnología externa. */
public record TransformationExportDiagnostic(String severity,String code,String path,String message,String solution) {
    public TransformationExportDiagnostic {
        severity=text(severity); code=text(code); path=text(path); message=text(message); solution=text(solution);
    }
    /** Normaliza texto opcional. */ /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de TransformationExportDiagnostic. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value==null?"":value.trim();}
}
