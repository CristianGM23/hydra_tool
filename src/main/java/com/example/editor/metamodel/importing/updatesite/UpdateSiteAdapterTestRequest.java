package com.example.editor.metamodel.importing.updatesite;

/** JSON request combining the authored document and one safe fixture. */
public record UpdateSiteAdapterTestRequest(String document, UpdateSiteAdapterFixture fixture) {
    public UpdateSiteAdapterTestRequest {
        document = document == null ? "" : document;
    }
}
