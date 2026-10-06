package com.example.editor.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class BoundedRuntimeStateTests {
    @Test void historyIsBoundedByResourceCountAndDepth(){
        BoundedHistoryStore<Integer> history = new BoundedHistoryStore<>(2, 2);
        history.push("a", 1); history.push("a", 2); history.push("a", 3); history.push("b", 4); history.push("c", 5);
        assertThat(history.trackedKeys()).isEqualTo(2);
        assertThat(history.has("a")).isFalse();
        assertThat(history.pop("c")).contains(5);
        assertThat(history.trackedKeys()).isEqualTo(1);
    }

    @Test void stripedLocksUseConstantStorage(){
        StripedKeyLockRegistry locks = new StripedKeyLockRegistry(32);
        for(int index=0; index<10_000; index++){locks.lockFor("resource-" + index);}
        assertThat(locks.stripeCount()).isEqualTo(32);
    }
}
