package com.example.editor.integration.compatibility;

import java.time.Clock;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import com.example.editor.product.HydraReleaseVersion;

/** Builds a stable native-toolchain compatibility matrix without hiding partial support. */
@Service
public final class TechnologyCompatibilityMatrixService {
    private final Clock clock;

    /** Descripción: Inicializa una instancia de TechnologyCompatibilityMatrixService con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ public TechnologyCompatibilityMatrixService(){this(Clock.systemUTC());}

    /** Descripción: Inicializa una instancia de TechnologyCompatibilityMatrixService con sus dependencias y datos de entrada. Entradas: clock: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TechnologyCompatibilityMatrixService(Clock clock){
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Descripción: Crea o prepara el resultado de «build» dentro de TechnologyCompatibilityMatrixService. Entradas: probes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TechnologyCompatibilityMatrix build(List<NativeToolchainProbeResult> probes){
        Objects.requireNonNull(probes, "probes");
        Set<String> keys = new HashSet<>();
        for(NativeToolchainProbeResult probe : probes){
            if(!keys.add(Objects.requireNonNull(probe, "probe").key())){
                throw new IllegalArgumentException("Duplicate technology/version probe: " + probe.key());
            }
        }
        List<NativeToolchainProbeResult> ordered = probes.stream()
                .sorted(Comparator.comparing(NativeToolchainProbeResult::technology, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(NativeToolchainProbeResult::version, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new TechnologyCompatibilityMatrix(HydraReleaseVersion.VERSION, clock.instant(), overall(ordered), ordered);
    }

    /** Descripción: Ejecuta la operación «overall» correspondiente a la responsabilidad de TechnologyCompatibilityMatrixService. Entradas: probes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private CompatibilityStatus overall(List<NativeToolchainProbeResult> probes){
        if(probes.isEmpty() || probes.stream().allMatch(item -> item.status() == CompatibilityStatus.NOT_RUN)){
            return CompatibilityStatus.NOT_RUN;
        }
        if(probes.stream().anyMatch(item -> item.status() == CompatibilityStatus.FAILED)){
            return CompatibilityStatus.FAILED;
        }
        if(probes.stream().anyMatch(item -> item.status() == CompatibilityStatus.PARTIAL || item.status() == CompatibilityStatus.NOT_RUN)){
            return CompatibilityStatus.PARTIAL;
        }
        return CompatibilityStatus.PASSED;
    }
}
