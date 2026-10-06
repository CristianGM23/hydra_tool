package com.example.editor.codegen;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.editor.metamodel.importing.sql.SqlDdlImportPipeline;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;

class SqlDataModelCodeGenerationRendererTests {
    @Test
    void sqlTargetGeneratesExecutableSchemaConstraintsAndInstanceData() {
        var metamodel = new SqlDdlImportPipeline().importDdl("CREATE TABLE users(id BIGINT PRIMARY KEY, name VARCHAR(80) NOT NULL);", "users.sql", "users-schema").metamodel();
        var userClass = metamodel.classes().get(0);
        var model = new CanonicalModelDocument(null, "model1", "users-schema", "", "UsersData", List.of("user1"),
                List.of(new CanonicalModelElement("user1", userClass.id(), Map.of("id", 7L, "name", "O'Reilly"), Map.of())), List.of(), Map.of());
        var renderer = new SqlDataModelCodeGenerationRenderer();
        var artifact = renderer.generate("sql-ddl", ModelCodeGenerationTarget.require("sql-ddl"), metamodel, model, ModelCodeGenerationOptions.defaults());
        String sql = new String(artifact.content(), StandardCharsets.UTF_8);
        assertThat(artifact.fileName()).endsWith(".sql");
        assertThat(sql).contains("INSERT INTO \"users\"").contains("'O''Reilly'");
        assertThat(sql).contains("CREATE TABLE \"users\"");
        assertThat(sql).contains("PRIMARY KEY (\"id\")");
        assertThat(sql.indexOf("CREATE TABLE")).isLessThan(sql.indexOf("INSERT INTO"));
    }

    @Test
    void sqlTargetHasNoUnsupportedSourceScaffoldingCapabilities() {
        var target = ModelCodeGenerationTarget.require("sql-ddl");
        assertThat(target.outputKind()).isEqualTo("DATABASE_DATA_PROJECTION");
        assertThat(target.supportedFeatures()).isEmpty();
        assertThat(new SqlDataModelCodeGenerationRenderer().supportedFeatures("sql-ddl")).isEmpty();
    }
}
