package com.example.editor.runtime.job;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Configura el executor acotado compartido por operaciones largas que adopten HydraJobExecutionService. */
@Configuration
public class HydraJobExecutionConfiguration {
    @Bean(name = "hydraJobExecutor")
    /** Descripción: Crea el executor transversal con concurrencia y cola explícitamente acotadas. Entradas: properties: límites globales. Salida: executor administrado por Spring. */
    public ThreadPoolTaskExecutor hydraJobExecutor(HydraJobProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("hydra-job-");
        executor.setCorePoolSize(properties.getMaximumConcurrent());
        executor.setMaxPoolSize(properties.getMaximumConcurrent());
        executor.setQueueCapacity(properties.getMaximumQueued());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
