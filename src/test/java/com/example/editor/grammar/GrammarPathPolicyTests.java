package com.example.editor.grammar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import com.example.editor.grammar.core.GrammarPathPolicy;

class GrammarPathPolicyTests {
    @Test void rejectsWindowsDevicesTrailingDotsAndTrailingSpaces() {
        for (String value : new String[]{"generated/CON", "generated/com1.txt", "generated/folder.", "generated/folder ", "generated/NUL.json"}) {
            assertThatThrownBy(() -> GrammarPathPolicy.requirePortableRelative(value, "path"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void normalizesPortableSeparatorsWithoutHidingUnsafeSegments() {
        assertThat(GrammarPathPolicy.requirePortableRelative("tools\\generated\\emf", "path")).isEqualTo("tools/generated/emf");
        assertThatThrownBy(() -> GrammarPathPolicy.requirePortableRelative("generated/./emf", "path"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
