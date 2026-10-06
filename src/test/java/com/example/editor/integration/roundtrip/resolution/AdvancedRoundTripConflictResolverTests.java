package com.example.editor.integration.roundtrip.resolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AdvancedRoundTripConflictResolverTests {
    private final AdvancedRoundTripConflictResolver resolver = new AdvancedRoundTripConflictResolver();

    @Test
    void resolvesEveryConflictInStableOrder(){
        ElementConflict second = conflict("b", "generated/B.java", false, Set.of(ConflictResolutionChoice.KEEP_NATIVE));
        ElementConflict first = conflict("a", "generated/A.java", false, Set.of(ConflictResolutionChoice.MERGE_SAFE));
        RoundTripResolutionResult result = resolver.resolve(List.of(second, first), List.of(
                decision(second, ConflictResolutionChoice.KEEP_NATIVE),
                decision(first, ConflictResolutionChoice.MERGE_SAFE)));
        assertThat(result.canApply()).isTrue();
        assertThat(result.resolved()).extracting(item -> item.conflict().key())
                .containsExactly(first.key(), second.key());
    }

    @Test
    void leavesMissingOrSkippedDecisionsUnresolved(){
        ElementConflict conflict = conflict("a", "generated/A.java", false, Set.of(ConflictResolutionChoice.KEEP_NATIVE));
        RoundTripResolutionResult missing = resolver.resolve(List.of(conflict), List.of());
        RoundTripResolutionResult skipped = resolver.resolve(List.of(conflict), List.of(decision(conflict, ConflictResolutionChoice.SKIP)));
        assertThat(missing.canApply()).isFalse();
        assertThat(skipped.unresolved()).containsExactly(conflict);
    }

    @Test
    void protectsManualRegionsFromImplicitHydraOverwrite(){
        ElementConflict conflict = conflict("a", "generated/A.java", true,
                Set.of(ConflictResolutionChoice.KEEP_HYDRA, ConflictResolutionChoice.KEEP_MANUAL));
        assertThatThrownBy(() -> resolver.resolve(List.of(conflict), List.of(decision(conflict, ConflictResolutionChoice.KEEP_HYDRA))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("manual region");
    }

    @Test
    void rejectsUnknownOrUnsupportedDecisions(){
        ElementConflict conflict = conflict("a", "generated/A.java", false, Set.of(ConflictResolutionChoice.KEEP_NATIVE));
        assertThatThrownBy(() -> resolver.resolve(List.of(conflict), List.of(
                new ElementConflictDecision("other@generated/X.java", ConflictResolutionChoice.KEEP_NATIVE, "external wins"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
        assertThatThrownBy(() -> resolver.resolve(List.of(conflict), List.of(decision(conflict, ConflictResolutionChoice.MERGE_SAFE))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported");
    }

    private ElementConflict conflict(String elementId, String path, boolean manual, Set<ConflictResolutionChoice> choices){
        return new ElementConflict(elementId, path, "hydra", "native", manual, choices);
    }

    private ElementConflictDecision decision(ElementConflict conflict, ConflictResolutionChoice choice){
        return new ElementConflictDecision(conflict.key(), choice, "explicit test decision");
    }
}
