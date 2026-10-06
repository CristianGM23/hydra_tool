package com.example.editor.metamodel.importing;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.importing.support.SecureXmlSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba que los analizadores XML no permiten entidades externas.
 */
class SecureXmlSupportTests {

    /**
     * Descripción:
     * Lee un XML normal con espacios de nombres.
     */
    @Test
    void parsesSafeXml() {
        var document = SecureXmlSupport.parse("<metamodel id=\"sample\"/>".getBytes(StandardCharsets.UTF_8), "safe XML");

        assertThat(document.getDocumentElement().getAttribute("id")).isEqualTo("sample");
    }

    /**
     * Descripción:
     * Rechaza documentos con una declaración DOCTYPE.
     */
    @Test
    void rejectsDoctypeDeclarations() {
        String xml = "<!DOCTYPE metamodel [<!ENTITY value SYSTEM \"file:///tmp/secret\">]><metamodel>&value;</metamodel>";

        assertThatThrownBy(() -> SecureXmlSupport.parse(xml.getBytes(StandardCharsets.UTF_8), "unsafe XML")).isInstanceOf(MetamodelDesignException.class).hasMessageContaining("Could not parse unsafe XML");
    }
}
