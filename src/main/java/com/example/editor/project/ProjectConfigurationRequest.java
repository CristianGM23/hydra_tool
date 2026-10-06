package com.example.editor.project;

import java.util.Set;

/** Actualiza capacidades/experiencia sin cambiar la identidad del proyecto. */
public record ProjectConfigurationRequest(Set<ProjectCapability> enabledCapabilities,ProjectExperienceMode experienceMode,ProjectPrimaryArtifact primaryArtifact) { }
