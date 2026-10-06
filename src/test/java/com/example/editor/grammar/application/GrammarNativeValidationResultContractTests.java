package com.example.editor.grammar.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.editor.grammar.runtime.GrammarNativeValidationResult;
import com.example.editor.grammar.runtime.GrammarNativeValidationStep;

class GrammarNativeValidationResultContractTests {
    @Test void unavailableToolDefersNativeVerificationWithoutCallingGeneratedProjectInvalid() {
        var result = new GrammarNativeValidationResult(true, false,
                List.of(new GrammarNativeValidationStep("langium-dependencies", "TOOL_UNAVAILABLE", 1, null,
                        List.of("Executable is unavailable: npm"))), Map.of());
        assertThat(result.verificationDeferred()).isTrue();
        assertThat(result.definitiveFailure()).isFalse();
    }

    @Test void temporarilySkippedVerifierIsDeferred() {
        var result = new GrammarNativeValidationResult(true, false,
                List.of(new GrammarNativeValidationStep("xtext-generate", "SKIPPED", 0, null,
                        List.of("Native validation capacity is busy; retry later"))), Map.of());
        assertThat(result.verificationDeferred()).isTrue();
        assertThat(result.definitiveFailure()).isFalse();
    }

    @Test void actualGeneratedBuildFailureRemainsBlocking() {
        var result = new GrammarNativeValidationResult(true, false,
                List.of(new GrammarNativeValidationStep("antlr-java-build", "FAILED", 14, 1,
                        List.of("error(50): Main.g4:3:8: syntax error"))), Map.of());
        assertThat(result.verificationDeferred()).isFalse();
        assertThat(result.definitiveFailure()).isTrue();
    }
    @Test void nativeTimeoutDefersVerificationInsteadOfDiscardingGeneratedInfrastructure() {
        var result = new GrammarNativeValidationResult(true, false,
                List.of(new GrammarNativeValidationStep("langium-dependencies", "TIMED_OUT", 60_000, null,
                        List.of("Process exceeded 60 seconds"))), Map.of());
        assertThat(result.verificationDeferred()).isTrue();
        assertThat(result.definitiveFailure()).isFalse();
    }

    @Test void failedDependencyInstallationIsDeferredBecauseItDoesNotInvalidateGrammarSemantics() {
        var result = new GrammarNativeValidationResult(true, false,
                List.of(new GrammarNativeValidationStep("langium-dependencies", "FAILED", 3200, 1,
                        List.of("npm ERR! code ENOTFOUND\nnpm ERR! network request to https://registry.npmjs.org/langium failed"))), Map.of());
        assertThat(result.verificationDeferred()).isTrue();
        assertThat(result.definitiveFailure()).isFalse();
    }

    @Test void mavenRepositoryOutageIsDeferredEvenWhenItOccursDuringGeneratorStep() {
        var result = new GrammarNativeValidationResult(true, false,
                List.of(new GrammarNativeValidationStep("xtext-generate", "FAILED", 4000, 1,
                        List.of("Could not transfer artifact org.eclipse.xtext:xtext:jar:2.44.0 from central"))), Map.of());
        assertThat(result.verificationDeferred()).isTrue();
        assertThat(result.definitiveFailure()).isFalse();
    }

    @Test void realLangiumGeneratedBuildFailureRemainsBlocking() {
        var result = new GrammarNativeValidationResult(true, false,
                List.of(new GrammarNativeValidationStep("langium-build", "FAILED", 800, 2,
                        List.of("src/language/main.ts(12,4): error TS2304: Cannot find name BrokenGeneratedSymbol"))), Map.of());
        assertThat(result.verificationDeferred()).isFalse();
        assertThat(result.definitiveFailure()).isTrue();
    }

}
