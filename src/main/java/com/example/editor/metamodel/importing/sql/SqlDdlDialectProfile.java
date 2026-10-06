package com.example.editor.metamodel.importing.sql;

import java.util.Set;

/** Immutable capabilities for one SQL family. */
public record SqlDdlDialectProfile(SqlDdlDialect dialect, Set<SqlDdlCapability> capabilities) {
    public SqlDdlDialectProfile { capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities); }
    /** Descripción: Evalúa la condición «supports» sin modificar el estado externo. Entradas: capability: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean supports(SqlDdlCapability capability) { return capabilities.contains(capability); }
}
