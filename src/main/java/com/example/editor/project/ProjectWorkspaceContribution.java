package com.example.editor.project;

import java.nio.file.Path;
import java.util.Set;

/** Declara cómo participa un repositorio existente en la sincronización de un proyecto local. */
public record ProjectWorkspaceContribution(String id,Path workspaceRoot,Set<ArtifactKind> artifactKinds,String fallbackProjectRoot) {
    public ProjectWorkspaceContribution {
        if(id==null||id.isBlank()) throw new IllegalArgumentException("Workspace contribution id is required");
        if(workspaceRoot==null) throw new IllegalArgumentException("Workspace contribution root is required");
        id=id.trim();
        workspaceRoot=workspaceRoot.toAbsolutePath().normalize();
        artifactKinds=artifactKinds==null?Set.of():Set.copyOf(artifactKinds);
        fallbackProjectRoot=fallbackProjectRoot==null?"":fallbackProjectRoot.replace('\\','/');
        if(fallbackProjectRoot.startsWith("/")||fallbackProjectRoot.contains("..")) throw new IllegalArgumentException("Fallback project root must be relative");
    }
}
