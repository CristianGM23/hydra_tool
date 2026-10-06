package com.example.editor.project;

import java.time.Instant;

/** Resumen de un proyecto conocido por la instalación local. */
public record ProjectSummary(String id,String name,String directory,Instant lastOpenedAt,boolean active,boolean available) { }
