package com.example.editor.web;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import com.example.editor.audit.AuditRequestContext;

/** Converts HYDRA ApiError bodies to problem+json while preserving actionable diagnostic fields. */
@ControllerAdvice
public class HydraProblemDetailsAdvice implements ResponseBodyAdvice<Object> {
    /** Descripción: Evalúa la condición «supports» sin modificar el estado externo. Entradas: returnType: dato de entrada necesario para la operación; converterType: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ @Override public boolean supports(MethodParameter returnType,Class<? extends HttpMessageConverter<?>> converterType){return true;}
    /** Descripción: Ejecuta la operación «beforeBodyWrite» correspondiente a la responsabilidad de HydraProblemDetailsAdvice. Entradas: body: dato de entrada necesario para la operación; returnType: dato de entrada necesario para la operación; selectedContentType: dato de entrada necesario para la operación; converterType: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación; response: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Object beforeBodyWrite(Object body,MethodParameter returnType,MediaType selectedContentType,Class<? extends HttpMessageConverter<?>> converterType,ServerHttpRequest request,ServerHttpResponse response){
        if(!(body instanceof ApiExceptionHandler.ApiError error)) return body;
        int status=response instanceof org.springframework.http.server.ServletServerHttpResponse servlet?servlet.getServletResponse().getStatus():500;
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        String code=error.code()==null||error.code().isBlank()?"HYDRA_ERROR":error.code();
        String path=request.getURI().getPath();
        String operation=request.getMethod()+" "+path;
        String correlation=error.correlationId()==null||error.correlationId().isBlank()?AuditRequestContext.correlationId():error.correlationId();
        return new HydraProblemDetails("urn:hydra:error:"+code.toLowerCase().replace('_','-'),code,status,error.message(),path,correlation,code,error.message(),error.field(),error.reason(),error.solution(),error.suggestions(),error.technicalDetail(),operation,path,error.impact(),error.retryable(),error.recoveryActions());
    }
}
