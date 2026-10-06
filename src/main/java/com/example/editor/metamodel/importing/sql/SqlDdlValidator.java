package com.example.editor.metamodel.importing.sql;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.example.editor.metamodel.importing.sql.SqlDdlAst.Diagnostic;
import com.example.editor.metamodel.importing.sql.SqlDdlAst.ForeignKey;
import com.example.editor.metamodel.importing.sql.SqlDdlAst.Severity;
import com.example.editor.metamodel.importing.sql.SqlDdlAst.Table;

/** Semantic validator that rejects ambiguity and reports every avoidable structural loss before persistence. */
public final class SqlDdlValidator {
    private final SqlDdlLimits limits;
    private final SqlDdlDialectRegistry dialects;
    /** Descripción: Inicializa una instancia de SqlDdlValidator con sus dependencias y datos de entrada. Entradas: limits: dato de entrada necesario para la operación; dialects: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public SqlDdlValidator(SqlDdlLimits limits, SqlDdlDialectRegistry dialects) {
        this.limits = limits == null ? SqlDdlLimits.defaults() : limits;
        this.dialects = dialects == null ? new SqlDdlDialectRegistry() : dialects;
    }
    /** Descripción: Inicializa una instancia de SqlDdlValidator con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ public SqlDdlValidator() { this(SqlDdlLimits.defaults(), new SqlDdlDialectRegistry()); }

    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public SqlDdlValidationResult validate(SqlDdlAst.Document document) {
        enforceResourceLimits(document);
        List<Diagnostic> out = new ArrayList<>();
        Map<String,Table> exact = new HashMap<>(); Map<String,List<Table>> simple = new HashMap<>();
        for(Table table : document.tables()) {
            String tableKey = key(table.qualifiedName());
            if(exact.putIfAbsent(tableKey, table) != null) error(out, "SQL_DDL_DUPLICATE_TABLE", "Table is declared more than once: " + table.qualifiedName() + ".");
            simple.computeIfAbsent(key(simple(table.qualifiedName())), ignored -> new ArrayList<>()).add(table);
            validateTable(table, out);
        }
        validateEnums(document, out);
        for(Table table : document.tables()) for(ForeignKey fk : table.foreignKeys()) validateForeignKey(table, fk, exact, simple, out);
        SqlDdlDialectProfile profile = dialects.require(document.dialect());
        if(!document.enums().isEmpty() && !profile.supports(SqlDdlCapability.CREATE_TYPE_ENUM) && document.dialect() != SqlDdlDialect.ANSI) {
            warning(out, "SQL_DDL_DIALECT_ENUM_EXTENSION", "CREATE TYPE ... AS ENUM is not native to detected dialect " + document.dialect() + "; values are preserved in the neutral enum model.");
        }
        return new SqlDdlValidationResult(out);
    }


    /** Descripción: Aplica los presupuestos de seguridad del SQL DDL antes de cualquier mapeo al MetaModel canónico. Entrada: document contiene el AST ya parseado que debe permanecer dentro de los límites configurados. Salida: no devuelve valor; lanza SqlDdlSafetyException cuando un presupuesto de recursos se supera. */
    /** Descripción: Ejecuta la operación «enforceResourceLimits» correspondiente a la responsabilidad de SqlDdlValidator. Entradas: document: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void enforceResourceLimits(SqlDdlAst.Document document) {
        if(document.tables().size() > limits.maxTables()) throw new SqlDdlSafetyException("SQL DDL table count exceeds configured limit of " + limits.maxTables() + ".");
        for(Table table : document.tables()) {
            if(table.qualifiedName().length() > limits.maxIdentifierCharacters()) throw new SqlDdlSafetyException("SQL DDL table identifier exceeds configured length limit of " + limits.maxIdentifierCharacters() + ": " + table.qualifiedName() + ".");
            if(table.columns().size() > limits.maxColumnsPerTable()) throw new SqlDdlSafetyException("SQL DDL column count for table " + table.qualifiedName() + " exceeds configured limit of " + limits.maxColumnsPerTable() + ".");
            int constraints = table.primaryKeys().size() + table.foreignKeys().size() + table.uniqueKeys().size();
            if(constraints > limits.maxConstraintsPerTable()) throw new SqlDdlSafetyException("SQL DDL constraint count for table " + table.qualifiedName() + " exceeds configured limit of " + limits.maxConstraintsPerTable() + ".");
            for(var column : table.columns()) if(column.name().length() > limits.maxIdentifierCharacters()) throw new SqlDdlSafetyException("SQL DDL column identifier exceeds configured length limit of " + limits.maxIdentifierCharacters() + " in table " + table.qualifiedName() + ": " + column.name() + ".");
        }
        for(var value : document.enums()) if(value.qualifiedName().length() > limits.maxIdentifierCharacters()) throw new SqlDdlSafetyException("SQL DDL enum identifier exceeds configured length limit of " + limits.maxIdentifierCharacters() + ": " + value.qualifiedName() + ".");
    }

    /** Descripción: Valida las condiciones necesarias para «validateTable» y rechaza entradas no válidas. Entradas: table: dato de entrada necesario para la operación; out: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateTable(Table table, List<Diagnostic> out) {
        Set<String> columns = new HashSet<>();
        for(var column : table.columns()) {
            if(!columns.add(key(column.name()))) error(out, "SQL_DDL_DUPLICATE_COLUMN", "Column " + column.name() + " is declared more than once in table " + table.qualifiedName() + ".");
        }
        for(var pk : table.primaryKeys()) validateColumns(table, pk.columns(), columns, "primary key", out);
        for(var unique : table.uniqueKeys()) validateColumns(table, unique.columns(), columns, "unique constraint", out);
        for(var fk : table.foreignKeys()) validateColumns(table, fk.columns(), columns, "foreign key", out);
    }

    /** Descripción: Valida las condiciones necesarias para «validateColumns» y rechaza entradas no válidas. Entradas: table: dato de entrada necesario para la operación; values: colección de elementos que debe procesarse; known: dato de entrada necesario para la operación; kind: dato de entrada necesario para la operación; out: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateColumns(Table table, List<String> values, Set<String> known, String kind, List<Diagnostic> out) {
        if(values.isEmpty()) error(out, "SQL_DDL_EMPTY_CONSTRAINT", "A " + kind + " in table " + table.qualifiedName() + " has no columns.");
        for(String value : values) if(!known.contains(key(value))) error(out, "SQL_DDL_UNKNOWN_CONSTRAINT_COLUMN", "The " + kind + " in table " + table.qualifiedName() + " references unknown column " + value + ".");
    }

    /** Descripción: Valida las condiciones necesarias para «validateForeignKey» y rechaza entradas no válidas. Entradas: source: dato de entrada necesario para la operación; fk: dato de entrada necesario para la operación; exact: dato de entrada necesario para la operación; bySimple: dato de entrada necesario para la operación; out: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateForeignKey(Table source, ForeignKey fk, Map<String,Table> exact, Map<String,List<Table>> bySimple, List<Diagnostic> out) {
        Table target = exact.get(key(fk.targetTable()));
        if(target == null) {
            List<Table> candidates = bySimple.getOrDefault(key(simple(fk.targetTable())), List.of());
            if(candidates.size() == 1) target = candidates.get(0);
            else if(candidates.size() > 1) {
                error(out, "SQL_DDL_AMBIGUOUS_FOREIGN_KEY_TARGET", "Foreign key " + display(fk) + " in table " + source.qualifiedName() + " refers to ambiguous table " + fk.targetTable() + ". Use a schema-qualified name."); return;
            }
        }
        if(target == null) {
            warning(out, "SQL_DDL_UNRESOLVED_FOREIGN_KEY_TARGET", "Foreign key " + display(fk) + " in table " + source.qualifiedName() + " refers to table " + fk.targetTable() + " which is not declared in this import."); return;
        }
        Set<String> targetColumns = new HashSet<>(); for(var column : target.columns()) targetColumns.add(key(column.name()));
        for(String value : fk.targetColumns()) if(!targetColumns.contains(key(value))) error(out, "SQL_DDL_UNKNOWN_FOREIGN_KEY_TARGET_COLUMN", "Foreign key " + display(fk) + " refers to unknown target column " + value + " in table " + target.qualifiedName() + ".");
        if(fk.columns().size() != fk.targetColumns().size()) error(out, "SQL_DDL_FOREIGN_KEY_ARITY", "Foreign key " + display(fk) + " in table " + source.qualifiedName() + " has different source and target column counts.");
    }

    /** Descripción: Valida las condiciones necesarias para «validateEnums» y rechaza entradas no válidas. Entradas: document: dato de entrada necesario para la operación; out: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateEnums(SqlDdlAst.Document document, List<Diagnostic> out) {
        Set<String> names = new HashSet<>();
        for(var value : document.enums()) {
            if(!names.add(key(value.qualifiedName()))) error(out, "SQL_DDL_DUPLICATE_ENUM", "Enum type is declared more than once: " + value.qualifiedName() + ".");
            if(value.literals().isEmpty()) error(out, "SQL_DDL_EMPTY_ENUM", "Enum type has no literals: " + value.qualifiedName() + ".");
            Set<String> literals = new HashSet<>(); for(String literal : value.literals()) if(!literals.add(literal)) error(out, "SQL_DDL_DUPLICATE_ENUM_LITERAL", "Enum " + value.qualifiedName() + " repeats literal " + literal + ".");
        }
    }
    /** Descripción: Ejecuta la operación «error» correspondiente a la responsabilidad de SqlDdlValidator. Entradas: out: dato de entrada necesario para la operación; code: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void error(List<Diagnostic> out, String code, String message) { out.add(new Diagnostic(code, Severity.ERROR, 1, 1, message)); }
    /** Descripción: Ejecuta la operación «warning» correspondiente a la responsabilidad de SqlDdlValidator. Entradas: out: dato de entrada necesario para la operación; code: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void warning(List<Diagnostic> out, String code, String message) { out.add(new Diagnostic(code, Severity.WARNING, 1, 1, message)); }
    /** Descripción: Ejecuta la operación «display» correspondiente a la responsabilidad de SqlDdlValidator. Entradas: fk: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String display(ForeignKey fk) { return fk.name().isBlank() ? String.join("_", fk.columns()) : fk.name(); }
    /** Descripción: Ejecuta la operación «simple» correspondiente a la responsabilidad de SqlDdlValidator. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String simple(String value) { int dot = value.lastIndexOf('.'); return dot < 0 ? value : value.substring(dot + 1); }
    /** Descripción: Ejecuta la operación «key» correspondiente a la responsabilidad de SqlDdlValidator. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String key(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT); }
}
