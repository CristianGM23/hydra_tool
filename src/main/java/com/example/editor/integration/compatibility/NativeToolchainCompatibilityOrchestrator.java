package com.example.editor.integration.compatibility;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Runs toolchain definitions in a stable order and builds the canonical compatibility matrix. */
@Service
public class NativeToolchainCompatibilityOrchestrator {
    private final NativeToolchainProbeExecutor executor;
    private final TechnologyCompatibilityMatrixService matrixService;
    /** Descripción: Inicializa una instancia de NativeToolchainCompatibilityOrchestrator con sus dependencias y datos de entrada. Entradas: executor: dato de entrada necesario para la operación; matrixService: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public NativeToolchainCompatibilityOrchestrator(NativeToolchainProbeExecutor executor,
            TechnologyCompatibilityMatrixService matrixService){
        this.executor = Objects.requireNonNull(executor, "executor");
        this.matrixService = Objects.requireNonNull(matrixService, "matrixService");
    }
    /** Descripción: Procesa la operación «execute» coordinando las responsabilidades de NativeToolchainCompatibilityOrchestrator. Entradas: definitions: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TechnologyCompatibilityMatrix execute(List<NativeToolchainProbeDefinition> definitions){
        Objects.requireNonNull(definitions, "definitions");
        Set<String> keys = new HashSet<>();
        for(NativeToolchainProbeDefinition definition : definitions){
            if(!keys.add(Objects.requireNonNull(definition, "definition").key())){
                throw new IllegalArgumentException("Duplicate toolchain definition: " + definition.key());
            }
        }
        List<NativeToolchainProbeResult> results = definitions.stream()
                .sorted(Comparator.comparing(NativeToolchainProbeDefinition::technology, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(NativeToolchainProbeDefinition::version, String.CASE_INSENSITIVE_ORDER))
                .map(executor::execute).toList();
        return matrixService.build(results);
    }
}
