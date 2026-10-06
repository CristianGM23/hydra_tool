package com.example.editor.metamodel.importing.updatesite;

/** Minimized structural fuzzing failure for one declared invariant. */
public record UpdateSiteAdapterVerificationFailure(String caseName, String invariant,
        UpdateSiteAdapterFixture fixture, UpdateSiteAdapterFixture minimizedFixture,
        String explanation) { }
