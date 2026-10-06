package com.example.editor.metamodel.importing.sql;

import java.util.List;
import java.util.Map;

/** SQL-specific AST. It is intentionally an adapter layer before the canonical neutral MetaModel. */
public final class SqlDdlAst {
    /** Descripción: Inicializa una instancia de SqlDdlAst con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private SqlDdlAst() {}

    public record Document(SqlDdlDialect dialect, List<Table> tables, List<EnumType> enums, List<Diagnostic> diagnostics) {
        public Document { tables=copy(tables); enums=copy(enums); diagnostics=copy(diagnostics); }
    }
    public record Table(String qualifiedName, List<Column> columns, List<PrimaryKey> primaryKeys,
                        List<ForeignKey> foreignKeys, List<UniqueKey> uniqueKeys, Map<String,String> annotations) {
        public Table { columns=copy(columns); primaryKeys=copy(primaryKeys); foreignKeys=copy(foreignKeys); uniqueKeys=copy(uniqueKeys); annotations=annotations==null?Map.of():Map.copyOf(annotations); }
    }
    public record Column(String name, String sqlType, boolean nullable, String defaultExpression,
                         boolean primaryKey, boolean unique, boolean generated, Map<String,String> annotations) {
        public Column { defaultExpression=defaultExpression==null?"":defaultExpression; annotations=annotations==null?Map.of():Map.copyOf(annotations); }
    }
    public record PrimaryKey(String name, List<String> columns) { public PrimaryKey { columns=copy(columns); } }
    public record ForeignKey(String name, List<String> columns, String targetTable, List<String> targetColumns,
                             String onDelete, String onUpdate) {
        public ForeignKey { columns=copy(columns); targetColumns=copy(targetColumns); onDelete=safe(onDelete); onUpdate=safe(onUpdate); }
    }
    public record UniqueKey(String name, List<String> columns) { public UniqueKey { columns=copy(columns); } }
    public record EnumType(String qualifiedName, List<String> literals) { public EnumType { literals=copy(literals); } }
    public record Diagnostic(String code, Severity severity, int line, int column, String message) {}
    public enum Severity { INFO, WARNING, ERROR }
    /** Descripción: Ejecuta la operación «copy» correspondiente a la responsabilidad de SqlDdlAst. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static <T> List<T> copy(List<T> values) { return values==null?List.of():List.copyOf(values); }
    /** Descripción: Ejecuta la operación «safe» correspondiente a la responsabilidad de SqlDdlAst. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String safe(String value) { return value==null?"":value; }
}
