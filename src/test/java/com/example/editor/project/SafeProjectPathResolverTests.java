package com.example.editor.project;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.project.storage.SafeProjectPathResolver;

class SafeProjectPathResolverTests {
    @TempDir Path temporary;

    @Test void resolvesSafeRelativePath() {
        SafeProjectPathResolver resolver=new SafeProjectPathResolver(new LocalProjectProperties());
        assertThat(resolver.resolve(temporary,"metamodels/people.json"))
                .isEqualTo(temporary.resolve("metamodels/people.json"));
    }

    @Test void normalizesWindowsSeparators() {
        SafeProjectPathResolver resolver=new SafeProjectPathResolver(new LocalProjectProperties());
        assertThat(resolver.resolve(temporary,"docs\\readme.md")).isEqualTo(temporary.resolve("docs/readme.md"));
    }

    @Test void rejectsParentTraversal() {
        SafeProjectPathResolver resolver=new SafeProjectPathResolver(new LocalProjectProperties());
        assertThatThrownBy(() -> resolver.resolve(temporary,"../outside.json")).isInstanceOf(ProjectException.class);
    }

    @Test void rejectsUnixAndWindowsAbsolutePaths() {
        SafeProjectPathResolver resolver=new SafeProjectPathResolver(new LocalProjectProperties());
        assertThatThrownBy(() -> resolver.resolve(temporary,"/outside.json")).isInstanceOf(ProjectException.class);
        assertThatThrownBy(() -> resolver.resolve(temporary,"C:/outside.json")).isInstanceOf(ProjectException.class);
    }

    @Test void convertsOnlyPathsInsideTheProjectToRelativePaths() {
        SafeProjectPathResolver resolver=new SafeProjectPathResolver(new LocalProjectProperties());
        assertThat(resolver.relative(temporary,temporary.resolve("docs/readme.md"))).isEqualTo("docs/readme.md");
        assertThatThrownBy(() -> resolver.relative(temporary,temporary.getParent().resolve("outside.md"))).isInstanceOf(ProjectException.class);
    }

    @Test void resolvesMissingNestedPathWithoutRequiringTheFileToExist() {
        SafeProjectPathResolver resolver=new SafeProjectPathResolver(new LocalProjectProperties());
        Path expected=temporary.resolve("editors/representations/structural.editor.json");
        assertThat(resolver.resolve(temporary,"editors/representations/structural.editor.json")).isEqualTo(expected);
        assertThat(expected).doesNotExist();
    }

    @Test void resolvesTheProjectRootForAnEmptyRelativePath() {
        SafeProjectPathResolver resolver=new SafeProjectPathResolver(new LocalProjectProperties());
        assertThat(resolver.resolve(temporary,"")).isEqualTo(temporary.toAbsolutePath().normalize());
    }

}
