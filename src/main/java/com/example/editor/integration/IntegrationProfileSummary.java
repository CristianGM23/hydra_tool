package com.example.editor.integration;
/** Resumen ligero para seleccionar un perfil. */
public record IntegrationProfileSummary(String id, String metamodelId, String name, IntegrationTechnology technology,
        IntegrationDeliveryMode deliveryMode, int featureCount, int mappingCount, String etag) { }
