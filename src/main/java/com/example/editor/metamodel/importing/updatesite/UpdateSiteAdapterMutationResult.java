package com.example.editor.metamodel.importing.updatesite;

/** Controlled mutation used to reveal over-broad or brittle matching. */
public record UpdateSiteAdapterMutationResult(String fixtureName, String mutation,
        boolean baselineMatched, boolean mutatedMatched, boolean killed, String explanation) { }
