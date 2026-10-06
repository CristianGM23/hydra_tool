package com.example.editor.config.governance;

/** Safe configuration descriptor. Values marked secret never enter this model. */
public record HydraTechnicalSetting(String key, String category, String value, boolean mutable, String description) { }
