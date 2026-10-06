package com.example.editor.security.input;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.editor.audit.AuditRequestContext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Primera frontera de seguridad para peticiones HTTP de la API.
 *
 * Rechaza peticiones desproporcionadas y secuencias de ruta/control claramente
 * inseguras antes de llegar a un controlador. No inspecciona ni modifica el
 * contenido semántico de DSLs o modelos.
 */
@Component
public class HydraRequestHardeningFilter extends OncePerRequestFilter {
    private final HydraInputSecurityProperties properties;
    private final HydraInputPolicy policy;

    /** Descripción: Inicializa una instancia de HydraRequestHardeningFilter con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; policy: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public HydraRequestHardeningFilter(HydraInputSecurityProperties properties, HydraInputPolicy policy) {
        this.properties = properties;
        this.policy = policy;
    }

    @Override
    /** Descripción: Evalúa la condición «shouldNotFilter» sin modificar el estado externo. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null || !uri.startsWith("/api/");
    }

    @Override
    /** Descripción: Ejecuta la operación «doFilterInternal» correspondiente a la responsabilidad de RequestTooLargeException. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; response: dato de entrada necesario para la operación; filterChain: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            validateRequestLine(request);
            validateHeaders(request);
            validateContentLength(request);
            validateParameters(request);
            filterChain.doFilter(request, response);
        } catch(HydraInputValidationException exception) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_INPUT", exception.getMessage(), exception.field());
        } catch(RequestTooLargeException exception) {
            writeError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "REQUEST_TOO_LARGE", exception.getMessage(), "request");
        }
    }

    /** Descripción: Valida las condiciones necesarias para «validateRequestLine» y rechaza entradas no válidas. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateRequestLine(HttpServletRequest request) {
        String uri = request.getRequestURI() == null ? "" : request.getRequestURI();
        String query = request.getQueryString() == null ? "" : request.getQueryString();
        if(uri.length() > properties.getMaxRequestUriCharacters()) throw new HydraInputValidationException("requestUri", "Request URI is too long");
        if(query.length() > properties.getMaxQueryCharacters()) throw new HydraInputValidationException("query", "Request query is too long");
        String lower = uri.toLowerCase(Locale.ROOT);
        if(lower.contains("%00") || lower.contains("%0d") || lower.contains("%0a") || lower.contains("%2e%2e")
                || lower.contains("../") || lower.contains("..\\")) {
            throw new HydraInputValidationException("requestUri", "The request contains an unsafe path or encoded control sequence");
        }
    }

    /** Descripción: Valida las condiciones necesarias para «validateHeaders» y rechaza entradas no válidas. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateHeaders(HttpServletRequest request) {
        Enumeration<String> names = request.getHeaderNames();
        int count = 0;
        while(names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            if(++count > 120) throw new HydraInputValidationException("headers", "Too many request headers");
            if(name.length() > 128) throw new HydraInputValidationException("headers", "Request header name is too long");
            Enumeration<String> values = request.getHeaders(name);
            while(values.hasMoreElements()) {
                String value = values.nextElement();
                if(value != null && value.length() > properties.getMaxHeaderCharacters()) {
                    throw new HydraInputValidationException(name, "Request header value is too long");
                }
                policy.validateParameter(name, value);
            }
        }
    }

    /** Descripción: Valida las condiciones necesarias para «validateContentLength» y rechaza entradas no válidas. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateContentLength(HttpServletRequest request) {
        long length = request.getContentLengthLong();
        if(length < 0) return;
        String contentType = request.getContentType() == null ? "" : request.getContentType().toLowerCase(Locale.ROOT);
        long maximum = contentType.startsWith(MediaType.MULTIPART_FORM_DATA_VALUE)
                ? properties.getMaxMultipartRequestBytes()
                : properties.getMaxStructuredRequestBytes();
        if(length > maximum) throw new RequestTooLargeException("The request is too large");
    }

    /** Descripción: Valida las condiciones necesarias para «validateParameters» y rechaza entradas no válidas. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateParameters(HttpServletRequest request) {
        String contentType = request.getContentType() == null ? "" : request.getContentType().toLowerCase(Locale.ROOT);
        if(contentType.startsWith(MediaType.MULTIPART_FORM_DATA_VALUE)) return;
        for(Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            if(entry.getKey().length() > 128) throw new HydraInputValidationException("parameter", "Request parameter name is too long");
            for(String value : entry.getValue()) policy.validateParameter(entry.getKey(), value);
        }
    }

    /** Descripción: Actualiza el estado gestionado por RequestTooLargeException mediante la operación «writeError». Entradas: response: dato de entrada necesario para la operación; status: dato de entrada necesario para la operación; code: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; field: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void writeError(HttpServletResponse response, int status, String code, String message, String field) throws IOException {
        if(response.isCommitted()) return;
        response.resetBuffer();
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        String correlationId = AuditRequestContext.correlationId();
        response.getWriter().write("{\"type\":\"urn:hydra:error:" + json(code.toLowerCase(java.util.Locale.ROOT).replace('_','-'))
                + "\",\"title\":\"" + json(code) + "\",\"status\":" + status + ",\"detail\":\"" + json(message)
                + "\",\"correlationId\":\"" + json(correlationId) + "\",\"code\":\"" + json(code) + "\",\"message\":\"" + json(message)
                + "\",\"field\":\"" + json(field) + "\"}");
        response.flushBuffer();
    }

    /** Descripción: Ejecuta la operación «json» correspondiente a la responsabilidad de RequestTooLargeException. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String json(String value) {
        if(value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n");
    }

    private static final class RequestTooLargeException extends RuntimeException {
        /** Descripción: Inicializa una instancia de RequestTooLargeException con sus dependencias y datos de entrada. Entradas: message: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ RequestTooLargeException(String message) { super(message); }
    }
}
