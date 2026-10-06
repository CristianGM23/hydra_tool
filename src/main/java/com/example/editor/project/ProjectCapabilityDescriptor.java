package com.example.editor.project;

import java.util.Set;

public record ProjectCapabilityDescriptor(String id,Set<ProjectCapability> requires) { }
