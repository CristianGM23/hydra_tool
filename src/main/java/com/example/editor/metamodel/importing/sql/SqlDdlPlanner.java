package com.example.editor.metamodel.importing.sql;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.example.editor.metamodel.importing.sql.SqlDdlAst.Diagnostic;
import com.example.editor.metamodel.importing.sql.SqlDdlAst.Severity;
import com.example.editor.metamodel.importing.sql.SqlDdlAst.Table;

/** Dependency planner. It never executes SQL; cycles become deferred-reference metadata rather than unsafe ordering guesses. */
public final class SqlDdlPlanner {
    /** Descripción: Ejecuta la operación «plan» correspondiente a la responsabilidad de SqlDdlPlanner. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public SqlDdlPlan plan(SqlDdlAst.Document document) {
        Map<String,Table> tables = new LinkedHashMap<>(); Map<String,String> simple = new HashMap<>(); Set<String> ambiguous = new LinkedHashSet<>();
        for(Table table : document.tables()) {
            tables.put(key(table.qualifiedName()), table); String simpleKey = key(simple(table.qualifiedName()));
            if(simple.putIfAbsent(simpleKey, key(table.qualifiedName())) != null) ambiguous.add(simpleKey);
        }
        Map<String,Set<String>> dependencies = new LinkedHashMap<>(); for(String id : tables.keySet()) dependencies.put(id, new LinkedHashSet<>());
        for(Table table : document.tables()) for(var fk : table.foreignKeys()) {
            String target = resolve(fk.targetTable(), tables, simple, ambiguous); String source = key(table.qualifiedName());
            if(target != null && !target.equals(source)) dependencies.get(source).add(target);
        }
        List<Table> ordered = new ArrayList<>(); Set<String> emitted = new LinkedHashSet<>();
        boolean progress;
        do {
            progress = false;
            for(var entry : dependencies.entrySet()) if(!emitted.contains(entry.getKey()) && emitted.containsAll(entry.getValue())) {
                emitted.add(entry.getKey()); ordered.add(tables.get(entry.getKey())); progress = true;
            }
        } while(progress);
        List<Diagnostic> diagnostics = new ArrayList<>(); List<SqlDdlPlan.DeferredForeignKey> deferred = new ArrayList<>();
        if(emitted.size() != tables.size()) {
            List<String> cyclic = dependencies.keySet().stream().filter(key -> !emitted.contains(key)).toList();
            diagnostics.add(new Diagnostic("SQL_DDL_REFERENCE_CYCLE", Severity.INFO, 1, 1, "Reference cycle detected across: " + String.join(", ", cyclic) + ". Foreign keys remain explicit and are deferred for projection ordering."));
            for(String key : cyclic) { Table table = tables.get(key); ordered.add(table); for(var fk : table.foreignKeys()) deferred.add(new SqlDdlPlan.DeferredForeignKey(table.qualifiedName(), fk)); }
        }
        return new SqlDdlPlan(ordered, deferred, diagnostics);
    }
    /** Descripción: Obtiene la información correspondiente a «resolve» dentro de SqlDdlPlanner. Entradas: value: valor de entrada que debe procesarse; exact: dato de entrada necesario para la operación; simple: dato de entrada necesario para la operación; ambiguous: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String resolve(String value, Map<String,Table> exact, Map<String,String> simple, Set<String> ambiguous) {
        String exactKey = key(value); if(exact.containsKey(exactKey)) return exactKey; String simpleKey = key(simple(value)); return ambiguous.contains(simpleKey) ? null : simple.get(simpleKey);
    }
    /** Descripción: Ejecuta la operación «simple» correspondiente a la responsabilidad de SqlDdlPlanner. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String simple(String value) { int dot=value.lastIndexOf('.'); return dot<0?value:value.substring(dot+1); }
    /** Descripción: Ejecuta la operación «key» correspondiente a la responsabilidad de SqlDdlPlanner. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String key(String value) { return value==null?"":value.toLowerCase(Locale.ROOT); }
}
