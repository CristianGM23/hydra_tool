package com.example.editor.metamodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MetamodelIdPolicyTests {
    @Test void acceptsThePortableIdentifierContract() {
        assertThat(MetamodelIdPolicy.valid("workflow.v2-main_01")).isTrue();
        assertThat(MetamodelIdPolicy.require("workflow.v2-main_01")).isEqualTo("workflow.v2-main_01");
    }

    @Test void rejectsTraversalWhitespaceAndOversizedIdentifiers() {
        assertThat(MetamodelIdPolicy.valid("../workflow")).isFalse();
        assertThat(MetamodelIdPolicy.valid(" workflow")).isFalse();
        assertThat(MetamodelIdPolicy.valid("a".repeat(121))).isFalse();
        assertThatThrownBy(() -> MetamodelIdPolicy.require("../workflow"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
