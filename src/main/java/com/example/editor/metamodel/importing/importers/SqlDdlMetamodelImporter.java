package com.example.editor.metamodel.importing.importers;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import com.example.editor.metamodel.core.TransformationWarning;
import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;
import com.example.editor.metamodel.importing.MetamodelImportFormat;
import com.example.editor.metamodel.importing.MetamodelImportOptions;
import com.example.editor.metamodel.importing.MetamodelImportOutput;
import com.example.editor.metamodel.importing.MetamodelImportSource;
import com.example.editor.metamodel.importing.MetamodelImporter;
import com.example.editor.metamodel.importing.sql.SqlDdlAst;
import com.example.editor.metamodel.importing.sql.SqlDdlImportPipeline;
import com.example.editor.metamodel.importing.sql.SqlDdlLexicalException;
import com.example.editor.metamodel.importing.sql.SqlDdlSafetyException;

/** Imports relational SQL DDL through a dedicated AST and then maps it to HYDRA's canonical MetaModel. */
@Component
public class SqlDdlMetamodelImporter implements MetamodelImporter {
    private final SqlDdlImportPipeline pipeline = new SqlDdlImportPipeline();
    /** Descripción: Ejecuta la operación «format» correspondiente a la responsabilidad de SqlDdlMetamodelImporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public MetamodelImportFormat format() { return MetamodelImportFormat.SQL_DDL; }

    /** Descripción: Ejecuta la operación «importMetamodel» correspondiente a la responsabilidad de SqlDdlMetamodelImporter. Entradas: source: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ @Override public MetamodelImportOutput importMetamodel(MetamodelImportSource source,MetamodelImportOptions options) {
        String text=new String(source.content(),StandardCharsets.UTF_8);
        final SqlDdlImportPipeline.Result result;
        try {
            result=pipeline.importDdl(text,source.fileName(),options.targetId());
        } catch(SqlDdlSafetyException exception) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST,"SQL DDL import was stopped before persistence because a safety limit was exceeded: " + exception.getMessage());
        } catch(SqlDdlLexicalException exception) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST,"SQL DDL could not be read at line " + exception.line() + ", column " + exception.column() + ": " + exception.getMessage());
        }
        List<SqlDdlAst.Diagnostic> errors=result.diagnostics().stream().filter(item->item.severity()==SqlDdlAst.Severity.ERROR).toList();
        if(!errors.isEmpty()) {
            SqlDdlAst.Diagnostic first=errors.get(0);
            throw new MetamodelDesignException(Reason.INVALID_REQUEST,"SQL DDL could not be imported at line " + first.line() + ", column " + first.column() + ": " + first.message());
        }
        List<TransformationWarning> warnings=new ArrayList<>();
        for(SqlDdlAst.Diagnostic item:result.diagnostics()) {
            String prefix=item.severity()==SqlDdlAst.Severity.INFO?"IMPORT_INFO_SQL_DDL_":"IMPORT_LOSSY_SQL_DDL_";
            warnings.add(new TransformationWarning(prefix + item.code(),"line:"+item.line()+":column:"+item.column(),item.message()));
        }
        return new MetamodelImportOutput(result.metamodel(),null,warnings);
    }
}
