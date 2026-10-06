package com.example.editor.audit;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

/** Registry de clasificadores HTTP ordenados; el clasificador genérico queda como fallback. */
@Component
public class AuditHttpActivityClassifierRegistry {
    private final List<AuditHttpActivityClassifier> classifiers;

    /** Descripción: Inicializa una instancia de AuditHttpActivityClassifierRegistry con sus dependencias y datos de entrada. Entradas: classifiers: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public AuditHttpActivityClassifierRegistry(List<AuditHttpActivityClassifier> classifiers) {
        this.classifiers = classifiers == null ? List.of() : classifiers.stream()
                .sorted(Comparator.comparingInt(AuditHttpActivityClassifier::priority).reversed()).toList();
    }

    /** Descripción: Ejecuta la operación «classify» correspondiente a la responsabilidad de AuditHttpActivityClassifierRegistry. Entradas: method: dato de entrada necesario para la operación; requestPath: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<AuditHttpActivityClassification> classify(String method, String requestPath) {
        for(AuditHttpActivityClassifier classifier : classifiers) {
            Optional<AuditHttpActivityClassification> result = classifier.classify(method, requestPath);
            if(result.isPresent()) return result;
        }
        return Optional.empty();
    }
}
