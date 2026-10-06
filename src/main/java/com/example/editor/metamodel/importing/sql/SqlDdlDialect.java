package com.example.editor.metamodel.importing.sql;

/** Dialect hints recovered without making the neutral MetaModel depend on SQL. */
public enum SqlDdlDialect {
    ANSI, POSTGRESQL, MYSQL, SQL_SERVER, ORACLE, SQLITE;

    /** Descripción: Ejecuta la operación «infer» correspondiente a la responsabilidad de SqlDdlDialect. Entradas: source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static SqlDdlDialect infer(String source) {
        String text = source == null ? "" : source.toLowerCase(java.util.Locale.ROOT);
        if(text.contains("`") || text.contains("auto_increment") || text.contains("engine=")) return MYSQL;
        if(text.contains("serial") || text.contains("jsonb") || text.contains("create type") && text.contains(" as enum")) return POSTGRESQL;
        if(text.contains("identity(") || text.contains("nvarchar") || text.contains("[dbo]")) return SQL_SERVER;
        if(text.contains("varchar2") || text.contains("number(") || text.contains("sequence")) return ORACLE;
        if(text.contains("pragma ") || text.contains("without rowid") || text.contains("sqlite_")) return SQLITE;
        return ANSI;
    }
}
