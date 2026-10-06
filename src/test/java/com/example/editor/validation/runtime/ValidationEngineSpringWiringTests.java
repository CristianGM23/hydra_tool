package com.example.editor.validation.runtime;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.editor.metamodel.behavior.OperationQueryRuntime;
import com.example.editor.semantic.expression.ExpressionEvaluator;
import static org.assertj.core.api.Assertions.assertThat;

/** Impide que el constructor de reloj de pruebas vuelva a volver ambiguo el bean de Spring. */
class ValidationEngineSpringWiringTests {

    @Test
    void explicitlyMarksTheProductionConstructorForSpringInjection() {
        Constructor<?> constructor = Arrays.stream(ValidationEngine.class.getDeclaredConstructors())
                .filter(candidate -> candidate.isAnnotationPresent(Autowired.class))
                .findFirst()
                .orElseThrow();
        assertThat(constructor.getParameterTypes())
                .containsExactly(ExpressionEvaluator.class, OperationQueryRuntime.class);
    }
}
