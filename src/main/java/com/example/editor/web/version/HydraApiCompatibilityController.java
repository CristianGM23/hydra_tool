package com.example.editor.web.version;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
/** Expone la política de versión/deprecación para clientes HTTP y generadores. */
@RestController
@RequestMapping("/api/compatibility")
public class HydraApiCompatibilityController {
    private final HydraApiContractProperties properties; private final HydraApiDeprecationRegistry deprecations;
    /** Descripción: Inicializa el controlador de compatibilidad. Entradas: properties, deprecations. Salida: instancia lista. */ public HydraApiCompatibilityController(HydraApiContractProperties properties,HydraApiDeprecationRegistry deprecations){this.properties=properties;this.deprecations=deprecations;}
    /** Descripción: Devuelve versión contractual y deprecaciones actuales. Entradas: ninguna. Salida: documento estable. */ @GetMapping public Map<String,Object> compatibility(){return Map.of("apiVersion",properties.getVersion(),"pathVersioning","compatible-unversioned-v1","deprecations",deprecations.entries());}
}
