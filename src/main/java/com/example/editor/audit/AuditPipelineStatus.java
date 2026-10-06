package com.example.editor.audit;

public record AuditPipelineStatus(boolean enabled, boolean running, int queued, int capacity,
        long accepted, long synchronousFallbacks, long dropped, long failures, long persisted) { }
