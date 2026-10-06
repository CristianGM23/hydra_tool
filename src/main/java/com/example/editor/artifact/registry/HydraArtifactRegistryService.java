package com.example.editor.artifact.registry;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.editor.artifact.transaction.HydraArtifactTransactionOperation;
import com.example.editor.artifact.transaction.HydraArtifactTransactionService;
import com.example.editor.runtime.job.HydraJobExecutionService;
import com.example.editor.runtime.job.HydraJobSnapshot;

/** Servicio de aplicación para registrar, escanear y consultar artefactos neutrales. */
@Service
public class HydraArtifactRegistryService {
    private final HydraArtifactRegistryStore store;
    private final HydraArtifactScanner scanner;
    private final HydraArtifactGraphService graphs;
    private final HydraArtifactCompatibilityService compatibility;
    private final HydraArtifactTransactionService transactions;
    private final HydraJobExecutionService jobs;
    private final Path registryRoot;
    private final Path scanRoot;

    /** Descripción: Inicializa el servicio con almacenamiento, grafo, compatibilidad, transacciones y trabajos globales. Entradas: store: persistencia; scanner: inventario; graphs: grafo; compatibility: compatibilidad; transactions: journal; jobs: ejecución asíncrona; registryRoot: raíz de registro; scanRoot: raíz de escaneo. Salida: deja el servicio listo. */
    public HydraArtifactRegistryService(HydraArtifactRegistryStore store, HydraArtifactScanner scanner,
            HydraArtifactGraphService graphs, HydraArtifactCompatibilityService compatibility,
            HydraArtifactTransactionService transactions, HydraJobExecutionService jobs,
            @Value("${hydra.artifacts.registry-root:data/artifacts}") String registryRoot,
            @Value("${hydra.artifacts.scan-root:data}") String scanRoot) {
        this.store = store;
        this.scanner = scanner;
        this.graphs = graphs;
        this.compatibility = compatibility;
        this.transactions = transactions;
        this.jobs = jobs;
        this.registryRoot = Path.of(registryRoot).toAbsolutePath().normalize();
        this.scanRoot = Path.of(scanRoot).toAbsolutePath().normalize();
    }

    /** Descripción: Obtiene la instantánea persistida del registro. Entradas: no requiere parámetros. Salida: snapshot actual. */
    public HydraArtifactRegistrySnapshot snapshot() { return store.load(registryRoot); }

    /** Descripción: Construye el grafo de dependencias a partir de la instantánea actual. Entradas: no requiere parámetros. Salida: grafo completo. */
    public HydraArtifactDependencyGraph graph() { return graphs.build(snapshot()); }

    /** Descripción: Calcula impacto transitivo de retirar o modificar un artefacto. Entradas: artifactId: identificador objetivo. Salida: dependientes y orden seguro. */
    public HydraArtifactImpact impact(String artifactId) { return graphs.impact(snapshot(), artifactId); }

    /** Descripción: Evalúa compatibilidad semántica y de versiones de un artefacto con sus dependencias. Entradas: artifactId: identificador objetivo. Salida: informe de compatibilidad. */
    public HydraArtifactCompatibilityReport compatibility(String artifactId) { return compatibility.evaluate(snapshot(), artifactId); }

    /** Descripción: Registra o sustituye un descriptor dentro de una transacción recuperable. Entradas: descriptor: descriptor neutral. Salida: snapshot actualizado. */
    public HydraArtifactRegistrySnapshot register(HydraArtifactDescriptor descriptor) {
        return transactions.execute(HydraArtifactTransactionOperation.UPDATE, descriptor.id(),
                List.of(registryRoot.toString()), () -> {
                    Map<String, HydraArtifactDescriptor> byId = new LinkedHashMap<>();
                    snapshot().artifacts().forEach(item -> byId.put(item.id(), item));
                    byId.put(descriptor.id(), descriptor);
                    return store.save(registryRoot, new HydraArtifactRegistrySnapshot("1.0", Instant.now(), List.copyOf(byId.values())));
                });
    }

    /** Descripción: Escanea síncronamente la raíz configurada y publica el registro dentro de una transacción recuperable. Entradas: no requiere parámetros. Salida: snapshot actualizado. */
    public HydraArtifactRegistrySnapshot scan() {
        return transactions.execute(HydraArtifactTransactionOperation.SCAN, "artifact-registry",
                List.of(registryRoot.toString(), scanRoot.toString()), () -> { return scanInternal(); });
    }

    /** Descripción: Encola un escaneo de artefactos en el ejecutor transversal para no bloquear la petición HTTP. Entradas: correlationId: identificador opcional de correlación. Salida: instantánea inicial del trabajo. */
    public HydraJobSnapshot scanAsync(String correlationId) {
        return jobs.submit("ARTIFACT_SCAN", "Scan canonical artifacts", correlationId, context -> {
            context.progress(5, "DISCOVERY", "Scanning canonical artifact root");
            context.throwIfCancellationRequested();
            transactions.execute(HydraArtifactTransactionOperation.SCAN, "artifact-registry",
                    List.of(registryRoot.toString(), scanRoot.toString()), () -> {
                        context.progress(45, "HASHING", "Hashing and classifying artifacts");
                        scanInternal();
                    });
            context.progress(95, "PUBLISHING", "Artifact registry published");
        });
    }

    /** Descripción: Ejecuta la lógica interna de escaneo sin crear otro journal anidado. Entradas: no requiere parámetros. Salida: snapshot publicado. */
    private HydraArtifactRegistrySnapshot scanInternal() {
        Map<String, HydraArtifactDescriptor> byId = new LinkedHashMap<>();
        snapshot().artifacts().forEach(item -> byId.put(item.id(), item));
        scanner.scan(scanRoot).forEach(item -> byId.put(item.id(), item));
        return store.save(registryRoot, new HydraArtifactRegistrySnapshot("1.0", Instant.now(), List.copyOf(byId.values())));
    }
}
