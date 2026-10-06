package com.example.editor.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/** Contrato de perspectivas del editor textual: debe reutilizar todo el catálogo de exportación del modelo. */
class TextualModelPerspectiveCatalogTests {
    @Test
    void catalogContainsEveryRequestedModelPerspectiveWithoutMaintainingASecondList() {
        Set<String> ids = ModelCodeGenerationTarget.catalog().stream().map(ModelCodeGenerationTarget::id)
                .collect(Collectors.toSet());

        assertThat(ids).contains("xmi", "emf", "ecore", "java", "python", "drawio", "plantuml");
        assertThat(ids).contains("json", "xml", "langium", "xtext", "antlr", "sql-ddl", "typescript",
                "javascript", "go", "blockly-json", "blockly-javascript", "glsp", "markdown", "markdown-agent");
    }
}
