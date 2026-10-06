package com.example.editor.system;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.editor.model.ModelSessionProperties;
import com.example.editor.product.HydraProductIdentity;

/**
 * Comprueba condiciones locales necesarias antes de considerar publicable la
 * instalación actual de HYDRA. No crea directorios ni modifica artefactos.
 */
@Service
public class HydraPlatformReadinessService {
    private static final List<String> CRITICAL_CONTRACTS = List.of(
            "schemas/metamodel/meta-model-2.1.schema.json",
            "schemas/validation/validation-model-1.0.schema.json",
            "schemas/editor/editor-definition-model-1.0.schema.json",
            "schemas/event/event-flow-model-1.0.schema.json",
            "schemas/portability/layer-interchange-envelope-1.0.schema.json");

    private final int minimumJavaFeature;
    private final String releaseGateRoot;
    private final int releaseGateMaximumReports;
    private final int maximumModelSessions;
    private final Duration modelSessionIdleTimeout;
    private final ClassLoader classLoader;

    /** Crea el servicio con la configuración efectiva de la aplicación. */
    @Autowired
    /** Descripción: Inicializa una instancia de HydraPlatformReadinessService con sus dependencias y datos de entrada. Entradas: minimumJavaFeature: dato de entrada necesario para la operación; releaseGateRoot: dato de entrada necesario para la operación; releaseGateMaximumReports: dato de entrada necesario para la operación; modelSessionProperties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public HydraPlatformReadinessService(@Value("${hydra.platform-readiness.minimum-java-feature:24}") int minimumJavaFeature, @Value("${hydra.release-gates.root:data/release-gates}") String releaseGateRoot, @Value("${hydra.release-gates.maximum-reports:200}") int releaseGateMaximumReports, ModelSessionProperties modelSessionProperties) {
        this(minimumJavaFeature, releaseGateRoot, releaseGateMaximumReports, modelSessionProperties.getMaxActive(), modelSessionProperties.getIdleTimeout(), HydraPlatformReadinessService.class.getClassLoader());
    }

    /** Descripción: Inicializa una instancia de HydraPlatformReadinessService con sus dependencias y datos de entrada. Entradas: minimumJavaFeature: dato de entrada necesario para la operación; releaseGateRoot: dato de entrada necesario para la operación; releaseGateMaximumReports: dato de entrada necesario para la operación; maximumModelSessions: dato de entrada necesario para la operación; modelSessionIdleTimeout: dato de entrada necesario para la operación; classLoader: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ HydraPlatformReadinessService(int minimumJavaFeature, String releaseGateRoot, int releaseGateMaximumReports, int maximumModelSessions, Duration modelSessionIdleTimeout, ClassLoader classLoader) {
        this.minimumJavaFeature = Math.max(1, minimumJavaFeature);
        this.releaseGateRoot = releaseGateRoot == null ? "" : releaseGateRoot.trim();
        this.releaseGateMaximumReports = releaseGateMaximumReports;
        this.maximumModelSessions = maximumModelSessions;
        this.modelSessionIdleTimeout = modelSessionIdleTimeout;
        this.classLoader = classLoader == null ? HydraPlatformReadinessService.class.getClassLoader() : classLoader;
    }

    /** Evalúa readiness sin producir efectos laterales. */
    /** Descripción: Ejecuta la operación «evaluate» correspondiente a la responsabilidad de HydraPlatformReadinessService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public HydraPlatformReadinessReport evaluate() {
        List<HydraPlatformReadinessCheck> checks = new ArrayList<>();
        int javaFeature = Runtime.version().feature();
        checks.add(check("JAVA_RUNTIME", javaFeature >= minimumJavaFeature,
                "Java " + javaFeature + " satisfies the configured minimum " + minimumJavaFeature + ".",
                "Java " + javaFeature + " is below the configured minimum " + minimumJavaFeature + "."));
        boolean productIdentityValid = "HYDRA".equals(HydraProductIdentity.PRODUCT)
                && HydraProductIdentity.ACRONYM != null && !HydraProductIdentity.ACRONYM.isBlank();
        checks.add(check("PRODUCT_IDENTITY", productIdentityValid,
                "Product identity is configured.",
                "Product identity is incomplete."));
        boolean releaseGateConfiguration = !releaseGateRoot.isBlank() && releaseGateMaximumReports > 0;
        checks.add(check("RELEASE_GATE_STORAGE", releaseGateConfiguration,
                "Release-gate evidence retention is configured.",
                "Release-gate storage root or retention limit is invalid."));
        boolean sessionConfiguration = maximumModelSessions > 0 && modelSessionIdleTimeout != null
                && !modelSessionIdleTimeout.isZero() && !modelSessionIdleTimeout.isNegative();
        checks.add(check("MODEL_SESSION_LIMITS", sessionConfiguration,
                "Model-session capacity and idle timeout are bounded.",
                "Model-session capacity or idle timeout is invalid."));
        List<String> missingContracts = CRITICAL_CONTRACTS.stream().filter(resource -> classLoader.getResource(resource) == null).toList();
        checks.add(check("CANONICAL_CONTRACTS", missingContracts.isEmpty(),
                "Critical canonical schemas, including Layer Interchange 1.0, are available.",
                "Missing critical canonical schemas: " + String.join(", ", missingContracts)));
        String status = checks.stream().anyMatch(item -> item.status() == HydraPlatformReadinessCheckStatus.FAIL)
                ? "NOT_READY"
                : checks.stream().anyMatch(item -> item.status() == HydraPlatformReadinessCheckStatus.WARNING) ? "DEGRADED" : "READY";
        return new HydraPlatformReadinessReport(HydraProductIdentity.PRODUCT, status, javaFeature, checks);
    }

    /** Descripción: Valida las condiciones necesarias para «check» y rechaza entradas no válidas. Entradas: code: dato de entrada necesario para la operación; passed: dato de entrada necesario para la operación; successMessage: dato de entrada necesario para la operación; failureMessage: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private HydraPlatformReadinessCheck check(String code, boolean passed, String successMessage, String failureMessage) {
        return new HydraPlatformReadinessCheck(code,
                passed ? HydraPlatformReadinessCheckStatus.PASS : HydraPlatformReadinessCheckStatus.FAIL,
                passed ? successMessage : failureMessage);
    }
}
