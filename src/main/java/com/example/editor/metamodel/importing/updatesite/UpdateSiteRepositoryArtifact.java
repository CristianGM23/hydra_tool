package com.example.editor.metamodel.importing.updatesite;

/** Metadatos de un blob p2 que pueden consultarse sin cargar su contenido. */
record UpdateSiteRepositoryArtifact(String path, long size, String sha256) { }
