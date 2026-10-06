package com.example.editor.integration;
/** Perfil cargado junto con su ETag. */
public record IntegrationProfileLoadResult(IntegrationProfileModel profile, String etag, IntegrationCheckReport checkReport) { }
