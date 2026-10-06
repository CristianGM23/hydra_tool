package com.example.editor.validation.quickfix;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.editor.validation.runtime.ValidationChangeSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuickFixContractsTests {
    @Test void requestRemovesBlankAndRepeatedSelectionIds() {
        QuickFixExecutionRequest request = new QuickFixExecutionRequest(Map.of(), List.of("one", "", "one", "two"));
        assertThat(request.selectionElementIds()).containsExactly("one", "two");
    }
    @Test void requestCopiesArguments() {
        Map<String, Object> source = new LinkedHashMap<>(); source.put("name", "before");
        QuickFixExecutionRequest request = new QuickFixExecutionRequest(source, List.of());
        source.put("name", "after");
        assertThat(request.arguments()).containsEntry("name", "before");
    }
    @Test void requestArgumentsAreImmutable() {
        QuickFixExecutionRequest request = new QuickFixExecutionRequest(Map.of("name", "value"), List.of());
        assertThatThrownBy(() -> request.arguments().put("other", "value")).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void changeSummaryDefensivelyCopiesLists() {
        List<String> created = new ArrayList<>(List.of("one"));
        CanonicalModelChangeSummary summary = new CanonicalModelChangeSummary(created, List.of(), List.of(), List.of(), List.of(), List.of(), ValidationChangeSet.empty());
        created.add("two");
        assertThat(summary.createdElementIds()).containsExactly("one");
    }
    @Test void changeSummaryReportsChangesFromValidationChangeSet() {
        CanonicalModelChangeSummary summary = new CanonicalModelChangeSummary(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new ValidationChangeSet(List.of(), List.of(), List.of(), List.of(), List.of(), true));
        assertThat(summary.changed()).isTrue();
    }
    @Test void noChangeSummaryReportsFalse() {
        CanonicalModelChangeSummary summary = new CanonicalModelChangeSummary(null, null, null, null, null, null, null);
        assertThat(summary.changed()).isFalse();
        assertThat(summary.validationChangeSet()).isEqualTo(ValidationChangeSet.empty());
    }
}
