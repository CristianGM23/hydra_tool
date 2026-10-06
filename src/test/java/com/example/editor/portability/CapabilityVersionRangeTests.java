package com.example.editor.portability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CapabilityVersionRangeTests {
    @Test void acceptsBlankAndWildcardRanges() { assertThat(CapabilityVersionRange.supports("1.0", "")).isTrue(); assertThat(CapabilityVersionRange.supports("1.0", "*")).isTrue(); }
    @Test void acceptsExactVersionsWithMissingMinorComponents() { assertThat(CapabilityVersionRange.supports("1.0.0", "1")).isTrue(); }
    @Test void comparesMinimumVersions() { assertThat(CapabilityVersionRange.supports("1.2", ">=1.0")).isTrue(); assertThat(CapabilityVersionRange.supports("0.9", ">=1.0")).isFalse(); }
    @Test void comparesMaximumVersions() { assertThat(CapabilityVersionRange.supports("1.2", "<2.0")).isTrue(); assertThat(CapabilityVersionRange.supports("2.0", "<2.0")).isFalse(); }
    @Test void combinesSeveralClauses() { assertThat(CapabilityVersionRange.supports("1.5", ">=1.0 <2.0")).isTrue(); }
    @Test void supportsClosedAndOpenIntervals() { assertThat(CapabilityVersionRange.supports("1.0", "[1.0,2.0)")).isTrue(); assertThat(CapabilityVersionRange.supports("2.0", "[1.0,2.0)")).isFalse(); }
    @Test void supportsCaretCompatibility() { assertThat(CapabilityVersionRange.supports("1.9", "^1.2")).isTrue(); assertThat(CapabilityVersionRange.supports("2.0", "^1.2")).isFalse(); }
    @Test void treatsStableReleaseAsNewerThanQualifier() { assertThat(CapabilityVersionRange.supports("1.0", ">1.0-beta")).isTrue(); }
    @Test void rejectsMalformedRanges() { assertThat(CapabilityVersionRange.valid("[1.0]")).isFalse(); assertThat(CapabilityVersionRange.valid(">=banana")).isFalse(); }
    @Test void rejectsMissingActualVersionForConstrainedDependency() { assertThat(CapabilityVersionRange.supports("", ">=1.0")).isFalse(); }
}
