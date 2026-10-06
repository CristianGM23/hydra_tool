package com.example.editor.security.input;

import java.lang.reflect.Type;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

/**
 * Segunda frontera: valida el objeto JSON ya deserializado mediante límites
 * universales y reglas de texto plano, sin alterar expresiones ni código.
 */
@Component
public class HydraRequestBodyHardeningAdvice extends RequestBodyAdviceAdapter {
    private final HydraInputPolicy policy;

    /** Descripción: Inicializa una instancia de HydraRequestBodyHardeningAdvice con sus dependencias y datos de entrada. Entradas: policy: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public HydraRequestBodyHardeningAdvice(HydraInputPolicy policy) {
        this.policy = policy;
    }

    @Override
    /** Descripción: Evalúa la condición «supports» sin modificar el estado externo. Entradas: methodParameter: dato de entrada necesario para la operación; targetType: dato de entrada necesario para la operación; converterType: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean supports(MethodParameter methodParameter, Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        return methodParameter.getContainingClass().getPackageName().startsWith("com.example.editor");
    }

    @Override
    /** Descripción: Ejecuta la operación «afterBodyRead» correspondiente a la responsabilidad de HydraRequestBodyHardeningAdvice. Entradas: body: dato de entrada necesario para la operación; inputMessage: dato de entrada necesario para la operación; parameter: dato de entrada necesario para la operación; targetType: dato de entrada necesario para la operación; converterType: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
            Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        policy.validatePayload(body);
        return body;
    }
}
