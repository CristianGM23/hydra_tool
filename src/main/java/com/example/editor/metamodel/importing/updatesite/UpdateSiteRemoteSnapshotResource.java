package com.example.editor.metamodel.importing.updatesite;

/** One locked resource in a content-addressed p2 snapshot. */
public record UpdateSiteRemoteSnapshotResource(String url, String virtualPath, long bytes,
        String sha256, int depth, String objectPath) { }
