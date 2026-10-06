package com.example.editor.metamodel.importing;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configura los ejecutores acotados del análisis de importación.
 */
@Configuration
public class MetamodelImportAnalysisConfiguration {

    /** Crea el executor limitado que procesa los análisis. */
    @Bean(name = "metamodelImportAnalysisExecutor")
    /** Descripción: Ejecuta la operación «metamodelImportAnalysisExecutor» correspondiente a la responsabilidad de MetamodelImportAnalysisConfiguration. Entradas: properties: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public Executor metamodelImportAnalysisExecutor(MetamodelImportProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int corePoolSize = Math.max(1, properties.getAnalysisCorePoolSize());
        int maxPoolSize = Math.max(corePoolSize, properties.getAnalysisMaxPoolSize());
        executor.setThreadNamePrefix("metamodel-import-analysis-");
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(Math.max(1, properties.getAnalysisQueueCapacity()));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(properties.getAnalysisShutdownWaitSeconds());
        executor.initialize();
        return executor;
    }

    /**
     * Watchdog independiente del pool de trabajo. Un análisis que monopoliza todos los workers
     * no puede impedir que sus propios deadlines sean aplicados.
     */
    @Bean(name = "metamodelImportAnalysisWatchdog", destroyMethod = "shutdown")
    /** Descripción: Ejecuta la operación «metamodelImportAnalysisWatchdog» correspondiente a la responsabilidad de MetamodelImportAnalysisConfiguration. Entradas: properties: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public ScheduledExecutorService metamodelImportAnalysisWatchdog(MetamodelImportProperties properties) {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "metamodel-import-watchdog-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        ScheduledThreadPoolExecutor watchdog = new ScheduledThreadPoolExecutor(properties.getAnalysisWatchdogPoolSize(), factory);
        watchdog.setRemoveOnCancelPolicy(true);
        watchdog.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        watchdog.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        return watchdog;
    }
}
