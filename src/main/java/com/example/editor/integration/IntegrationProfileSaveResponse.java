package com.example.editor.integration;
/** Respuesta de guardado con ETag y comprobación. */
public record IntegrationProfileSaveResponse(IntegrationProfileModel profile, String etag, IntegrationCheckReport checkReport) { }
