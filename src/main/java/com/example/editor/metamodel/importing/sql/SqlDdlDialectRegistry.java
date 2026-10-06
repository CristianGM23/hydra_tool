package com.example.editor.metamodel.importing.sql;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/** Registry isolated from parsing so dialect profiles can evolve without branching the canonical MetaModel. */
public final class SqlDdlDialectRegistry {
    private final Map<SqlDdlDialect,SqlDdlDialectProfile> profiles;

    /** Descripción: Inicializa una instancia de SqlDdlDialectRegistry con sus dependencias y datos de entrada. Entradas: overrides: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public SqlDdlDialectRegistry(Map<SqlDdlDialect,SqlDdlDialectProfile> overrides) {
        EnumMap<SqlDdlDialect,SqlDdlDialectProfile> values = new EnumMap<>(defaults());
        if(overrides != null) values.putAll(overrides);
        profiles = Map.copyOf(values);
    }
    /** Descripción: Inicializa una instancia de SqlDdlDialectRegistry con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ public SqlDdlDialectRegistry() { this(Map.of()); }
    /** Descripción: Valida las condiciones necesarias para «require» y rechaza entradas no válidas. Entradas: dialect: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public SqlDdlDialectProfile require(SqlDdlDialect dialect) {
        SqlDdlDialectProfile profile = profiles.get(dialect == null ? SqlDdlDialect.ANSI : dialect);
        if(profile == null) throw new IllegalArgumentException("Unsupported SQL DDL dialect: " + dialect);
        return profile;
    }
    /** Descripción: Ejecuta la operación «defaults» correspondiente a la responsabilidad de SqlDdlDialectRegistry. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<SqlDdlDialect,SqlDdlDialectProfile> defaults() {
        EnumMap<SqlDdlDialect,SqlDdlDialectProfile> values = new EnumMap<>(SqlDdlDialect.class);
        values.put(SqlDdlDialect.ANSI, profile(SqlDdlDialect.ANSI, SqlDdlCapability.SCHEMA_QUALIFIED_NAMES, SqlDdlCapability.ALTER_TABLE_CONSTRAINTS));
        values.put(SqlDdlDialect.POSTGRESQL, profile(SqlDdlDialect.POSTGRESQL, SqlDdlCapability.SCHEMA_QUALIFIED_NAMES, SqlDdlCapability.CREATE_TYPE_ENUM, SqlDdlCapability.ALTER_TABLE_CONSTRAINTS, SqlDdlCapability.IDENTITY_COLUMNS, SqlDdlCapability.GENERATED_COLUMNS, SqlDdlCapability.DEFERRABLE_CONSTRAINTS));
        values.put(SqlDdlDialect.MYSQL, profile(SqlDdlDialect.MYSQL, SqlDdlCapability.SCHEMA_QUALIFIED_NAMES, SqlDdlCapability.ALTER_TABLE_CONSTRAINTS, SqlDdlCapability.AUTO_INCREMENT_COLUMNS, SqlDdlCapability.GENERATED_COLUMNS));
        values.put(SqlDdlDialect.SQL_SERVER, profile(SqlDdlDialect.SQL_SERVER, SqlDdlCapability.SCHEMA_QUALIFIED_NAMES, SqlDdlCapability.ALTER_TABLE_CONSTRAINTS, SqlDdlCapability.IDENTITY_COLUMNS, SqlDdlCapability.GENERATED_COLUMNS));
        values.put(SqlDdlDialect.ORACLE, profile(SqlDdlDialect.ORACLE, SqlDdlCapability.SCHEMA_QUALIFIED_NAMES, SqlDdlCapability.ALTER_TABLE_CONSTRAINTS, SqlDdlCapability.IDENTITY_COLUMNS, SqlDdlCapability.GENERATED_COLUMNS, SqlDdlCapability.DEFERRABLE_CONSTRAINTS));
        values.put(SqlDdlDialect.SQLITE, profile(SqlDdlDialect.SQLITE, SqlDdlCapability.GENERATED_COLUMNS));
        return values;
    }
    /** Descripción: Ejecuta la operación «profile» correspondiente a la responsabilidad de SqlDdlDialectRegistry. Entradas: dialect: dato de entrada necesario para la operación; capabilities: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static SqlDdlDialectProfile profile(SqlDdlDialect dialect, SqlDdlCapability... capabilities) {
        return new SqlDdlDialectProfile(dialect, Set.of(capabilities));
    }
}
