package com.example.editor.transformation.testing;

import java.util.List;

/** Cobertura funcional de reglas y bindings observada al ejecutar la suite. */
public record TransformationCoverageReport(
        int ruleTotal,
        int ruleCovered,
        int bindingTotal,
        int bindingCovered,
        List<TransformationRuleCoverage> rules,
        List<TransformationBindingCoverage> bindings) {
    public TransformationCoverageReport {
        rules = rules == null ? List.of() : List.copyOf(rules);
        bindings = bindings == null ? List.of() : List.copyOf(bindings);
    }
}
