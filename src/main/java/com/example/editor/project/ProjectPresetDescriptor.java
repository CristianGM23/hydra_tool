package com.example.editor.project;

import java.util.Set;

public record ProjectPresetDescriptor(String id,String label,String description,Set<ProjectCapability> capabilities) { }
