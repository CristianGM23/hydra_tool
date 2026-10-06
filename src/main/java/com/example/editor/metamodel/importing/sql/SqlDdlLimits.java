package com.example.editor.metamodel.importing.sql;

/** Resource budgets that keep hostile or accidental SQL input bounded before it reaches the neutral model. */
public record SqlDdlLimits(int maxSourceCharacters, int maxTokens, int maxStatements, int maxTables,
        int maxColumnsPerTable, int maxConstraintsPerTable, int maxIdentifierCharacters, int maxParenthesisDepth) {
    public SqlDdlLimits {
        if(maxSourceCharacters < 1 || maxTokens < 1 || maxStatements < 1 || maxTables < 1
                || maxColumnsPerTable < 1 || maxConstraintsPerTable < 1 || maxIdentifierCharacters < 1
                || maxParenthesisDepth < 1) throw new IllegalArgumentException("SQL DDL limits must be positive.");
    }
    /** Descripción: Ejecuta la operación «defaults» correspondiente a la responsabilidad de SqlDdlLimits. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static SqlDdlLimits defaults() {
        return new SqlDdlLimits(8_000_000, 500_000, 20_000, 10_000, 5_000, 5_000, 512, 64);
    }
}
