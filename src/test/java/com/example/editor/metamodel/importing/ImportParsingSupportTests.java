package com.example.editor.metamodel.importing;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaTypeReferenceKind;
import com.example.editor.metamodel.importing.support.ImportParsingSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba conversiones comunes.
 */
class ImportParsingSupportTests {

    @Test
    void removesCompoundExtensions() {
        assertThat(ImportParsingSupport.fileBaseName("company.metamodel.json")).isEqualTo("company");
        assertThat(ImportParsingSupport.fileBaseName("company.agent.md")).isEqualTo("company");
    }

    @Test
    void createsStableIdentifiers() {
        assertThat(ImportParsingSupport.safeId("Company Model", "model")).isEqualTo("company-model");
        assertThat(ImportParsingSupport.safeId("2026", "model")).isEqualTo("m-2026");
    }

    @Test
    void parsesMultiplicities() {
        assertThat(ImportParsingSupport.multiplicity("0..*", null).upperBound()).isNull();
        assertThat(ImportParsingSupport.multiplicity("1", null).lowerBound()).isEqualTo(1);
        assertThat(ImportParsingSupport.multiplicity("1..5", null).upperBound()).isEqualTo(5);
    }

    @Test
    void mapsEcorePrimitiveNames() {
        var type = ImportParsingSupport.typeReference("EString");
        assertThat(type.kind()).isEqualTo(MetaTypeReferenceKind.PRIMITIVE);
        assertThat(type.targetId()).isEqualTo(MetaPrimitiveType.STRING.name());
    }

    @Test
    void preservesUnknownTypesAsExternal() {
        var type = ImportParsingSupport.typeReference("com.example.Money");
        assertThat(type.kind()).isEqualTo(MetaTypeReferenceKind.EXTERNAL);
        assertThat(type.externalName()).isEqualTo("com.example.Money");
    }
}
