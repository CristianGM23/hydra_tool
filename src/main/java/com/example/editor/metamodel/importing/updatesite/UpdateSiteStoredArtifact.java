package com.example.editor.metamodel.importing.updatesite;

import java.nio.file.Path;

/** Referencia inmutable a un artefacto almacenado por contenido. */
record UpdateSiteStoredArtifact(String sha256, long size, Path path) { }
