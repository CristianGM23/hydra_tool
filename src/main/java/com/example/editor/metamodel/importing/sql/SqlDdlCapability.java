package com.example.editor.metamodel.importing.sql;

/** Dialect capabilities used for diagnostics and future renderer profiles. */
public enum SqlDdlCapability {
    SCHEMA_QUALIFIED_NAMES,
    CREATE_TYPE_ENUM,
    ALTER_TABLE_CONSTRAINTS,
    IDENTITY_COLUMNS,
    AUTO_INCREMENT_COLUMNS,
    GENERATED_COLUMNS,
    DEFERRABLE_CONSTRAINTS
}
