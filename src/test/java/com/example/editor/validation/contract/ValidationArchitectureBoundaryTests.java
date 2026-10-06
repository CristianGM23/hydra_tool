package com.example.editor.validation.contract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationArchitectureBoundaryTests {

    private static final List<String> FORBIDDEN_IMPORTS = List.of(
        "org.eclipse.emf",
        "com.example.editor.emf",
        "com.example.editor.metamodel.adapter.ecore",
        "com.example.editor.metamodel.projection"
    );

    @Test
    void canonicalValidationSubsystemDoesNotDependOnExternalTechnologyAdapters() throws IOException {
        Path packageDirectory = Path.of("src/main/java/com/example/editor/validation");
        assertThat(packageDirectory).isDirectory();

        try(var files = Files.walk(packageDirectory)) {
            List<Path> javaFiles = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .toList();
            assertThat(javaFiles).isNotEmpty();

            for(Path javaFile : javaFiles) {
                String source = Files.readString(javaFile);
                for(String forbiddenImport : FORBIDDEN_IMPORTS) {
                    assertThat(source)
                        .as("%s must not depend on %s", javaFile, forbiddenImport)
                        .doesNotContain("import " + forbiddenImport);
                }
            }
        }
    }
}
