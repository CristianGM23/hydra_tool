package com.example.editor.audit;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class AuditRedactionRegistry {
    public static final String REDACTED = "[redacted]";
    private final List<AuditRedactionPolicy> policies;
    /** Descripción: Inicializa una instancia de AuditRedactionRegistry con sus dependencias y datos de entrada. Entradas: policies: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public AuditRedactionRegistry(List<AuditRedactionPolicy> policies) { this.policies = policies == null ? List.of() : List.copyOf(policies); }
    /** Descripción: Ejecuta la operación «protect» correspondiente a la responsabilidad de AuditRedactionRegistry. Entradas: path: ruta o ubicación utilizada por la operación; value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public Object protect(String path, Object value) { return sensitive(path) ? REDACTED : value; }
    /** Descripción: Ejecuta la operación «sensitive» correspondiente a la responsabilidad de AuditRedactionRegistry. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean sensitive(String path) { return policies.stream().anyMatch(policy -> policy.sensitive(path)); }
}
