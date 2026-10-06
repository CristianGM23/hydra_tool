package com.example.editor.codegen;

import static org.assertj.core.api.Assertions.assertThat;
import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;

/** Protege el contrato de construcción de los renderers para que Spring pueda descubrirlos sin requerir un constructor vacío. */
class ModelCodeGenerationSpringConstructorContractTests {
    /** Descripción: Comprueba una única frontera de construcción por renderer. Entradas: clases de renderers. Salida: contrato compatible con autowiring por constructor. */
    @Test
    void renderersExposeOneInjectableConstructorEach() {
        assertSingleConstructor(ProjectionModelCodeGenerationRenderer.class);
        assertSingleConstructor(SourceModelCodeGenerationRenderer.class);
    }

    /** Descripción: Valida que una clase de renderer exponga exactamente un constructor. Entradas: clase. Salida: aserción estructural. */
    private static void assertSingleConstructor(Class<?> type) {
        Constructor<?>[] constructors = type.getDeclaredConstructors();
        assertThat(constructors).hasSize(1);
        assertThat(constructors[0].getParameterTypes()).containsExactly(tools.jackson.databind.json.JsonMapper.class);
    }
}
