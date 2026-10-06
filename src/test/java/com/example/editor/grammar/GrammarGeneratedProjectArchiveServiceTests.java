package com.example.editor.grammar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;

import com.example.editor.grammar.application.GrammarGeneratedProjectArchiveService;
import com.example.editor.grammar.core.GeneratedGrammarProject;

class GrammarGeneratedProjectArchiveServiceTests {
    @Test
    void packagesProviderProjectDeterministicallyInPortablePathOrder() throws Exception {
        GrammarProperties properties = new GrammarProperties();
        GrammarGeneratedProjectArchiveService service = new GrammarGeneratedProjectArchiveService(properties);
        GeneratedGrammarProject project = new GeneratedGrammarProject("people-langium", Map.of(
                "src/generated/module.ts", "export const generated = true;\n",
                "package.json", "{\"name\":\"people\"}\n",
                "src/language-server/main.ts", "export {};\n"), Map.of("provider", "LANGIUM"));

        byte[] first = service.archive(project);
        byte[] second = service.archive(project);

        assertThat(second).isEqualTo(first);
        List<String> entries = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(first))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) entries.add(entry.getName());
        }
        assertThat(entries).containsExactly("package.json", "src/generated/module.ts", "src/language-server/main.ts");
    }

    @Test
    void rejectsEmptyGeneratedProjectInsteadOfReturningAFalseDownload() {
        GrammarGeneratedProjectArchiveService service = new GrammarGeneratedProjectArchiveService(new GrammarProperties());
        GeneratedGrammarProject project = new GeneratedGrammarProject("empty", Map.of(), Map.of());
        assertThatThrownBy(() -> service.archive(project))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not contain a generated project");
    }
}
