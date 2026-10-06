package com.example.editor.metamodel.importing.updatesite;

/** Creation or validation result for an offline p2 mirror. */
public record UpdateSiteP2MirrorResult(UpdateSiteP2MirrorManifest manifest,
        UpdateSiteP2MirrorValidation validation, boolean reused) { }
