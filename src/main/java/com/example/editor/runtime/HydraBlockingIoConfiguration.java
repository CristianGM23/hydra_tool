package com.example.editor.runtime;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Centraliza las tareas bloqueantes cortas que solo esperan E/S externa.
 *
 * Los lectores de stdout/stderr no deben ocupar el common pool de
 * CompletableFuture ni crear pools de plataforma por cada archivo. Los hilos
 * virtuales son apropiados para esta espera bloqueante y el bean se cierra de
 * forma ordenada junto con Spring.
 */
@Configuration
public class HydraBlockingIoConfiguration {

    @Bean(name = "hydraBlockingIoExecutor", destroyMethod = "shutdownNow")
    /** Descripción: Ejecuta la operación «hydraBlockingIoExecutor» correspondiente a la responsabilidad de HydraBlockingIoConfiguration. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public ExecutorService hydraBlockingIoExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("hydra-blocking-io-", 0).factory());
    }
}
