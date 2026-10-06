package com.example.editor.web.version;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Adds a stable API contract version header while keeping existing /api routes compatible. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public final class HydraApiVersionFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Hydra-Api-Version";
    private final HydraApiContractProperties properties;
    private final HydraApiDeprecationRegistry deprecations;

    /** Descripción: Inicializa una instancia de HydraApiVersionFilter con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public HydraApiVersionFilter(HydraApiContractProperties properties,HydraApiDeprecationRegistry deprecations) { this.properties = properties; this.deprecations=deprecations; }

    @Override
    /** Descripción: Evalúa la condición «shouldNotFilter» sin modificar el estado externo. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null || !(uri.equals("/api") || uri.startsWith("/api/"));
    }

    @Override
    /** Descripción: Ejecuta la operación «doFilterInternal» correspondiente a la responsabilidad de HydraApiVersionFilter. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; response: dato de entrada necesario para la operación; chain: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader(HEADER, properties.getVersion());
        HydraApiDeprecationRegistry.Entry deprecated=deprecations.find(request.getRequestURI());
        if(deprecated!=null){response.setHeader("Deprecation","true");response.setHeader("Sunset",deprecated.sunset().toString());response.setHeader("Link","<"+deprecated.replacement()+">; rel=successor-version");}
        chain.doFilter(request, response);
    }
}
