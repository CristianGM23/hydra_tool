package com.example.editor.metamodel.export;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.example.editor.metamodel.importing.sql.SqlDdlImportPipeline;

class SqlDdlMetamodelExporterTests {
    @Test
    void exportsCanonicalMetaModelBackToPortableDdlAndDefersForeignKeys() {
        var imported = new SqlDdlImportPipeline().importDdl("""
                CREATE TABLE users (id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL);
                CREATE TABLE tasks (id BIGINT PRIMARY KEY, owner_id BIGINT NOT NULL,
                  CONSTRAINT fk_owner FOREIGN KEY(owner_id) REFERENCES users(id) ON DELETE CASCADE);
                """, "schema.sql", "schema");
        var context = new DefaultNeutralMetamodelExportContext(imported.metamodel(), null, null, List.of());
        String ddl = new SqlDdlMetamodelExporter().export(context);
        assertThat(ddl).contains("CREATE TABLE \"users\"").contains("CREATE TABLE \"tasks\"");
        assertThat(ddl.indexOf("ALTER TABLE \"tasks\"")).isGreaterThan(ddl.indexOf("CREATE TABLE \"tasks\""));
        assertThat(ddl).contains("ADD CONSTRAINT \"fk_owner\" FOREIGN KEY (\"owner_id\") REFERENCES \"users\" (\"id\") ON DELETE CASCADE");
    }

    @Test
    void roundTripKeepsTablesColumnsEnumsAndReferencesInNeutralHub() {
        var pipeline = new SqlDdlImportPipeline();
        var first = pipeline.importDdl("""
                CREATE TYPE state AS ENUM ('OPEN','CLOSED');
                CREATE TABLE projects (id BIGINT PRIMARY KEY, state state NOT NULL);
                CREATE TABLE issues (id BIGINT PRIMARY KEY, project_id BIGINT, FOREIGN KEY(project_id) REFERENCES projects(id));
                """, "roundtrip.sql", "roundtrip");
        String ddl = new SqlDdlMetamodelExporter().export(new DefaultNeutralMetamodelExportContext(first.metamodel(), null, null, List.of()));
        var second = pipeline.importDdl(ddl, "roundtrip-export.sql", "roundtrip-export");
        assertThat(second.hasErrors()).isFalse();
        assertThat(second.metamodel().classes()).hasSameSizeAs(first.metamodel().classes());
        assertThat(second.metamodel().enums()).hasSameSizeAs(first.metamodel().enums());
        assertThat(second.metamodel().relations()).hasSameSizeAs(first.metamodel().relations());
    }
}
