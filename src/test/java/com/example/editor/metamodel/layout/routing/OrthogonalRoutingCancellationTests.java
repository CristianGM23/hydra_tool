package com.example.editor.metamodel.layout.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CancellationException;

import org.junit.jupiter.api.Test;

/**
 * The metamodel import analysis job routes the imported diagram and, when it exceeds its deadline, cancels with an interrupt
 * (MetamodelImportAnalysisJobService: task.cancel(true)). The router ignored interrupts, so a cancelled import kept a worker thread busy
 * routing until the end (430 s for 250 classes with jar 11). It must stop, as the textual parser does (CancellationException).
 */
class OrthogonalRoutingCancellationTests {

    @Test
    void anInterruptedRoutingStopsInsteadOfFinishing() {
        var diagram = OrthogonalRoutingEquivalenceTests.route();  // warms the engine once without interruption
        assertThat(diagram.edges()).isNotEmpty();
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(OrthogonalRoutingEquivalenceTests::route).isInstanceOf(CancellationException.class);
        } finally {
            Thread.interrupted();  // never leave the test thread interrupted
        }
    }

    @Test
    void concurrentRoutingsShareNoStateAndGiveTheSameRoutes() throws Exception {
        var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<String>>();
            for (int i = 0; i < 4; i++) futures.add(pool.submit(() -> OrthogonalRoutingEquivalenceTests.fingerprint(OrthogonalRoutingEquivalenceTests.route())));
            for (var future : futures) assertThat(future.get()).isEqualTo(OrthogonalRoutingEquivalenceTests.ROUTES_OF_THE_PREVIOUS_ENGINE);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void routingWithoutInterruptionIsUnchanged() throws Exception {
        assertThat(OrthogonalRoutingEquivalenceTests.fingerprint(OrthogonalRoutingEquivalenceTests.route()))
                .isEqualTo(OrthogonalRoutingEquivalenceTests.ROUTES_OF_THE_PREVIOUS_ENGINE);
    }
}
