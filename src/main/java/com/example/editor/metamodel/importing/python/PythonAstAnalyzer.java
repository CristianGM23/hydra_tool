package com.example.editor.metamodel.importing.python;

import java.util.List;

import com.example.editor.metamodel.importing.ImportSourceEntry;

public interface PythonAstAnalyzer {
    /** Descripción: Ejecuta la operación «analyze» correspondiente a la responsabilidad de PythonAstAnalyzer. Entradas: sources: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ PythonProjectAnalysis analyze(List<ImportSourceEntry> sources);
}
