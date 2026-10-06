package com.example.editor.audit;

public record AuditPresentedChange(String label, String path, AuditChangeKind kind, String beforeValue, String afterValue) { }
