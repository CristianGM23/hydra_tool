package com.example.editor.metamodel.adapter.ecore;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaPrimitiveType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba el registro configurable de tipos primitivos.
 */
class EcorePrimitiveTypeRegistryTests {

    /**
     * Descripción:
     * Comprueba los mapeos predeterminados.
     */
    @Test
    void resolvesDefaultPrimitiveMappings() {
        EcoreAdapterProperties properties = new EcoreAdapterProperties();
        EcorePrimitiveTypeRegistry registry = new EcorePrimitiveTypeRegistry(properties);

        assertThat(registry.toEcore(MetaPrimitiveType.STRING).getName()).isEqualTo("EString");
        assertThat(registry.fromEcore(registry.toEcore(MetaPrimitiveType.INTEGER))).isEqualTo(MetaPrimitiveType.INTEGER);
    }

    /**
     * Descripción:
     * Comprueba una sustitución configurada.
     */
    @Test
    void appliesConfiguredPrimitiveOverride() {
        EcoreAdapterProperties properties = new EcoreAdapterProperties();
        properties.getPrimitiveMappings().put("INTEGER", "EIntegerObject");
        EcorePrimitiveTypeRegistry registry = new EcorePrimitiveTypeRegistry(properties);

        assertThat(registry.toEcore(MetaPrimitiveType.INTEGER).getName()).isEqualTo("EIntegerObject");
        assertThat(registry.fromEcore(registry.toEcore(MetaPrimitiveType.INTEGER))).isEqualTo(MetaPrimitiveType.INTEGER);
    }
}
