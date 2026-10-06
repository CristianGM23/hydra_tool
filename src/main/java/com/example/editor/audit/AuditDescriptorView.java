package com.example.editor.audit;

public record AuditDescriptorView(String activityType, String moduleId, String action, String title, String module, AuditImportance importance) { }
