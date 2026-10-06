package com.example.editor.metamodel.importing.updatesite;

/** Drift status for one locked URL. */
public record UpdateSiteRemoteSnapshotDriftItem(String url, String expectedSha256,
        String actualSha256, String status, String explanation) { }
