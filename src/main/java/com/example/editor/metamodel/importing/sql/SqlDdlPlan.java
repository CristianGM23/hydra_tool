package com.example.editor.metamodel.importing.sql;

import java.util.List;

/** Safe, non-executing plan used only to make reference ordering and cycles explicit. */
public record SqlDdlPlan(List<SqlDdlAst.Table> orderedTables, List<DeferredForeignKey> deferredForeignKeys,
        List<SqlDdlAst.Diagnostic> diagnostics) {
    public SqlDdlPlan { orderedTables = List.copyOf(orderedTables); deferredForeignKeys = List.copyOf(deferredForeignKeys); diagnostics = List.copyOf(diagnostics); }
    public record DeferredForeignKey(String sourceTable, SqlDdlAst.ForeignKey foreignKey) { }
}
