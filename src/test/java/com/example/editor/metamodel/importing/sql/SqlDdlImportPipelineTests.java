package com.example.editor.metamodel.importing.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.example.editor.metamodel.core.MetaTypeReferenceKind;

class SqlDdlImportPipelineTests {
    @Test
    void importsSchemaThroughDedicatedAstIntoCanonicalMetaModel() {
        var result = new SqlDdlImportPipeline().importDdl("""
                CREATE TYPE public.status AS ENUM ('NEW','DONE');
                CREATE TABLE public.users (id BIGSERIAL PRIMARY KEY, name VARCHAR(80) NOT NULL UNIQUE, status public.status DEFAULT 'NEW');
                CREATE TABLE public.tasks (id BIGINT PRIMARY KEY, owner_id BIGINT NOT NULL,
                  CONSTRAINT fk_owner FOREIGN KEY(owner_id) REFERENCES public.users(id) ON DELETE CASCADE);
                """, "schema.sql", "schema");
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.metamodel().classes()).hasSize(2);
        assertThat(result.metamodel().enums()).hasSize(1);
        assertThat(result.metamodel().relations()).hasSize(1);
        assertThat(result.metamodel().annotations()).containsEntry("sourceFormat", "sql-ddl");
        var users = result.metamodel().classes().stream().filter(value -> value.name().equals("Users")).findFirst().orElseThrow();
        assertThat(users.attributes().stream().filter(value -> value.name().equals("status")).findFirst().orElseThrow().type().kind()).isEqualTo(MetaTypeReferenceKind.ENUM);
    }

    @Test
    void plansReferenceCyclesWithoutExecutingOrDroppingRelations() {
        var result = new SqlDdlImportPipeline().importDdl("""
                CREATE TABLE a (id BIGINT PRIMARY KEY, b_id BIGINT, FOREIGN KEY(b_id) REFERENCES b(id));
                CREATE TABLE b (id BIGINT PRIMARY KEY, a_id BIGINT, FOREIGN KEY(a_id) REFERENCES a(id));
                """, "cycle.sql", "cycle");
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.plan().deferredForeignKeys()).hasSize(2);
        assertThat(result.diagnostics()).anySatisfy(value -> assertThat(value.code()).isEqualTo("SQL_DDL_REFERENCE_CYCLE"));
        assertThat(result.metamodel().relations()).hasSize(2);
    }

    @Test
    void rejectsAmbiguousUnqualifiedForeignKeyTargetsBeforeMapping() {
        var result = new SqlDdlImportPipeline().importDdl("""
                CREATE TABLE one.users (id BIGINT PRIMARY KEY);
                CREATE TABLE two.users (id BIGINT PRIMARY KEY);
                CREATE TABLE tasks (id BIGINT PRIMARY KEY, owner_id BIGINT, FOREIGN KEY(owner_id) REFERENCES users(id));
                """, "ambiguous.sql", "ambiguous");
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.metamodel()).isNull();
        assertThat(result.diagnostics()).anySatisfy(value -> assertThat(value.code()).isEqualTo("SQL_DDL_AMBIGUOUS_FOREIGN_KEY_TARGET"));
    }

    @Test
    void reportsUnknownExternalForeignKeysAsLossyInsteadOfDroppingSilently() {
        var result = new SqlDdlImportPipeline().importDdl("""
                CREATE TABLE tasks (id BIGINT PRIMARY KEY, owner_id BIGINT, FOREIGN KEY(owner_id) REFERENCES external_users(id));
                """, "external.sql", "external");
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.diagnostics()).anySatisfy(value -> {
            assertThat(value.severity()).isEqualTo(SqlDdlAst.Severity.WARNING);
            assertThat(value.code()).isEqualTo("SQL_DDL_UNRESOLVED_FOREIGN_KEY_TARGET");
        });
        assertThat(result.metamodel().relations()).isEmpty();
    }

    @Test
    void rejectsDuplicateTablesAndColumnsAtomically() {
        var duplicateTable = new SqlDdlImportPipeline().importDdl("CREATE TABLE x(id INT); CREATE TABLE x(id INT);", "duplicate.sql", "duplicate");
        assertThat(duplicateTable.hasErrors()).isTrue();
        assertThat(duplicateTable.metamodel()).isNull();
        var duplicateColumn = new SqlDdlImportPipeline().importDdl("CREATE TABLE x(id INT, id BIGINT);", "duplicate-column.sql", "duplicate-column");
        assertThat(duplicateColumn.hasErrors()).isTrue();
        assertThat(duplicateColumn.metamodel()).isNull();
    }

    @Test
    void enforcesResourceLimitsBeforeCanonicalMutation() {
        var limits = new SqlDdlLimits(100, 20, 2, 2, 2, 2, 20, 3);
        assertThatThrownBy(() -> new SqlDdlImportPipeline(limits, new SqlDdlDialectRegistry()).importDdl("CREATE TABLE this_is_a_long_input(id INTEGER, name VARCHAR(20), other VARCHAR(20));", "large.sql", "large"))
                .isInstanceOf(SqlDdlSafetyException.class);
    }

    @Test
    void rejectsMalformedQuotedInputWithLocation() {
        assertThatThrownBy(() -> new SqlDdlParser("CREATE TABLE x(name VARCHAR(20) DEFAULT 'oops);").parse())
                .isInstanceOf(SqlDdlLexicalException.class)
                .satisfies(error -> {
                    var lexical = (SqlDdlLexicalException) error;
                    assertThat(lexical.line()).isEqualTo(1);
                    assertThat(lexical.column()).isGreaterThan(1);
                });
    }

    @Test
    void infersMajorDialectFamiliesWithoutChangingCanonicalCore() {
        assertThat(SqlDdlDialect.infer("CREATE TABLE x(id SERIAL); CREATE TYPE s AS ENUM ('A');")).isEqualTo(SqlDdlDialect.POSTGRESQL);
        assertThat(SqlDdlDialect.infer("CREATE TABLE x(id INT AUTO_INCREMENT PRIMARY KEY);" )).isEqualTo(SqlDdlDialect.MYSQL);
        assertThat(SqlDdlDialect.infer("CREATE TABLE [dbo].[x] (id INT IDENTITY(1,1));")).isEqualTo(SqlDdlDialect.SQL_SERVER);
        assertThat(new SqlDdlDialectRegistry().require(SqlDdlDialect.POSTGRESQL).capabilities()).contains(SqlDdlCapability.CREATE_TYPE_ENUM);
    }
}
