package com.example.editor.metamodel.importing.updatesite;

/** One immutable authored adapter version. */
public record UpdateSiteDeclarativeAdapterVersionEntry(String adapterId, String version,
        String fileName, String fingerprint, boolean current) { }
