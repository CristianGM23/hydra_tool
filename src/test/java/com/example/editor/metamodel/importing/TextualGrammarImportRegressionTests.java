package com.example.editor.metamodel.importing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReferenceKind;
import com.example.editor.metamodel.importing.support.GeneratedGrammarImportSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba la reconstrucción de metamodelos desde gramáticas Xtext y Langium reales.
 */
class TextualGrammarImportRegressionTests {

    @Test
    void importsLangiumEntryRuleAsExplicitRoot() throws IOException {
        var parsed = parseResource("/metamodels/systemdsl.langium", "langium");
        var root = parsed.metamodel().classes().stream().filter(item -> item.name().equals("SystemModel")).findFirst().orElseThrow();
        assertThat(root.annotations()).containsEntry("root", "true").containsEntry("entryRule", "true");
        assertThat(parsed.metamodel().annotations()).containsEntry("entryRule", "SystemModel").containsEntry("rootClassId", root.id());
        assertThat(parsed.metamodel().relations().stream().filter(item -> item.sourceTypeId().equals(root.id())).map(item -> item.name())).containsExactlyInAnyOrder("types", "components");
    }

    @Test
    void importsXtextFirstParserRuleAsRoot() throws IOException {
        var parsed = parseResource("/metamodels/systemdsl.xtext", "xtext");
        var root = parsed.metamodel().classes().stream().filter(item -> item.name().equals("SystemModel")).findFirst().orElseThrow();
        assertThat(root.annotations()).containsEntry("root", "true");
        assertThat(parsed.metamodel().relations().stream().filter(item -> item.sourceTypeId().equals(root.id())).map(item -> item.name())).contains("types", "enums", "components");
        assertThat(parsed.warnings()).extracting(item -> item.code()).contains("IMPORT_UNRESOLVED_GRAMMAR_TARGET");
    }

    @Test
    void importsLangiumEnumWithSymbolicNamesAlthoughPropertyNameDiffers() throws IOException {
        var parsed = parseResource("/metamodels/systemdsl.langium", "langium");
        var environment = parsed.metamodel().enums().stream()
                .filter(item -> item.name().equals("Environment"))
                .findFirst()
                .orElseThrow();

        assertThat(environment.literals())
                .extracting(item -> item.name())
                .containsExactly("DEVELOPMENT", "TESTING", "PRODUCTION");

        assertThat(environment.literals())
                .extracting(item -> item.annotations().get("serializedValue"))
                .containsExactly("DEV", "TEST", "PROD");

        assertThat(parsed.metamodel().classes().stream()
                .filter(item -> item.name().equals("Database"))
                .flatMap(item -> item.attributes().stream())
                .filter(item -> item.name().equals("env")))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.type().kind()).isEqualTo(MetaTypeReferenceKind.ENUM);
                    assertThat(item.type().targetId()).isEqualTo(environment.id());
                });
    }

    @Test
    void importsXtextEnumAndOptionalMultiplicity() throws IOException {
        var parsed = parseResource("/metamodels/systemdsl.xtext", "xtext");
        var environment = parsed.metamodel().enums().stream()
                .filter(item -> item.name().equals("Environment"))
                .findFirst()
                .orElseThrow();

        assertThat(environment.literals())
                .extracting(item -> item.name())
                .containsExactly("DEVELOPMENT", "TESTING", "PRODUCTION");

        assertThat(parsed.metamodel().classes().stream()
                .filter(item -> item.name().equals("Service"))
                .flatMap(item -> item.attributes().stream())
                .filter(item -> item.name().equals("env")))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.multiplicity().lowerBound()).isZero();
                    assertThat(item.multiplicity().upperBound()).isEqualTo(1);
                    assertThat(item.type().targetId()).isEqualTo(environment.id());
                });
    }

    @Test
    void importsAbstractionAndInheritanceFromAlternatives() throws IOException {
        var parsed = parseResource("/metamodels/systemdsl.langium", "langium");
        var component = parsed.metamodel().classes().stream().filter(item -> item.name().equals("Component")).findFirst().orElseThrow();
        assertThat(component.kind()).isEqualTo(MetaClassKind.ABSTRACT_CLASS);
        assertThat(parsed.metamodel().relations().stream().filter(item -> item.kind() == MetaRelationKind.INHERITANCE).filter(item -> item.targetTypeId().equals(component.id()))).extracting(item -> item.sourceTypeId()).hasSize(2);
        assertThat(parsed.warnings()).extracting(item -> item.code()).contains("IMPORT_ABSTRACT_RULE_INFERRED");
    }

    @Test
    void preservesCrossReferenceAndUsesNonNameScopeKey() {
        String grammar = """
                grammar scope.Test with org.eclipse.xtext.common.Terminals
                Root: databases+=Database* services+=Service*;
                Database: 'database' identifier=ID;
                Service: 'service' target=[Database|ID];
                """;
        var parsed = GeneratedGrammarImportSupport.parseTextual(grammar, "scope.Test", "", "xtext");
        var relation = parsed.metamodel().relations().stream().filter(item -> item.name().equals("target")).findFirst().orElseThrow();
        assertThat(relation.kind()).isEqualTo(MetaRelationKind.REFERENCE);
        assertThat(relation.annotations()).containsEntry("scopeKeyFeature", "identifier").containsEntry("referenceTerminal", "ID");
        assertThat(parsed.warnings()).extracting(item -> item.code()).contains("IMPORT_INFO_CROSS_REFERENCE_DETECTED", "IMPORT_SCOPE_EXTERNAL_CONFIGURATION");
    }

    @Test
    void warnsWhenCrossReferenceScopeKeyCannotBeInferred() {
        String grammar = """
                grammar ScopeMissing
                entry Root: services+=Service* targets+=Target*;
                Target: 'target';
                Service: 'service' target=[Target:ID];
                """;
        var parsed = GeneratedGrammarImportSupport.parseTextual(grammar, "ScopeMissing", "", "langium");
        assertThat(parsed.warnings()).extracting(item -> item.code()).contains("IMPORT_SCOPE_KEY_NOT_FOUND");
    }

    @Test
    void detectsAndAutoResolvesDirectLeftRecursionForMetamodelDerivation() {
        String grammar = """
                grammar Expressions
                entry Model: expressions+=Expression*;
                Expression: Expression '+' right=INT | value=INT;
                """;
        var parsed = GeneratedGrammarImportSupport.parseTextual(grammar, "Expressions", "", "langium");
        assertThat(parsed.warnings()).extracting(item -> item.code()).contains("IMPORT_LEFT_RECURSION_AUTO_RESOLVED");
        assertThat(parsed.metamodel().relations()).noneMatch(item -> item.sourceTypeId().equals(item.targetTypeId()) && item.kind() == MetaRelationKind.CONTAINMENT);
    }

    @Test
    void detectsAndAutoResolvesIndirectLeftRecursionForMetamodelDerivation() {
        String grammar = """
                grammar Indirect
                entry Model: values+=A*;
                A: B value=INT;
                B: A other=INT;
                """;
        var parsed = GeneratedGrammarImportSupport.parseTextual(grammar, "Indirect", "", "langium");
        assertThat(parsed.warnings()).extracting(item -> item.code()).contains("IMPORT_LEFT_RECURSION_AUTO_RESOLVED");
    }

    @Test
    void reportsAndRepairsEnumLiteralAndDeclarationConflicts() {
        String grammar = """
                grammar Conflict
                entry Root: first=State second=State;
                enum State {
                    1BAD = 'X',
                    GOOD = 'X',
                    GOOD = ''
                }
                State: 'state' name=ID;
                """;
        var parsed = GeneratedGrammarImportSupport.parseTextual(grammar, "Conflict", "", "langium");
        var enumeration = parsed.metamodel().enums().stream().filter(item -> item.name().equals("State")).findFirst().orElseThrow();
        assertThat(enumeration.literals()).extracting(item -> item.name()).containsExactly("_1BAD", "GOOD", "GOOD_2");
        assertThat(parsed.warnings()).extracting(item -> item.code()).contains("IMPORT_RULE_NAME_CONFLICT_IGNORED", "IMPORT_ENUM_LITERAL_INVALID_AUTO_FIXED", "IMPORT_ENUM_LITERAL_VALUE_DUPLICATE", "IMPORT_ENUM_LITERAL_NAME_DUPLICATE_AUTO_FIXED", "IMPORT_ENUM_LITERAL_VALUE_INVALID_AUTO_FIXED");
    }

    private GeneratedGrammarImportSupport.ParsedGrammar parseResource(String path, String sourceFormat) throws IOException {
        try(var input = getClass().getResourceAsStream(path)) {
            if(input == null) {
                throw new IOException("Missing test resource: " + path);
            }

            return GeneratedGrammarImportSupport.parseTextual(new String(input.readAllBytes(), StandardCharsets.UTF_8), "SystemDSL", "", sourceFormat);
        }
    }
}
