package com.example.editor.architecture;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Impone límites homogéneos de arquitectura sin excepciones por fichero. */
class StructuralArchitectureGuardTests {

    @Test
    void productionSourceFilesRespectUniformArchitectureLimits() throws Exception {
        Properties baseline = baseline();
        assertThat(baseline.stringPropertyNames()).noneMatch(key -> key.startsWith("exception."));
        List<String> violations = new ArrayList<>();
        inspect(Path.of("src/main/java"), List.of(".java"), integer(baseline, "default.java"), true, violations);
        inspect(Path.of("frontend/src"), List.of(".ts", ".tsx"), integer(baseline, "default.typescript"), true, violations);
        inspect(Path.of("frontend/src"), List.of(".css"), integer(baseline, "default.css"), false, violations);
        assertThat(violations).as("production source files must respect the shared architecture limits").isEmpty();
    }

    @Test
    void productionSourceFilesDoNotHideComplexityInGiantLines() throws Exception {
        Properties logical = logicalBaseline();
        assertThat(logical.stringPropertyNames()).noneMatch(key -> key.startsWith("exception."));
        List<String> violations = new ArrayList<>();
        inspectLogicalSize(Path.of("src/main/java"), List.of(".java"),
                integer(logical, "default.java.maxLine"), integer(logical, "default.java.characters"), violations);
        inspectLogicalSize(Path.of("frontend/src"), List.of(".ts", ".tsx"),
                integer(logical, "default.typescript.maxLine"), integer(logical, "default.typescript.characters"), violations);
        inspectLogicalSize(Path.of("frontend/src"), List.of(".css"),
                integer(logical, "default.css.maxLine"), integer(logical, "default.css.characters"), violations);
        assertThat(violations).as("production sources must respect shared logical-size budgets").isEmpty();
    }

    private void inspect(Path root, List<String> extensions, int limit, boolean logicalCode,
            List<String> violations) throws IOException {
        if(!Files.isDirectory(root)) return;
        try(var paths = Files.walk(root)) {
            for(Path path : paths.filter(Files::isRegularFile)
                    .filter(value -> extensions.stream().anyMatch(ext -> value.toString().endsWith(ext))).toList()) {
                String normalized = path.toString().replace('\\', '/');
                if(normalized.contains(".test.")) continue;
                long lines = logicalCode ? logicalCodeLines(Files.readString(path)) : Files.lines(path).count();
                if(lines > limit) violations.add(normalized + " has " + lines + " logical lines; ceiling is " + limit);
            }
        }
    }

    private long logicalCodeLines(String source) {
        String withoutBlocks = source.replaceAll("(?s)/\\*.*?\\*/", "");
        return withoutBlocks.lines().map(String::trim).filter(line -> !line.isEmpty() && !line.startsWith("//")).count();
    }

    private void inspectLogicalSize(Path root, List<String> extensions, int maximumLineLength,
            int maximumCharacters, List<String> violations) throws IOException {
        if(!Files.isDirectory(root)) return;
        try(var paths = Files.walk(root)) {
            for(Path path : paths.filter(Files::isRegularFile)
                    .filter(value -> extensions.stream().anyMatch(ext -> value.toString().endsWith(ext))).toList()) {
                String normalized = path.toString().replace('\\', '/');
                if(normalized.contains(".test.")) continue;
                String content = Files.readString(path);
                if(content.length() > maximumCharacters) violations.add(normalized + " has " + content.length() + " characters; ceiling is " + maximumCharacters);
                int longest = content.lines().mapToInt(String::length).max().orElse(0);
                if(longest > maximumLineLength) violations.add(normalized + " has a line with " + longest + " characters; ceiling is " + maximumLineLength);
            }
        }
    }

    private Properties logicalBaseline() throws IOException {
        return load(Path.of("config/hydra-logical-size-baseline.properties"));
    }

    private Properties baseline() throws IOException {
        return load(Path.of("config/hydra-architecture-baseline.properties"));
    }

    private Properties load(Path path) throws IOException {
        Properties properties = new Properties();
        try(InputStream input = Files.newInputStream(path)) { properties.load(input); }
        return properties;
    }

    private int integer(Properties properties, String key) {
        return Integer.parseInt(properties.getProperty(key));
    }
}
