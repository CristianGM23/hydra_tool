package com.example.editor.audit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Punto de sustitución limpio para autenticación futura sin modificar el subsistema de auditoría. */
@Configuration
public class AuditActorConfiguration {
    @Bean
    @ConditionalOnMissingBean(AuditActorContext.class)
    /** Descripción: Ejecuta la operación «localAuditActorContext» correspondiente a la responsabilidad de AuditActorConfiguration. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public AuditActorContext localAuditActorContext() {
        return new LocalAuditActorContext();
    }
}
