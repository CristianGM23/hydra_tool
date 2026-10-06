package com.example.editor.architecture;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Evita configuraciones ambiguas por claves repetidas en application.properties. */
class ApplicationPropertiesUniquenessTests {

    @Test
    void applicationPropertiesDoesNotDeclareTheSameKeyTwice() throws Exception {
        List<String> lines = Files.readAllLines(Path.of("src/main/resources/application.properties"));
        Map<String, Integer> firstLine = new LinkedHashMap<>();
        List<String> duplicates = new ArrayList<>();
        for(int index = 0; index < lines.size(); index++) {
            String line = lines.get(index).trim();
            if(line.isBlank() || line.startsWith("#") || !line.contains("=")) continue;
            String key = line.substring(0, line.indexOf('=')).trim();
            Integer previous = firstLine.putIfAbsent(key, index + 1);
            if(previous != null) duplicates.add(key + " at lines " + previous + " and " + (index + 1));
        }
        assertThat(duplicates).as("duplicate configuration keys make effective settings order-dependent").isEmpty();
    }
}
