package com.example.editor.metamodel.importing.updatesite;

/** Positive or negative fixture with an explicit expected match. */
public record UpdateSiteAdapterBenchCase(String name, UpdateSiteAdapterFixture fixture, boolean expectedMatch) {
    public UpdateSiteAdapterBenchCase {
        name = name == null || name.isBlank() ? "fixture" : name.trim();
        fixture = fixture == null ? new UpdateSiteAdapterFixture("", "", java.util.Map.of()) : fixture;
    }
}
