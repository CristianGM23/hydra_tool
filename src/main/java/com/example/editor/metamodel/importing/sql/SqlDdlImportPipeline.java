package com.example.editor.metamodel.importing.sql;

import java.util.ArrayList;
import java.util.List;
import com.example.editor.metamodel.core.MetaModel;

/** Complete SQL DDL adapter pipeline. MetaModel is always the canonical hand-off to the rest of HYDRA. */
public final class SqlDdlImportPipeline {
    private final SqlDdlLimits limits;
    private final SqlDdlValidator validator;
    private final SqlDdlPlanner planner;
    private final SqlDdlMetamodelMapper mapper;
    /** Descripción: Inicializa una instancia de SqlDdlImportPipeline con sus dependencias y datos de entrada. Entradas: limits: dato de entrada necesario para la operación; dialects: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public SqlDdlImportPipeline(SqlDdlLimits limits, SqlDdlDialectRegistry dialects) {
        this.limits = limits == null ? SqlDdlLimits.defaults() : limits;
        this.validator = new SqlDdlValidator(this.limits, dialects);
        this.planner = new SqlDdlPlanner();
        this.mapper = new SqlDdlMetamodelMapper();
    }
    /** Descripción: Inicializa una instancia de SqlDdlImportPipeline con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ public SqlDdlImportPipeline() { this(SqlDdlLimits.defaults(), new SqlDdlDialectRegistry()); }
    /** Descripción: Ejecuta la operación «importDdl» correspondiente a la responsabilidad de Result. Entradas: source: dato de entrada necesario para la operación; sourceName: dato de entrada necesario para la operación; requestedId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Result importDdl(String source, String sourceName, String requestedId) {
        SqlDdlAst.Document parsed = new SqlDdlParser(source, limits).parse();
        List<SqlDdlAst.Diagnostic> diagnostics = new ArrayList<>(parsed.diagnostics());
        SqlDdlValidationResult validated = validator.validate(parsed); diagnostics.addAll(validated.diagnostics());
        if(hasErrors(diagnostics)) return new Result(parsed, null, null, diagnostics);
        SqlDdlPlan plan = planner.plan(parsed); diagnostics.addAll(plan.diagnostics());
        SqlDdlAst.Document planned = new SqlDdlAst.Document(parsed.dialect(), plan.orderedTables(), parsed.enums(), diagnostics);
        MetaModel neutral = mapper.map(planned, sourceName, requestedId);
        return new Result(planned, plan, neutral, diagnostics);
    }
    /** Descripción: Evalúa la condición «hasErrors» sin modificar el estado externo. Entradas: diagnostics: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean hasErrors(List<SqlDdlAst.Diagnostic> diagnostics) { return diagnostics.stream().anyMatch(item -> item.severity() == SqlDdlAst.Severity.ERROR); }
    public record Result(SqlDdlAst.Document document, SqlDdlPlan plan, MetaModel metamodel, List<SqlDdlAst.Diagnostic> diagnostics) {
        public Result { diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics); }
        /** Descripción: Evalúa la condición «hasErrors» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean hasErrors() { return diagnostics.stream().anyMatch(item -> item.severity() == SqlDdlAst.Severity.ERROR); }
    }
}
